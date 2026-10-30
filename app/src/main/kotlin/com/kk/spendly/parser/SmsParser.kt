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
    /** Shown only when a message names nobody and says nothing about the kind of payment either. */
    const val UNKNOWN_MERCHANT = "Unknown"

    // Used when a message names nobody: they describe the kind of payment, not who was paid. A category chosen for one of
    // these is never shared with the others, since a "UPI payment" to one person says nothing about the next one.
    private val genericLabels = setOf(
        UNKNOWN_MERCHANT, "UPI payment", "UPI received", "Card payment", "Card bill payment", "NEFT transfer", "IMPS transfer",
        "RTGS transfer", "Deposit", "Salary", "Interest", "Refund", "Cashback", "Provident fund",
    ).map { it.lowercase() }.toSet()

    fun isGeneric(merchant: String): Boolean = merchant.trim().lowercase() in genericLabels

    // Bank alerts come from alphanumeric sender IDs (e.g. VM-HDFCBK), never plain phone numbers.
    private val senderRegex = Regex("^(?:[A-Z]{2}-)?[A-Z][A-Z0-9]{2,10}(?:-[A-Z])?$", RegexOption.IGNORE_CASE)
    private val otpRegex = Regex("\\b(otp|one[- ]time password|verification code)\\b", RegexOption.IGNORE_CASE)
    private val amountRegex = Regex("(?:rs\\.?|inr|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE)
    private val debitRegex = Regex("\\b(debited|spent|paid|sent|withdrawn|purchase|payment of)\\b", RegexOption.IGNORE_CASE)
    private val creditRegex = Regex("\\b(credited|received|deposited|refund(?:ed)?)\\b", RegexOption.IGNORE_CASE)
    // Some banks write only "Dr." and "Cr."; used when none of the words above appear.
    private val drRegex = Regex("\\bdr\\b", RegexOption.IGNORE_CASE)
    private val crRegex = Regex("\\bcr\\b", RegexOption.IGNORE_CASE)
    private val accountRegex = Regex(
        "(?:a/c|acct|account|card)\\.?\\s*(?:no\\.?)?\\s*(?:ending\\s*)?[x*]*\\s*(\\d{3,4})\\b",
        RegexOption.IGNORE_CASE,
    )
    private val refRegex = Regex("(?:ref(?:erence)?(?:\\s*no\\.?)?|rrn)[:\\s]*(\\d{6,})", RegexOption.IGNORE_CASE)

    // A card-bill confirmation ("payment of Rs X received towards your credit card") repeats a payment the bank already reported
    // as a debit, so reading it too would count the same money twice.
    private val cardBillRegex = Regex("payment of .{0,30}\\breceived\\b.{0,40}\\bcredit card", RegexOption.IGNORE_CASE)

    // Everything after these is a warning or helpline footer. Its phone numbers and "to" must not be mistaken for the payee.
    private val footerRegex = Regex(
        "\\b(?:not you|if not you|if this (?:was )?not|sms block|block upi|to block|dispute|fraud|helpline|toll[- ]free)\\b|\\bcall\\b",
        RegexOption.IGNORE_CASE,
    )

    private const val STOP =
        "(?=\\s+(?:on|ref(?:no)?|upi|utr|avl|bal|via|using|dt|date|for|through|thru)\\b|\\s+-\\w|\\s*[(,;]|\\.(?:\\s|$)|\\s*$)"
    private const val NAME = "([A-Za-z0-9@._&*\\- ]{2,64}?)"
    private val payeeRegex = Regex("\\b(?:to|at)\\s+(?:vpa\\s+)?$NAME$STOP", RegexOption.IGNORE_CASE)
    private val payerRegex = Regex("\\b(?:from|by)\\s+(?:vpa\\s+)?$NAME$STOP", RegexOption.IGNORE_CASE)

    // UPI/P2M/<ref>/<NAME> (also IMPS, NEFT, RTGS): the name follows the reference number.
    private val nameAfterRef =
        Regex("\\b(?:UPI|IMPS|NEFT|RTGS)[/-](?:[A-Z0-9]{2,4}[/-])?\\d{8,}[/-]([^/\\n.]{2,40})", RegexOption.IGNORE_CASE)
    // UPI/<NAME>/<ref>: the name sits between the word UPI and the reference number.
    private val nameBeforeRef = Regex("\\bUPI/([A-Za-z][^/\\n]{1,40}?)/\\d{8,}", RegexOption.IGNORE_CASE)
    // NEFT Cr-<IFSC>-<NAME>-<ref>: the name follows the bank's IFSC code.
    private val nameAfterIfsc = Regex(
        "\\b(?:NEFT|IMPS|RTGS)\\b[^\\n]{0,30}?\\b[A-Z]{4}0[A-Z0-9]{6}\\b[-/\\s]+([A-Za-z][A-Za-z .&]{1,40}?)" +
            "(?=[-/\\s]*(?:\\d|ref(?![a-z])|avl(?![a-z])|bal(?![a-z]))|[.,]|\\s*$)",
        RegexOption.IGNORE_CASE,
    )
    // ...by NEFT trf: <NAME> on ...
    private val nameAfterTrf = Regex(
        "\\btrf\\s*[:\\-]\\s*([A-Za-z][A-Za-z .&]{2,40}?)(?=\\s+(?:on|avl|bal|ref|dt)\\b|[-.,/(]|\\s*$)",
        RegexOption.IGNORE_CASE,
    )
    // "...on 25-Oct-26; SWIGGY credited." names the receiver of a debit.
    private val nameBeforeCredited =
        Regex(";\\s*([A-Za-z][A-Za-z0-9 .&_-]{1,40}?)\\s+credited\\b", RegexOption.IGNORE_CASE)

    // When a message gives only the other side's account ("...and credited to a/c no. XXXX5678"), that is all there is to show.
    private val counterpartyOfDebit = Regex(
        "\\bcredited\\s+to\\s+(?:your\\s+)?(?:a/?c|acct|account)\\.?\\s*(?:no\\.?)?\\s*[x*]*\\s*(\\d{3,})\\b", RegexOption.IGNORE_CASE,
    )
    private val counterpartyOfCredit = Regex(
        "\\bdebited\\s+from\\s+(?:your\\s+)?(?:a/?c|acct|account)\\.?\\s*(?:no\\.?)?\\s*[x*]*\\s*(\\d{3,})\\b", RegexOption.IGNORE_CASE,
    )

    private val notAName = Regex(
        "^(?:your|a/?c\\b|acct|account|upi\\b|ref|neft|imps|rtgs|cheque|chq|cash\\b|the\\b|you\\b|atm\\b|rs\\.?\\s*\\d|inr\\s*\\d)",
        RegexOption.IGNORE_CASE,
    )
    private val sixDigits = Regex("\\d{6,}")
    private val warningWord = Regex("\\b(?:block|call)\\b", RegexOption.IGNORE_CASE)
    private val transferCodes = setOf("P2M", "P2A", "P2P", "CR", "DR")
    private val trailingRef = Regex("\\s+(?:ref(?:no)?\\.?\\s*)?\\d{4,}$", RegexOption.IGNORE_CASE)
    private val multiSpace = Regex("\\s{2,}")

    private class Kind(pattern: String, val credit: Boolean? = null, val label: (TxnType) -> String) {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
    }

    // What to show when a message names nobody at all: the kind of payment, first match wins.
    private val kinds = listOf(
        Kind("\\batm\\b|\\bwithdrawn\\b|\\bwithdrawal\\b") { "ATM withdrawal" },
        Kind("credit card", credit = true) { "Card bill payment" },
        Kind("\\b(?:emi|nach|ecs|mandate|auto[- ]?debit|autopay|standing instruction)\\b") { "EMI / auto-debit" },
        Kind("\\bsalary\\b") { "Salary" },
        Kind("\\binterest\\b") { "Interest" },
        Kind("\\brefund|\\breversal|\\breversed\\b") { "Refund" },
        Kind("\\bcashback\\b") { "Cashback" },
        Kind("\\bepf\\b|provident|\\bpf\\b") { "Provident fund" },
        Kind("\\bneft\\b") { "NEFT transfer" },
        Kind("\\bimps\\b") { "IMPS transfer" },
        Kind("\\brtgs\\b") { "RTGS transfer" },
        Kind("\\bupi\\b") { if (it == TxnType.DEBIT) "UPI payment" else "UPI received" },
        Kind("\\bcard\\b") { "Card payment" },
        Kind("\\bdeposit", credit = true) { "Deposit" },
    )

    fun parse(sender: String, body: String, timestamp: Long): ParsedTxn? {
        if (!senderRegex.matches(sender.trim())) return null
        if (otpRegex.containsMatchIn(body)) return null
        if (cardBillRegex.containsMatchIn(body)) return null

        val amountMatch = amountRegex.find(body) ?: return null
        val amountPaise = amountMatch.groupValues[1].replace(",", "").toBigDecimalOrNull()
            ?.movePointRight(2)?.toLong() ?: return null
        if (amountPaise <= 0) return null

        var debitAt = debitRegex.find(body)?.range?.first
        var creditAt = creditRegex.find(body)?.range?.first
        if (debitAt == null && creditAt == null) {
            debitAt = drRegex.find(body)?.range?.first
            creditAt = crRegex.find(body)?.range?.first
        }
        val type = when {
            debitAt == null && creditAt == null -> return null
            creditAt == null -> TxnType.DEBIT
            debitAt == null -> TxnType.CREDIT
            debitAt < creditAt -> TxnType.DEBIT
            else -> TxnType.CREDIT
        }

        val merchant = findName(body, type) ?: findCounterpartyAccount(body, type) ?: kindLabel(body, type)
        val account = accountRegex.find(body)?.groupValues?.get(1)
        val ref = refRegex.find(body)?.groupValues?.get(1)

        // A bank SMS and a UPI-app SMS for the same payment share the ref number; without one,
        // fall back to amount + account + minute so repeat deliveries still collapse.
        val key = if (ref != null) "$type|$amountPaise|$ref"
        else "$type|$amountPaise|${account.orEmpty()}|${timestamp / 60_000}"

        return ParsedTxn(amountPaise, type, merchant, account, ref, timestamp, key)
    }

    /** Tries the message formats from most to least specific and returns the first sensible name. */
    private fun findName(body: String, type: TxnType): String? {
        val text = footerRegex.find(body)?.let { body.substring(0, it.range.first) } ?: body
        fun firstName(regex: Regex): String? =
            regex.findAll(text).firstNotNullOfOrNull { clean(it.groupValues[1]).takeIf(::looksLikeName) }

        return firstName(nameAfterRef)
            ?: firstName(nameBeforeRef)
            ?: firstName(nameAfterIfsc)
            ?: firstName(nameAfterTrf)
            ?: (if (type == TxnType.DEBIT) firstName(nameBeforeCredited) else null)
            ?: firstName(if (type == TxnType.DEBIT) payeeRegex else payerRegex)
    }

    private fun findCounterpartyAccount(body: String, type: TxnType): String? {
        val digits = (if (type == TxnType.DEBIT) counterpartyOfDebit else counterpartyOfCredit)
            .find(body)?.groupValues?.get(1) ?: return null
        return "A/c ••${digits.takeLast(4)}"
    }

    private fun kindLabel(body: String, type: TxnType): String =
        kinds.firstOrNull { (it.credit == null || it.credit == (type == TxnType.CREDIT)) && it.regex.containsMatchIn(body) }
            ?.label?.invoke(type) ?: UNKNOWN_MERCHANT

    /** Trims bank clutter around a name: card-network suffixes ("AMAZON PAY*ORDER9"), trailing reference numbers, stray dashes. */
    private fun clean(raw: String): String {
        var n = raw.substringBefore('*').trim().trim('-', '.', ',', ';', ':', '/').trim()
        n = n.replace(trailingRef, "").replace(multiSpace, " ").trim()
        if ('@' !in n && n.length > 40) n = n.take(40).substringBeforeLast(' ', n.take(40))
        return n
    }

    private fun looksLikeName(n: String): Boolean {
        if (n.length < 2 || n.none { it.isLetter() }) return false
        if (notAName.containsMatchIn(n) || warningWord.containsMatchIn(n)) return false
        if (n.uppercase() in transferCodes) return false
        // A long digit run with no "@" is a phone or reference number, not a payee. UPI IDs made of digits keep their "@".
        return '@' in n || !sixDigits.containsMatchIn(n)
    }
}
