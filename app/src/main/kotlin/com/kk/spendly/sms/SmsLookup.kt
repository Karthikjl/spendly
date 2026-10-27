package com.kk.spendly.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.kk.spendly.BuildConfig
import com.kk.spendly.data.TxnEntity
import com.kk.spendly.data.isManual
import com.kk.spendly.parser.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** The message a transaction was parsed from, as it sits in the phone's own messages app. */
data class OriginalSms(val sender: String, val body: String, val receivedAt: Long, val reference: String?)

sealed interface SmsLookupResult {
    data class Found(val sms: OriginalSms) : SmsLookupResult
    /** SMS permission is missing or blocked, so the inbox can't be read. */
    data object NoPermission : SmsLookupResult
    /** The permission is fine but no matching message exists any more (for example it was deleted). */
    data object NotFound : SmsLookupResult
}

/**
 * Finds the original SMS for a transaction by reading the inbox on demand.
 *
 * Spendly deliberately does not store message text: the privacy promise is that only the extracted amount, merchant and
 * date are kept. Instead the transaction's timestamp narrows the inbox to a short window, and each message in that window
 * is re-parsed; the one that produces the same fingerprint as the stored transaction is the original.
 */
object SmsLookup {
    private const val WINDOW_MS = 30 * 60_000L

    data class Candidate(val sender: String, val body: String, val date: Long, val dateSent: Long)

    /** Picks the candidate that parses to the stored transaction's fingerprint, preferring the one closest in time. */
    fun pick(candidates: List<Candidate>, dedupeKey: String, timestamp: Long): OriginalSms? =
        candidates.mapNotNull { c ->
            // Parse with the stored timestamp so the no-reference fallback key, which includes the minute, lines up.
            val parsed = SmsParser.parse(c.sender, c.body, timestamp)?.takeIf { it.dedupeKey == dedupeKey } ?: return@mapNotNull null
            val distance = minOf(abs(c.date - timestamp), if (c.dateSent > 0) abs(c.dateSent - timestamp) else Long.MAX_VALUE)
            distance to OriginalSms(c.sender, c.body, c.date, parsed.refNo)
        }.minByOrNull { it.first }?.second

    suspend fun find(context: Context, txn: TxnEntity): SmsLookupResult = withContext(Dispatchers.IO) {
        // The demo build never reads real messages; it shows a clearly fictional one so screenshots can include this view.
        if (BuildConfig.DEMO) return@withContext SmsLookupResult.Found(sampleFor(txn))
        if (txn.isManual) return@withContext SmsLookupResult.NotFound
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext SmsLookupResult.NoPermission
        }
        val from = (txn.timestamp - WINDOW_MS).toString()
        val to = (txn.timestamp + WINDOW_MS).toString()
        val candidates = mutableListOf<Candidate>()
        try {
            context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("address", "body", "date", "date_sent"),
                "(date BETWEEN ? AND ?) OR (date_sent BETWEEN ? AND ?)",
                arrayOf(from, to, from, to),
                "date DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    candidates += Candidate(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getLong(2), c.getLong(3))
                }
            }
        } catch (_: SecurityException) {
            return@withContext SmsLookupResult.NoPermission
        }
        pick(candidates, txn.dedupeKey, txn.timestamp)?.let { SmsLookupResult.Found(it) } ?: SmsLookupResult.NotFound
    }

    private fun sampleFor(txn: TxnEntity): OriginalSms {
        val date = SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH).format(Date(txn.timestamp))
        val amount = "%,.2f".format(Locale.ENGLISH, txn.amountPaise / 100.0)
        val ref = (900_000_000_000L + txn.id * 7919L).toString()
        val body = if (txn.isDebit) {
            "Rs.$amount debited from A/c XX4821 on $date to ${txn.merchant}. UPI Ref No $ref. Not you? Call 1800-000-0000. -Sample Bank"
        } else {
            "Your A/c XX4821 is credited by Rs.$amount on $date from ${txn.merchant}. UPI Ref No $ref. -Sample Bank"
        }
        return OriginalSms("VM-SMPLBK", body, txn.timestamp, ref)
    }
}
