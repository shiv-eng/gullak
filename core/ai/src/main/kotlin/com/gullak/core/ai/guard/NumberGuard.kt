package com.gullak.core.ai.guard

import com.gullak.core.ai.text.IndianNumbers
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Checks that every number in a model's explanation already appears in the plan the model was given.
 * "₹4,500", "4500", "4.5 hazaar" and "४५००" are all 4500. Dates in the input ("2027-06-30") allow their day,
 * month and year. A reply with any other number is discarded for a template.
 *
 * Limitation: numbers written as words ("aath", "eight") are not read; the explanation prompt asks for digits.
 */
class NumberGuard @Inject constructor() {

    fun allowedNumbers(planInput: String): Set<BigDecimal> = numbersIn(planInput)

    fun isGrounded(
        reply: String,
        planInput: String,
    ): Boolean = unexpectedNumbers(reply, planInput).isEmpty()

    fun unexpectedNumbers(
        reply: String,
        planInput: String,
    ): Set<BigDecimal> = numbersIn(reply) - allowedNumbers(planInput)

    private fun numbersIn(text: String): Set<BigDecimal> = IndianNumbers.find(text).map { it.value }.toSet()
}
