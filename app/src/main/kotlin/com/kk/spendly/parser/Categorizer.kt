package com.kk.spendly.parser

object Categorizer {
    const val OTHER = "Other"
    const val INCOME = "Income"

    val categories = listOf(
        "Food", "Groceries", "Transport", "Shopping", "Bills", "Entertainment", "Medical", "Transfers", OTHER, INCOME,
    )

    private val keywords = mapOf(
        "Food" to listOf("swiggy", "zomato", "dominos", "mcdonald", "kfc", "restaurant", "cafe", "starbucks"),
        "Groceries" to listOf("bigbasket", "blinkit", "zepto", "dmart", "grocer", "instamart"),
        "Transport" to listOf("uber", "ola", "rapido", "irctc", "redbus", "metro", "fuel", "petrol", "fastag"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "meesho"),
        "Bills" to listOf("airtel", "jio", "vodafone", "electricity", "bescom", "broadband", "insurance", "recharge"),
        "Entertainment" to listOf("netflix", "spotify", "hotstar", "bookmyshow", "prime", "youtube"),
        "Medical" to listOf(
            "pharmacy", "pharma", "chemist", "medical", "medico", "medplus", "apollo", "1mg", "pharmeasy", "netmeds", "practo",
            "hospital", "clinic", "diagnostic", "pathlab", "dental", "doctor", "health",
        ),
    )

    fun categorize(merchant: String, type: TxnType): String {
        if (type == TxnType.CREDIT) return INCOME
        val m = merchant.lowercase()
        keywords.forEach { (category, words) -> if (words.any { it in m }) return category }
        // A raw VPA like "john@okaxis", another person's account ("A/c ••1234") or a NEFT/IMPS/RTGS transfer with no merchant
        // keyword is most likely a person-to-person transfer.
        return if ('@' in m || m.startsWith("a/c ••") || m.endsWith(" transfer")) "Transfers" else OTHER
    }
}
