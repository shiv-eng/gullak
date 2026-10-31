package com.shivangi.gullak.core.ai.rules

import com.shivangi.gullak.core.model.ai.GoalLanguage
import com.shivangi.gullak.core.model.ai.ParsedGoal
import com.shivangi.gullak.core.model.money.Money
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The brief's 12 test sentences (today = 31 Oct 2026) through the offline parser. */
class RuleBasedGoalParserTest {

    private val today = LocalDate.of(2026, 10, 31)
    private val parser = RuleBasedGoalParser()

    private fun parse(text: String) = parser.parse(text, today)

    private fun assertGoal(
        parsed: ParsedGoal,
        name: String?,
        rupees: Long?,
        date: String?,
    ) {
        assertEquals("name", name, parsed.goalName)
        assertEquals("amount", rupees?.let(Money::fromRupees), parsed.targetAmount)
        assertEquals("date", date?.let(LocalDate::parse), parsed.targetDate)
    }

    @Test
    fun sentence01AgleDecember() =
        assertGoal(parse("Bhai ki shaadi, agle December tak, 50 hazaar"), "Bhai ki shaadi", 50_000, "2027-12-31")

    @Test
    fun sentence02MonthOnlyEnglish() = assertGoal(parse("Goa trip in March, 30,000"), "Goa trip", 30_000, "2027-03-31")

    @Test
    fun sentence03MonthOnlyHinglish() = assertGoal(
        parse("Beti ki school fees, April mein, 45 hazaar"),
        "Beti ki school fees",
        45_000,
        "2027-04-30",
    )

    @Test
    fun sentence04NeedsAmountAndDate() = assertGoal(parse("Mujhe naya phone lena hai"), "Naya phone", null, null)

    @Test
    fun sentence05ByJune() =
        assertGoal(parse("Laptop for my son, 36000, by June"), "Laptop for my son", 36_000, "2027-06-30")

    @Test
    fun sentence06FestivalBecomesDateHint() {
        val parsed = parse("Diwali ke liye 20 hazaar")

        assertGoal(parsed, "Diwali", 20_000, null)
        assertEquals("Diwali", parsed.dateHint)
    }

    @Test
    fun sentence07MonthsFromToday() =
        assertGoal(parse("6 mahine baad Goa jaana hai, 30 hazaar"), "Goa", 30_000, "2027-04-30")

    @Test
    fun sentence08DedhLakhInTwoYears() =
        assertGoal(parse("Bike ke liye dedh lakh, 2 saal mein"), "Bike", 1_50_000, "2028-10-31")

    @Test
    fun sentence09NeedsDate() = assertGoal(parse("Emergency fund banana hai, 1 lakh"), "Emergency fund", 1_00_000, null)

    @Test
    fun sentence10Devanagari() {
        val parsed = parse("मुझे अगले साल मई तक 25000 बचाने हैं")

        assertGoal(parsed, null, 25_000, "2027-05-31")
        assertEquals(GoalLanguage.HINDI, parsed.language)
    }

    @Test
    fun sentence11NeedsEverything() = assertGoal(parse("Kuch paise bachane hain"), null, null, null)

    @Test
    fun sentence12DayAndMonth() =
        assertGoal(parse("Maa ka birthday gift, 5000, 15 January"), "Maa ka birthday gift", 5_000, "2027-01-15")

    @Test
    fun demoScriptLaptop() = assertGoal(parse("Laptop ke liye 36 hazaar, June tak"), "Laptop", 36_000, "2027-06-30")

    @Test
    fun devanagariDemoSentenceFromSpeechRecognition() =
        assertGoal(parse("भाई की शादी अगले दिसंबर तक 50 हज़ार"), "भाई की शादी", 50_000, "2027-12-31")

    @Test
    fun followUpAnswerIsCombinedWithTheFirstTurn() =
        assertGoal(parse("Mujhe naya phone lena hai, 20 hazaar, March tak"), "Naya phone", 20_000, "2027-03-31")

