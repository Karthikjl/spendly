package com.kk.spendly.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import com.kk.spendly.BuildConfig
import com.kk.spendly.data.AppDatabase
import com.kk.spendly.data.Repository
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

object SmsBackfill {
    /** Reads the whole inbox once; duplicates are dropped by the unique dedupe key. Returns count of new rows. */
    suspend fun run(context: Context): Int {
        if (BuildConfig.DEMO) return 0 // the demo build must never import real messages
        val dao = AppDatabase.get(context).dao()
        var added = 0
        context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("address", "body", "date"),
            null, null, "date DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val parsed = SmsParser.parse(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getLong(2)) ?: continue
                if (Repository.save(dao, parsed) != null) added++
            }
        }
        return added
    }
}
