package com.kk.spendly.parser

enum class TxnType { DEBIT, CREDIT }

data class ParsedTxn(
    val amountPaise: Long,
    val type: TxnType,
    val merchant: String,
    val account: String?,
    val refNo: String?,
    val timestamp: Long,
    val dedupeKey: String,
)

object SmsParser {
    // Bank alerts come from alphanumeric sender IDs (e.g. VM-HDFCBK), never plain phone numbers.
    private val senderRegex = Regex("^(?:[A-Z]{2}-)?[A-Z][A-Z0-9]{2,10}(?:-[A-Z])?$", RegexOption.IGNORE_CASE)
    private val otpRegex = Regex("\\b(otp|one[- ]time password|verification code)\\b", RegexOption.IGNORE_CASE)
    private val amountRegex = Regex("(?:rs\\.?|inr|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE)
    private val debitRegex = Regex("\\b(debited|spent|paid|sent|withdrawn|purchase|payment of)\\b", RegexOption.IGNORE_CASE)
    private val creditRegex = Regex("\\b(credited|received|deposited|refund(?:ed)?)\\b", RegexOption.IGNORE_CASE)
    private val accountRegex = Regex(
        "(?:a/c|acct|account|card)\\.?\\s*(?:no\\.?)?\\s*(?:ending\\s*)?[x*]*\\s*(\\d{3,4})\\b",
        RegexOption.IGNORE_CASE,
    )
    private val refRegex = Regex("(?:ref(?:erence)?(?:\\s*no\\.?)?|rrn)[:\\s]*(\\d{6,})", RegexOption.IGNORE_CASE)

    private const val STOP =
        "(?=\\s+(?:on|ref|upi|avl|bal|via|not|call|if|using)\\b|\\s*[(,]|\\.(?:\\s|$)|\\s*$)"
    private const val NAME = "([A-Za-z0-9@._&\\- ]{2,40}?)"
    private val payeeRegex = Regex("\\b(?:to|at)\\s+(?:vpa\\s+)?$NAME$STOP", RegexOption.IGNORE_CASE)
    private val payerRegex = Regex("\\bfrom\\s+(?:vpa\\s+)?$NAME$STOP", RegexOption.IGNORE_CASE)

    fun parse(sender: String, body: String, timestamp: Long): ParsedTxn? {
        if (!senderRegex.matches(sender.trim())) return null
        if (otpRegex.containsMatchIn(body)) return null

        val amountMatch = amountRegex.find(body) ?: return null
        val amountPaise = amountMatch.groupValues[1].replace(",", "").toBigDecimalOrNull()
            ?.movePointRight(2)?.toLong() ?: return null
        if (amountPaise <= 0) return null

        val debitAt = debitRegex.find(body)?.range?.first
        val creditAt = creditRegex.find(body)?.range?.first
        val type = when {
            debitAt == null && creditAt == null -> return null
            creditAt == null -> TxnType.DEBIT
            debitAt == null -> TxnType.CREDIT
            debitAt < creditAt -> TxnType.DEBIT
            else -> TxnType.CREDIT
        }

        val merchantRegex = if (type == TxnType.DEBIT) payeeRegex else payerRegex
        val merchant = merchantRegex.find(body)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: "Unknown"
        val account = accountRegex.find(body)?.groupValues?.get(1)
        val ref = refRegex.find(body)?.groupValues?.get(1)

        // A bank SMS and a UPI-app SMS for the same payment share the ref number; without one,
        // fall back to amount + account + minute so repeat deliveries still collapse.
        val key = if (ref != null) "$type|$amountPaise|$ref"
        else "$type|$amountPaise|${account.orEmpty()}|${timestamp / 60_000}"

        return ParsedTxn(amountPaise, type, merchant, account, ref, timestamp, key)
    }
}