    @Test
    fun otherDateForms() {
        assertEquals(LocalDate.parse("2026-11-30"), parse("agle mahine 5000").targetDate)
        assertEquals(LocalDate.parse("2027-10-31"), parse("agle saal tak 50000").targetDate)
        assertEquals(LocalDate.parse("2028-03-31"), parse("March 2028 tak 50000").targetDate)
        assertEquals(LocalDate.parse("2027-01-15"), parse("by January 15, 9000").targetDate)
        assertEquals(LocalDate.parse("2028-10-31"), parse("do saal mein 1 lakh").targetDate)
        assertNull(parse("31 February ko 9000").targetDate)
    }

    @Test
    fun detectsLanguage() {
        assertEquals(GoalLanguage.HINGLISH, parse("Bhai ki shaadi, 50 hazaar").language)
        assertEquals(GoalLanguage.ENGLISH, parse("Goa trip in March, 30,000").language)
        assertEquals(GoalLanguage.HINDI, parse("दिवाली के लिए 20 हज़ार").language)
    }

    @Test
    fun smallNumbersWithoutRupeeSignAreNotAmounts() {
        assertNull(parse("3 bacchon ki fees").targetAmount)
        assertEquals(Money.fromRupees(50), parse("₹50 roz").targetAmount)
    }

    private fun assertReads(
        sentence: String,
        name: String?,
        rupees: Long?,
        date: String?,
    ) {
        val goal = RuleBasedGoalParser().parse(sentence, LocalDate.of(2026, 10, 31))
        assertEquals(sentence, name, goal.goalName)
        assertEquals(sentence, rupees?.let { Money.fromRupees(it) }, goal.targetAmount)
        assertEquals(sentence, date?.let(LocalDate::parse), goal.targetDate)
    }

    @Test
    fun readsGoalsTheWaySpeechWritesThem() {
        assertReads("bhai ki shadi me 10000 rs dene h 15 march ko", "Bhai ki shadi", 10_000, "2027-03-15")
        assertReads("Bhai ki shadi mein ₹10000 dene hain 15 March ko", "Bhai ki shadi", 10_000, "2027-03-15")
        assertReads("bhai ki shadi me 10,000 rupees dene hain 15 March tak", "Bhai ki shadi", 10_000, "2027-03-15")
        assertReads("10000 rs bhai ki shadi me dene h 5 april ko", "Bhai ki shadi", 10_000, "2027-04-05")
        assertReads(
            "I need 10000 rupees for my brother's wedding on 15th March",
            "Brother's wedding",
            10_000,
            "2027-03-15",
        )
    }

    @Test
    fun readsAmountsSaidInWords() {
        assertReads("bhai ki shadi me das hazar dene hai december tak", "Bhai ki shadi", 10_000, "2026-12-31")
        assertReads("goa jana hai paanch hazar rupaye me may me", "Goa", 5_000, "2027-05-31")
        assertReads("brother's wedding 10k by 15 march 2027", "Brother's wedding", 10_000, "2027-03-15")
        assertReads("phone cover paanch sau 15 december", "Phone cover", 500, "2026-12-15")
        assertReads("bike do lakh 2 saal mein", "Bike", 2_00_000, "2028-10-31")
    }

    @Test
    fun aNumberWordOnItsOwnIsNotAnAmount() {
        assertReads("do log goa jayenge", "Do log goa jayenge", null, null)
    }

    @Test
    fun readsDayOfMonthAndNumericDates() {
        assertReads("shadi 10000 15 tarikh ko", "Shadi", 10_000, "2026-11-15")
        assertReads("shadi 10000 15 tarikh ko march", "Shadi", 10_000, "2027-03-15")
        assertReads("shadi 10000 march ki 15", "Shadi", 10_000, "2027-03-15")
        assertReads("shadi 10000 15/03/2027 ko", "Shadi", 10_000, "2027-03-15")
        assertReads("shadi 10000 15-3-27", "Shadi", 10_000, "2027-03-15")
        assertReads("shadi 10000 31/02/2027", "Shadi", 10_000, null)
    }

    @Test
    fun aLaterReplyCorrectsTheAmountOrDateAndKeepsTheName() {
        val goal = RuleBasedGoalParser().parse(
            listOf("bhai ki shadi 10000 15 march", "nahi 20 hazaar"),
            LocalDate.of(2026, 10, 31),
        )

        assertEquals("Bhai ki shadi", goal.goalName)
        assertEquals(Money.fromRupees(20_000), goal.targetAmount)
        assertEquals(LocalDate.of(2027, 3, 15), goal.targetDate)
    }
}
