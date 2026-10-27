package com.kk.spendly.sms

import com.kk.spendly.parser.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmsLookupTest {
    private val t = 1_700_000_000_000L
    private val bank = "VM-HDFCBK"
    private val withRef = "Rs.450.00 debited from A/c XX1234 on 25-10-26 to VPA swiggy@icici (UPI Ref No 629812345678). -HDFC Bank"

    private fun keyOf(body: String, ts: Long = t) = SmsParser.parse(bank, body, ts)!!.dedupeKey

    @Test
    fun findsTheMessageWithTheSameFingerprint() {
        val other = "Rs.99.00 debited from A/c XX1234 on 25-10-26 to VPA tea@okbank (UPI Ref No 111111111111). -HDFC Bank"
        val candidates = listOf(
            SmsLookup.Candidate(bank, other, t - 60_000, 0),
            SmsLookup.Candidate(bank, withRef, t, t - 2_000),
        )
        val found = SmsLookup.pick(candidates, keyOf(withRef), t)
        assertNotNull(found)
        assertEquals(withRef, found!!.body)
        assertEquals("629812345678", found.reference)
        assertEquals(bank, found.sender)
    }

    @Test
    fun returnsNothingWhenNoMessageMatches() {
        val other = "Rs.99.00 debited from A/c XX1234 on 25-10-26 to VPA tea@okbank (UPI Ref No 111111111111). -HDFC Bank"
        assertNull(SmsLookup.pick(listOf(SmsLookup.Candidate(bank, other, t, 0)), keyOf(withRef), t))
        assertNull(SmsLookup.pick(emptyList(), keyOf(withRef), t))
    }

    @Test
    fun ignoresOtpsAndPersonalNumbers() {
        val candidates = listOf(
            SmsLookup.Candidate(bank, "123456 is your OTP for a payment of Rs.450.00 ref 629812345678", t, 0),
            SmsLookup.Candidate("+919876543210", withRef, t, 0),
        )
        assertNull(SmsLookup.pick(candidates, keyOf(withRef), t))
    }

    @Test
    fun picksTheClosestWhenBankAndUpiAppBothSentAMessage() {
        val upiApp = "You paid Rs.450.00 to swiggy@icici. UPI Ref 629812345678"
        val candidates = listOf(
            SmsLookup.Candidate("AX-UPIPAY", upiApp, t + 20 * 60_000, 0),
            SmsLookup.Candidate(bank, withRef, t + 5_000, 0),
        )
        assertEquals(bank, SmsLookup.pick(candidates, keyOf(withRef), t)!!.sender)
    }

    @Test
    fun matchesOnTheSentTimeWhenReceivedTimeDiffers() {
        // Live messages are stored with the carrier's sent time; the inbox "date" can be a little later.
        val candidates = listOf(SmsLookup.Candidate(bank, withRef, t + 25 * 60_000, t))
        assertNotNull(SmsLookup.pick(candidates, keyOf(withRef), t))
    }

    @Test
    fun worksForMessagesWithoutAReferenceNumber() {
        val noRef = "Rs.120.00 debited from A/c XX1234 on 25-10-26 to ZOMATO"
        val key = keyOf(noRef)
        val found = SmsLookup.pick(listOf(SmsLookup.Candidate(bank, noRef, t + 90_000, 0)), key, t)
        assertNotNull(found)
        assertNull(found!!.reference)
    }
}
