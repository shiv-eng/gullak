package com.shivangi.gullak.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shivangi.gullak.core.data.db.GullakDatabase
import com.shivangi.gullak.core.domain.testing.TestData
import com.shivangi.gullak.core.domain.testing.TestData.debit
import com.shivangi.gullak.core.domain.testing.TestData.rupees
import com.shivangi.gullak.core.model.goal.DebitKind
import com.shivangi.gullak.core.model.goal.DebitStatus
import com.shivangi.gullak.core.model.goal.ScheduledDebit
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** DAO behaviour through the repositories, on an in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
class RoomRepositoriesTest {

    private val database = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), GullakDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val goals = RoomGoalRepository(database.goalDao())
    private val debits = RoomDebitRepository(database.debitDao())

    @After
    fun closeDatabase() = database.close()

    @Test
    fun goalWithFullScheduleRoundTrips() = runTest {
        val base = TestData.laptopGoal()
        val schedule = requireNotNull(base.schedule).copy(
            amountPerDebit = rupees(6_000),
            extraDebits = listOf(ScheduledDebit(LocalDate.of(2027, 3, 15), rupees(2_250), DebitKind.CATCH_UP)),
            excusedDates = setOf(LocalDate.of(2027, 3, 5)),
        )
        val goal = base.copy(schedule = schedule)

        goals.upsert(goal)

        assertEquals(goal, goals.getGoal(goal.id))
        assertEquals(listOf(goal), goals.observeGoals().first())
    }

    @Test
    fun oneTimeGoalHasNoSchedule() = runTest {
        val goal = TestData.laptopGoal().copy(schedule = null)

        goals.upsert(goal)

        assertNull(goals.getGoal(goal.id)?.schedule)
    }

    @Test
    fun goalsKeepCreationOrder() = runTest {
        goals.upsert(TestData.weddingGoal())
        goals.upsert(TestData.laptopGoal())
        goals.upsert(TestData.weddingGoal().copy(name = "Shaadi"))

        assertEquals(listOf("wedding", "laptop"), goals.getGoals().map { it.id })
        assertEquals("Shaadi", goals.getGoal("wedding")?.name)
    }

    @Test
    fun updatingAGoalKeepsItsDebits() = runTest {
        val goal = TestData.laptopGoal()
        goals.upsert(goal)
        debits.insert(debit(goal.id, TestData.FIRST_DEBIT, rupees(4_500)))

        goals.upsert(goal.copy(targetDate = LocalDate.of(2027, 7, 31)))

        assertEquals(1, debits.getDebits(goal.id).size)
    }

    @Test
    fun debitsAreListedByDateAndFoundById() = runTest {
        goals.upsert(TestData.laptopGoal())
        goals.upsert(TestData.weddingGoal())
        val march = debit("laptop", LocalDate.of(2027, 3, 5), rupees(4_500), DebitStatus.FAILED)
        val november = debit("laptop", TestData.FIRST_DEBIT, rupees(4_500))
        val wedding = debit("wedding", TestData.FIRST_DEBIT, rupees(3_600))
        listOf(march, november, wedding).forEach { debits.insert(it) }

        assertEquals(listOf(november, march), debits.getDebits("laptop"))
        assertEquals(listOf(november, march), debits.observeDebits("laptop").first())
        assertEquals(3, debits.observeAllDebits().first().size)
        assertEquals(march, debits.getDebit(march.id))
        assertNull(debits.getDebit("missing"))
    }

    @Test
    fun deletingGoalsCascadesToDebits() = runTest {
        goals.upsert(TestData.laptopGoal())
        debits.insert(debit("laptop", TestData.FIRST_DEBIT, rupees(4_500)))

        goals.deleteAll()

        assertTrue(goals.getGoals().isEmpty())
        assertTrue(debits.observeAllDebits().first().isEmpty())
    }

    @Test
    fun observedGoalEmitsUpdatesAndNullWhenDeleted() = runTest {
        val goal = TestData.laptopGoal()
        goals.upsert(goal)
        assertEquals(goal, goals.observeGoal(goal.id).first())

        goals.deleteAll()

        assertNull(goals.observeGoal(goal.id).first())
    }
}
