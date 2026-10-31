package com.shivangi.gullak.core.model.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    private fun rupees(amount: Long) = Money.fromRupees(amount)

    @Test
    fun arithmeticWorksInPaise() {
        assertEquals(rupees(150), rupees(100) + rupees(50))
        assertEquals(rupees(50), rupees(100) - rupees(50))
        assertEquals(rupees(36_000), rupees(4_500) * 8)
        assertEquals(Money(2_150), Money(2_000) + Money(150))
    }

    @Test
    fun comparesByAmount() {
        assertTrue(rupees(21) < rupees(22))
        assertTrue(rupees(1_000) > Money(99_999))
        assertEquals(rupees(500), maxOf(rupees(500), rupees(499)))
    }

    @Test
    fun roundUpToRupeesRoundsToNextStep() {
        assertEquals(rupees(4_450), rupees(4_401).roundUpToRupees(50))
        assertEquals(rupees(4_450), rupees(4_450).roundUpToRupees(50))
        assertEquals(rupees(155), Money(15_101).roundUpToRupees(5))
        assertEquals(Money.ZERO, Money.ZERO.roundUpToRupees(10))
    }

    @Test
    fun splitRoundedUpRoundsTheExactQuotient() {
        // ₹36,000 / 238 = ₹151.26 → ₹155 (step 5)
        assertEquals(rupees(155), rupees(36_000).splitRoundedUp(238, 5))
        // ₹36,000 / 34 = ₹1,058.82 → ₹1,060 (step 10)
        assertEquals(rupees(1_060), rupees(36_000).splitRoundedUp(34, 10))
        // Exact division stays exact.
        assertEquals(rupees(4_500), rupees(36_000).splitRoundedUp(8, 50))
        // ₹4,500 / 2 = ₹2,250, already a multiple of 50.
        assertEquals(rupees(2_250), rupees(4_500).splitRoundedUp(2, 50))
    }

    @Test
    fun sumOfEmptyListIsZero() {
        assertEquals(Money.ZERO, emptyList<Money>().sum())
        assertEquals(rupees(30), listOf(rupees(10), rupees(20)).sum())
    }

    @Test
    fun formatsWithIndianGrouping() {
        assertEquals("₹0", IndianMoneyFormat.format(Money.ZERO))
        assertEquals("₹21", IndianMoneyFormat.format(rupees(21)))
        assertEquals("₹999", IndianMoneyFormat.format(rupees(999)))
        assertEquals("₹1,000", IndianMoneyFormat.format(rupees(1_000)))
        assertEquals("₹50,000", IndianMoneyFormat.format(rupees(50_000)))
        assertEquals("₹1,50,000", IndianMoneyFormat.format(rupees(1_50_000)))
        assertEquals("₹50,00,000", IndianMoneyFormat.format(rupees(50_00_000)))
        assertEquals("₹1,00,00,000", IndianMoneyFormat.format(rupees(1_00_00_000)))
    }

    @Test
    fun formatsPaiseOnlyWhenPresentAndNegativeAmounts() {
        assertEquals("₹21.50", IndianMoneyFormat.format(Money(2_150)))
        assertEquals("₹0.05", IndianMoneyFormat.format(Money(5)))
        assertEquals("-₹4,500", IndianMoneyFormat.format(rupees(-4_500)))
        assertEquals("₹36,000", rupees(36_000).toString())
    }
}
