package com.shivangi.gullak.core.model.money

/**
 * An amount of Indian rupees stored as whole paise. Money is never a floating-point number.
 */
@JvmInline
value class Money(val paise: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(Math.addExact(paise, other.paise))

    operator fun minus(other: Money): Money = Money(Math.subtractExact(paise, other.paise))

    operator fun times(count: Int): Money = Money(Math.multiplyExact(paise, count.toLong()))

    override fun compareTo(other: Money): Int = paise.compareTo(other.paise)

    val isPositive: Boolean get() = paise > 0

    /** Rounds up to the next multiple of [step] whole rupees (e.g. step 50: ₹4,401 → ₹4,450). */
    fun roundUpToRupees(step: Long): Money {
        require(step > 0) { "step must be positive" }
        val stepPaise = step * PAISE_PER_RUPEE
        return Money(Math.floorDiv(paise + stepPaise - 1, stepPaise) * stepPaise)
    }

    /**
     * Splits this amount into [parts] equal shares, each rounded up to a multiple of [stepRupees].
     * Rounding the exact quotient, not a truncated one, so ₹36,000 / 238 days → ₹155 with step 5.
     */
    fun splitRoundedUp(
        parts: Int,
        stepRupees: Long,
    ): Money {
        require(parts > 0) { "parts must be positive" }
        require(stepRupees > 0) { "stepRupees must be positive" }
        val stepPaise = stepRupees * PAISE_PER_RUPEE
        val divisor = Math.multiplyExact(parts.toLong(), stepPaise)
        return Money(Math.floorDiv(paise + divisor - 1, divisor) * stepPaise)
    }

    override fun toString(): String = IndianMoneyFormat.format(this)

    companion object {
        const val PAISE_PER_RUPEE = 100L

        val ZERO = Money(0)

        fun fromRupees(rupees: Long): Money = Money(Math.multiplyExact(rupees, PAISE_PER_RUPEE))
    }
}

fun Iterable<Money>.sum(): Money = fold(Money.ZERO) { acc, money -> acc + money }
