package com.kk.spendly.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmsParserTest {
    private val t = 1_700_000_000_000L

    @Test
    fun upiDebitToVpa() {
        val p = SmsParser.parse(
            "VM-HDFCBK",
            "Rs.450.00 debited from A/c XX1234 on 25-10-26 to VPA swiggy@icici (UPI Ref No 629812345678). -HDFC Bank",
            t,
        )
        assertNotNull(p)
        assertEquals(45_000L, p!!.amountPaise)
        assertEquals(TxnType.DEBIT, p.type)
        assertEquals("swiggy@icici", p.merchant)
        assertEquals("1234", p.account)
        assertEquals("629812345678", p.refNo)
        assertEquals("Food", Categorizer.categorize(p.merchant, p.type))
    }

    @Test
    fun sentFromHdfcStyle() {
        val p = SmsParser.parse(
            "AX-HDFCBK",
            "Sent Rs.120.00 From HDFC Bank A/C *1234 To ZOMATO On 25/10/26 Ref 629800001111 Not You? Call 18002586161",
            t,
        )!!
        assertEquals(12_000L, p.amountPaise)
        assertEquals(TxnType.DEBIT, p.type)
        assertEquals("ZOMATO", p.merchant)
        assertEquals("629800001111", p.refNo)
    }

    @Test
    fun creditWithCommaAmount() {
        val p = SmsParser.parse(
            "JD-SBIUPI",
            "Your a/c no. XXXXXXXX5678 is credited by Rs.5,000.00 on 25-Oct-26 by UPI Ref No 629855550000.",
            t,
        )!!
        assertEquals(500_000L, p.amountPaise)
        assertEquals(TxnType.CREDIT, p.type)
        assertEquals("5678", p.account)
        assertEquals("Income", Categorizer.categorize(p.merchant, p.type))
    }

    @Test
    fun cardSpend() {
        val p = SmsParser.parse(
            "VK-ICICIB",
            "Dear Customer, INR 1,299.00 spent on Card XX4321 at AMAZON on 24-10-26. Avl Lmt INR 50,000.",
            t,
        )!!
        assertEquals(129_900L, p.amountPaise)
        assertEquals("AMAZON", p.merchant)
        assertEquals("4321", p.account)
        assertEquals("Shopping", Categorizer.categorize(p.merchant, p.type))
    }

    @Test
    fun otpIsIgnored() {
        assertNull(SmsParser.parse("VM-HDFCBK", "123456 is your OTP for transaction of Rs.500.00. Do not share.", t))
    }

    @Test
    fun personalNumberIsIgnored() {
        assertNull(SmsParser.parse("+919876543210", "Rs.500 debited, paid you back", t))
    }

    @Test
    fun duplicateAlertsShareDedupeKey() {
        val a = SmsParser.parse("VM-HDFCBK", "Rs.99 debited from A/c XX1234 to VPA a@b (UPI Ref No 111111111111)", t)!!
        val b = SmsParser.parse("AX-UPIPAY", "You paid Rs.99 to a@b. UPI Ref 111111111111", t + 5_000)!!
        assertEquals(a.dedupeKey, b.dedupeKey)
    }

    @Test
    fun medicalMerchantsAreCategorized() {
        listOf("APOLLO PHARMACY", "city medicals", "Dr Lal Diagnostic").forEach {
            assertEquals(it, "Medical", Categorizer.categorize(it, TxnType.DEBIT))
        }
    }
}
