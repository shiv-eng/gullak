package com.shivangi.gullak.core.ai.demo

import com.shivangi.gullak.core.model.money.Money
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DemoAiResponsesTest {

    private val demo = DemoAiResponses()

    private val sentences = listOf(
        "Bhai ki shaadi, agle December tak, 50 hazaar",
        "Goa trip in March, 30,000",
        "Beti ki school fees, April mein, 45 hazaar",
        "Mujhe naya phone lena hai",
        "Laptop for my son, 36000, by June",
        "Diwali ke liye 20 hazaar",
        "6 mahine baad Goa jaana hai, 30 hazaar",
        "Bike ke liye dedh lakh, 2 saal mein",
        "Emergency fund banana hai, 1 lakh",
        "मुझे अगले साल मई तक 25000 बचाने हैं",
        "Kuch paise bachane hain",
        "Maa ka birthday gift, 5000, 15 January",
    )

    @Test
    fun everyTestSentenceHasASavedAnswer() {
        sentences.forEach { assertNotNull(it, demo.match(it)) }
    }

    @Test
    fun matchingToleratesCasePunctuationAndSpelling() {
        val goal = demo.match("bhai ki shadi agle december tak 50 hazar")

        assertEquals(Money.fromRupees(50_000), goal?.targetAmount)
        assertEquals(LocalDate.parse("2027-12-31"), goal?.targetDate)
    }

    @Test
    fun devanagariTranscriptOfTheDemoSentenceMatches() {
        val goal = demo.match("भाई की शादी अगले दिसंबर तक 50 हज़ार")

        assertEquals("Bhai ki shaadi", goal?.goalName)
    }

    @Test
    fun demoScriptLaptopSentence() {
        val goal = demo.match("Laptop ke liye 36 hazaar, June tak")

        assertEquals(Money.fromRupees(36_000), goal?.targetAmount)
        assertEquals(LocalDate.parse("2027-06-30"), goal?.targetDate)
    }

    @Test
    fun followUpCasesCarryAQuestion() {
        assertNotNull(demo.match("Mujhe naya phone lena hai")?.followUp)
        assertNotNull(demo.match("Kuch paise bachane hain")?.followUp)
    }

    @Test
    fun unrelatedTextDoesNotMatch() {
        assertNull(demo.match("Car ke liye 5 lakh, 3 saal mein"))
        assertNull(demo.match(""))
    }
}
