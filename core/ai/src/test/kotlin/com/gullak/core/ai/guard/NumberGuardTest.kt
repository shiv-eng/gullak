package com.gullak.core.ai.guard

import com.gullak.core.ai.text.IndianNumbers
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberGuardTest {

    private val guard = NumberGuard()

    private val laptopInput = """
        {"goal_name":"Laptop","chosen_option":{"frequency":"monthly","amount_per_debit_rupees":4500,
        "number_of_debits":8,"first_debit":"2026-11-05","last_debit":"2027-06-05","total_rupees":36000},
        "remaining_rupees":36000,"months_to_goal":7,"destination":"Paytm Money low-risk fund",
        "destination_reason":"Short goal: gold carries 3% GST and its price can fall."}
    """.trimIndent()

    private fun values(text: String) = IndianNumbers.find(text).map { it.value.toPlainString() }

    @Test
    fun readsIndianWaysOfWritingAmounts() {
        assertEquals(listOf("150000"), values("₹1,50,000"))
        assertEquals(listOf("50000"), values("50,000"))
        assertEquals(listOf("50000"), values("50 hazaar"))
        assertEquals(listOf("50000"), values("50 हज़ार"))
        assertEquals(listOf("150000"), values("1.5 lakh"))
        assertEquals(listOf("150000"), values("dedh lakh"))
        assertEquals(listOf("250000"), values("dhai lakh"))
        assertEquals(listOf("225000"), values("sava 2 lakh"))
        assertEquals(listOf("350000"), values("saadhe 3 lakh"))
        assertEquals(listOf("4500"), values("४५००"))
        assertEquals(listOf("4500"), values("4.5 hazaar"))
        assertEquals(listOf("20000000"), values("2 करोड़"))
    }

    @Test
    fun marksRupeeSignsAndScaleWords() {
        val (rupee, plain, scaled) = IndianNumbers.find("₹500 then 7 then 5 lakh")

        assertTrue(rupee.hasRupeeSign)
        assertFalse(plain.hasRupeeSign || plain.hasScaleWord)
        assertTrue(scaled.hasScaleWord)
    }

    @Test
    fun aFractionWordWithoutScaleIsNotANumber() {
        assertTrue(IndianNumbers.find("dedh saal mein").isEmpty())
    }

    @Test
    fun allowsEveryNumberFromThePlanInDifferentForms() {
        val reply = "Har mahine ki 5 tareekh ko ₹4,500 bachaiye, 8 baar, 5 Nov 2026 se 5 Jun 2027 tak. " +
            "36 hazaar jud jayenge, 7 mahine mein. Sone par 3% GST lagta hai."

        assertTrue(guard.isGrounded(reply, laptopInput))
    }

    @Test
    fun rejectsAnInventedNumber() {
        val reply = "Save ₹4,600 a month and you will have ₹36,000 by June."

        assertFalse(guard.isGrounded(reply, laptopInput))
        assertEquals(setOf(BigDecimal(4_600)), guard.unexpectedNumbers(reply, laptopInput))
    }

    @Test
    fun rejectsAnInventedReturnOrRate() {
        assertFalse(guard.isGrounded("Gold may give 12% a year.", laptopInput))
        assertFalse(guard.isGrounded("Aapko 1 lakh milenge.", laptopInput))
    }

    @Test
    fun replyWithoutNumbersIsGrounded() {
        assertTrue(guard.isGrounded("Thoda thoda bachaiye, laptop aa jayega.", laptopInput))
    }

    @Test
    fun devanagariDigitsInTheReplyAreChecked() {
        assertTrue(guard.isGrounded("हर महीने ₹४,५०० बचाइए, ८ बार।", laptopInput))
        assertFalse(guard.isGrounded("हर महीने ₹४,६०० बचाइए।", laptopInput))
    }
}
