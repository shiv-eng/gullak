package com.shivangi.gullak.core.model.money

/**
 * Formats rupees with Indian digit grouping: ₹21, ₹50,000, ₹1,50,000, ₹1,00,00,000.
 * Paise are shown only when non-zero (₹21.50). Digits are always Latin, matching Paytm's app.
 */
object IndianMoneyFormat {
    private const val RUPEE = "₹"
    private const val FIRST_GROUP = 3
    private const val LATER_GROUP = 2

    fun format(money: Money): String {
        val negative = money.paise < 0
        val absPaise = if (negative) -money.paise else money.paise
        val rupees = absPaise / Money.PAISE_PER_RUPEE
        val paise = absPaise % Money.PAISE_PER_RUPEE
        val grouped = groupIndian(rupees.toString())
        val fraction = if (paise == 0L) "" else ".%02d".format(paise)
        return (if (negative) "-" else "") + RUPEE + grouped + fraction
    }

    /** Groups digits as 3 from the right, then 2s: "150000" → "1,50,000". */
    internal fun groupIndian(digits: String): String {
        if (digits.length <= FIRST_GROUP) return digits
        val head = digits.dropLast(FIRST_GROUP)
        val tail = digits.takeLast(FIRST_GROUP)
        val headGroups = head.reversed().chunked(LATER_GROUP).joinToString(",").reversed()
        return "$headGroups,$tail"
    }
}
