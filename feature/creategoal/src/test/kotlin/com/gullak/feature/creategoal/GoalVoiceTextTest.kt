package com.gullak.feature.creategoal

import org.junit.Assert.assertEquals
import org.junit.Test

class GoalVoiceTextTest {

    @Test
    fun plainYesInAnyLanguageConfirms() {
        listOf("haan", "Haan theek hai", "yes that is right", "Ok", "हाँ सही है", "ji").forEach {
            assertEquals(it, ReadbackReply.YES, readbackReply(it))
        }
    }

    @Test
    fun plainNoAsksWhatToChange() {
        listOf("nahi", "No", "galat hai", "नहीं").forEach {
            assertEquals(it, ReadbackReply.NO, readbackReply(it))
        }
    }

    @Test
    fun anythingWithDetailsIsACorrection() {
        listOf("nahi 20 hazaar", "haan but 20 march", "date 15 april", "").forEach {
            assertEquals(it, ReadbackReply.CORRECTION, readbackReply(it))
        }
    }

    private val goals = listOf("Laptop", "New phone", "Bhai ki shaadi", "Goa trip", "Laptop for my son", "Phone cover")

    @Test
    fun nothingTypedShowsTheCommonGoals() {
        assertEquals(goals, goalSuggestions(" ", goals))
    }

    @Test
    fun typingCompletesThePhraseIgnoringCaseWithPrefixMatchesFirst() {
        assertEquals(listOf("Laptop", "Laptop for my son"), goalSuggestions("lap", goals))
        assertEquals(listOf("Phone cover", "New phone"), goalSuggestions("PHONE", goals))
    }

    @Test
    fun anExactNameIsNotSuggestedBackAndNoMatchShowsNothing() {
        assertEquals(listOf("Laptop for my son"), goalSuggestions("laptop", goals))
        assertEquals(emptyList<String>(), goalSuggestions("zzz", goals))
    }
}
