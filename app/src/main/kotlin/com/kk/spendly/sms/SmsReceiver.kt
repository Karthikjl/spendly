package com.kk.spendly.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import com.kk.spendly.BuildConfig
import com.kk.spendly.data.AppDatabase
import com.kk.spendly.data.Repository
import com.kk.spendly.parser.ParsedTxn
import androidx.room.withTransaction
import com.kk.spendly.parser.SmsParser
import com.kk.spendly.parser.TxnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (BuildConfig.DEMO || intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Long messages arrive in parts; stitch them back together per sender.
        val bySender = messages.groupBy { it.originatingAddress.orEmpty() }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(context).dao()
                bySender.forEach { (sender, parts) ->
                    val body = parts.joinToString("") { it.messageBody.orEmpty() }
                    val parsed = SmsParser.parse(sender, body, parts.first().timestampMillis) ?: return@forEach
                    val category = Repository.save(dao, parsed)
                    if (category != null && parsed.type == TxnType.DEBIT) BudgetAlerts.check(context, dao, category)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Decides how much of the inbox to read on launch. Pure, so it is easy to test. */
internal object BackfillPlan {
    /** Raise this whenever the parser learns something new: the next launch then re-reads everything once, upgrading old rows. */
    const val PARSER_VERSION = 2

    private const val OVERLAP_MS = 2 * 24 * 3_600_000L
    private const val FULL_RESCAN_MS = 7 * 24 * 3_600_000L

    /**
     * Messages dated after the returned time need reading; 0 means all of them. Everything is re-read after a parser update, on
     * the first run, and about weekly (a restored backup can bring in old-dated messages); otherwise only recent ones.
     */
    fun sinceMillis(savedVersion: Int, scannedUntil: Long, lastFullScan: Long, now: Long): Long = when {
        savedVersion != PARSER_VERSION || scannedUntil <= 0L || now - lastFullScan > FULL_RESCAN_MS -> 0L
        else -> (scannedUntil - OVERLAP_MS).coerceAtLeast(0L)
    }
}

object SmsBackfill {
    private const val PREFS = "backfill"
    private const val BATCH = 200

    /** Reads the inbox (all of it, or just what is new); duplicates are dropped by the unique dedupe key. Returns new rows. */
    suspend fun run(context: Context): Int {
        if (BuildConfig.DEMO) return 0 // the demo build must never import real messages
        val db = AppDatabase.get(context)
        val dao = db.dao()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val since = BackfillPlan.sinceMillis(
            prefs.getInt("parser_version", 0), prefs.getLong("scanned_until", 0L), prefs.getLong("last_full_scan", 0L), now,
        )
        var newest = prefs.getLong("scanned_until", 0L)
        var added = 0
        val batch = ArrayList<ParsedTxn>(BATCH)

        // One transaction per batch: Room announces a table change once per transaction, and every announcement re-runs all the
        // queries the screens are showing. Hundreds of single writes at launch used to mean hundreds of redraws.
        suspend fun flush() {
            if (batch.isEmpty()) return
            db.withTransaction { for (p in batch) if (Repository.save(dao, p) != null) added++ }
            batch.clear()
        }

        val cursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("address", "body", "date"),
            if (since > 0) "date > ?" else null,
            if (since > 0) arrayOf(since.toString()) else null,
            "date DESC",
        ) ?: return added // inbox unreadable (no permission): remember nothing, try again next launch
        cursor.use { c ->
            while (c.moveToNext()) {
                val date = c.getLong(2)
                if (date > newest) newest = date
                val parsed = SmsParser.parse(c.getString(0).orEmpty(), c.getString(1).orEmpty(), date) ?: continue
                batch += parsed
                if (batch.size >= BATCH) flush()
            }
        }
        flush()

        val editor = prefs.edit()
        editor.putInt("parser_version", BackfillPlan.PARSER_VERSION).putLong("scanned_until", newest)
        if (since == 0L) editor.putLong("last_full_scan", now)
        editor.apply()
        return added
    }
}
