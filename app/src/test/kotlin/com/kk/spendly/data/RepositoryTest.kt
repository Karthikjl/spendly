package com.kk.spendly.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryTest {
    @Test
    fun namedMerchantsAreRemembered() {
        assertTrue(Repository.remembersMerchant("SWIGGY"))
        assertTrue(Repository.remembersMerchant("john@okaxis"))
        assertTrue(Repository.remembersMerchant("Mr S V Dhanavel"))
    }

    @Test
    fun unknownAndBlankMerchantsAreNeverShared() {
        assertFalse(Repository.remembersMerchant("Unknown"))
        assertFalse(Repository.remembersMerchant("unknown"))
        assertFalse(Repository.remembersMerchant(""))
        assertFalse(Repository.remembersMerchant("   "))
    }

    @Test
    fun kindsOfPaymentAreNotRemembered() {
        assertFalse(Repository.remembersMerchant("UPI payment"))
        assertFalse(Repository.remembersMerchant("Card payment"))
        assertFalse(Repository.remembersMerchant("NEFT transfer"))
        // One account, one ATM or one lender is a stable "who", so those are worth remembering.
        assertTrue(Repository.remembersMerchant("A/c ••5678"))
        assertTrue(Repository.remembersMerchant("ATM withdrawal"))
        assertTrue(Repository.remembersMerchant("EMI / auto-debit"))
    }

    @Test
    fun aBetterReadingReplacesAWorseOne() {
        assertTrue(Repository.isBetterName("Unknown", "A/c ••5678"))
        assertTrue(Repository.isBetterName("Unknown", "UPI payment"))
        assertTrue(Repository.isBetterName("UPI payment", "SWIGGY"))
        assertTrue(Repository.isBetterName("SWIGGY Refno 629812345678", "SWIGGY"))
        assertTrue(Repository.isBetterName("919951860002 -Test Bank", "SWIGGY"))
    }

    @Test
    fun aWorseOrEqualReadingNeverReplacesAGoodName() {
        assertFalse(Repository.isBetterName("SWIGGY", "SWIGGY"))
        assertFalse(Repository.isBetterName("SWIGGY", "Unknown"))
        assertFalse(Repository.isBetterName("SWIGGY", "UPI payment"))
        assertFalse(Repository.isBetterName("SWIGGY", "ZOMATO")) // two good but different names: keep the stored one
        assertFalse(Repository.isBetterName("UPI payment", "Unknown"))
    }
}
