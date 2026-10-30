package com.kk.spendly.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Message styles that used to come out as "Unknown" or with a wrong name. All numbers and names here are invented. */
class SmsParserFormatsTest {
    private val t = 1_700_000_000_000L
    private fun parse(body: String, sender: String = "VM-TESTBK") = SmsParser.parse(sender, body, t)!!
    private fun merchant(body: String) = parse(body).merchant

    @Test
    fun nameAfterTheReferenceNumber() {
        val p = parse("INR 450.00 debited from A/c no. XX1234 on 25-10-2026 09:12:11 UPI/P2M/629812345678/SWIGGY. Not you? SMS BLOCK to 919951860002 -Test Bank")
        assertEquals("SWIGGY", p.merchant)
        assertEquals(TxnType.DEBIT, p.type)
    }

    @Test
    fun theHelplineNumberInAWarningIsNeverThePayee() {
        // Without footer handling the first "to" after the warning would win and the payee would be a phone number.
        val m = merchant("INR 450.00 debited from A/c no. XX1234 on 25-10-2026 for UPI/P2M/629812345678. Not you? SMS BLOCK to 919951860002 -Test Bank")
        assertFalse(m.contains("919951860002"))
    }

    @Test
    fun nameBetweenUpiAndTheReference() {
        assertEquals("SWIGGY", merchant("Your a/c XX1234 is debited by Rs.450.00 on 25-10-26 for UPI/SWIGGY/629812345678. Avl bal Rs.1200. -Test Bank"))
        val credit = parse("Rs 5,000.00 credited to a/c XX5678 on 25-10-26 by UPI/RAHUL S/629855550000. -Test Bank")
        assertEquals("RAHUL S", credit.merchant)
        assertEquals(TxnType.CREDIT, credit.type)
        assertEquals("RAHUL S", merchant("Test Bank Account XX234 credited:Rs. 5,000.00 on 25-Oct-26. Info: UPI/RAHUL S/629855550000. Available Balance Rs 9,000."))
    }

    @Test
    fun receiverNamedBeforeTheWordCredited() {
        assertEquals("SWIGGY", merchant("Test Bank Acct XX234 debited for Rs 450.00 on 25-Oct-26; SWIGGY credited. UPI:629812345678. Call 1800 for dispute."))
    }

    @Test
    fun referenceWordsAreNotPartOfTheName() {
        assertEquals("SWIGGY", merchant("Dear UPI user A/C X1234 debited by Rs450.0 on date 25Oct26 trf to SWIGGY Refno 629812345678. If not you, call 1800111109. -Test Bank"))
    }

    @Test
    fun transferStyleThatOnlyWritesDrAndCr() {
        val p = parse("Rs.450.00 Dr. from A/C XXXXXX1234 and Cr. to swiggy@icici. Ref:629812345678. AvlBal Rs.12000.00 -Test Bank")
        assertEquals(TxnType.DEBIT, p.type)
        assertEquals("swiggy@icici", p.merchant)
    }

    @Test
    fun neftNameAfterTheIfscCode() {
        val p = parse("Rs.15000.00 deposited in Test Bank A/c XX1234 on 25-10-26 for NEFT Cr-ABCD0123456-ACME LTD-REF123. Avl bal Rs.1000.")
        assertEquals("ACME LTD", p.merchant)
        assertEquals(TxnType.CREDIT, p.type)
        assertEquals("ACME LTD", merchant("A/c no XX1234 credited with INR 5000.00 towards by NEFT trf: ACME LTD on 25-10-26 Avl bal 100"))
    }

    @Test
    fun longUpiIdsAreKeptWhole() {
        val vpa = "paytmqr2810050501abcdefghijklmnopqrstuv@paytm"
        assertEquals(vpa, merchant("Rs.450.00 debited from a/c **1234 on 25-10-26 to VPA $vpa (UPI Ref No 629812345678)"))
    }

    @Test
    fun cardNetworkSuffixIsTrimmed() {
        assertEquals("AMAZON PAY", merchant("Dear Customer, INR 1,299.00 spent on Card XX4321 at AMAZON PAY*ORDER99 on 24-10-26. Avl Lmt INR 50,000."))
    }

    @Test
    fun otherPartyAccountWhenNoNameIsGiven() {
        val debit = parse("Your a/c no. XXXXXXXX1234 is debited for Rs. 450.00 on 25-10-2026 and credited to a/c no. XXXXXXXX5678 (UPI Ref no 629812345678).")
        assertEquals("A/c ••5678", debit.merchant)
        assertEquals("1234", debit.account) // the dedupe key and own-account digits are unchanged
        assertEquals("Transfers", Categorizer.categorize(debit.merchant, debit.type))
        val credit = parse("Your a/c no. XXXXXXXX1234 is credited for Rs. 450.00 on 25-10-2026 and debited from a/c no. XXXXXXXX5678 (UPI Ref no 629812345678).")
        assertEquals("A/c ••5678", credit.merchant)
        assertEquals(TxnType.CREDIT, credit.type)
    }

    @Test
    fun kindOfPaymentWhenThereIsNothingElse() {
        assertEquals("ATM withdrawal", merchant("Rs.2000.00 withdrawn from A/c XX1234 at ATM on 25-10-26. Avl Bal Rs.1000."))
        assertEquals("EMI / auto-debit", merchant("Rs.5947.00 debited from A/c XX1234 on 25-10-26 towards EMI loan A/c 98765. Avl bal Rs.1000."))
        assertEquals("UPI received", merchant("Your a/c no. XXXXXXXX5678 is credited by Rs.5,000.00 on 25-Oct-26 by UPI Ref No 629855550000."))
        assertEquals("NEFT transfer", merchant("Rs.100.00 debited from A/c XX1234 on 25-10-26 via NEFT. Avl bal Rs.1000."))
        assertEquals("Unknown", merchant("Rs.100.00 debited from A/c XX1234 on 25-10-26. Avl bal Rs.1000."))
    }

    @Test
    fun cardBillConfirmationIsNotCountedTwice() {
        assertNull(SmsParser.parse("VM-TESTBK", "Dear Customer, payment of Rs. 5,000.00 received towards your credit card ending 1234 on 25-10-26.", t))
    }

    @Test
    fun genericLabelsAreNeverSharedAcrossPayees() {
        listOf("Unknown", "UPI payment", "UPI received", "Card payment", "NEFT transfer").forEach { assertTrue(it, SmsParser.isGeneric(it)) }
        listOf("SWIGGY", "A/c ••5678", "ATM withdrawal", "EMI / auto-debit", "john@okaxis").forEach { assertFalse(it, SmsParser.isGeneric(it)) }
        assertNotNull(parse("Rs.100.00 debited from A/c XX1234 on 25-10-26 for UPI. Avl bal Rs.1000."))
    }

    @Test
    fun theFingerprintOfAPaymentDoesNotChange() {
        // Rows saved by older versions are matched by this key, so better name-reading must never alter it.
        val a = parse("Rs.450.00 debited from A/c XX1234 on 25-10-26 to VPA swiggy@icici (UPI Ref No 629812345678). -Test Bank")
        assertEquals("DEBIT|45000|629812345678", a.dedupeKey)
        val b = parse("Rs.120.00 debited from A/c XX1234 on 25-10-26 to ZOMATO")
        assertEquals("DEBIT|12000|1234|${t / 60_000}", b.dedupeKey)
    }
}
