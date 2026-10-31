package com.shivangi.gullak.core.data.clock

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.shivangi.gullak.core.data.InMemoryPreferencesDataStore
import com.shivangi.gullak.core.data.repository.DataStoreSettingsRepository
import com.shivangi.gullak.core.model.ai.AiPath
import com.shivangi.gullak.core.model.settings.AppSettings
import com.shivangi.gullak.core.model.settings.SpeechLanguage
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** DemoClock and the DataStore-backed settings. */
class DemoClockTest {

    private val realToday = LocalDate.of(2026, 11, 20)
    private val systemClock = Clock.fixed(realToday.atTime(10, 30).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private class Fixture(
        val store: DataStore<Preferences>,
        val settings: DataStoreSettingsRepository,
        val clock: DemoClock,
    )

    private fun runClockTest(block: suspend TestScope.(Fixture) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        val store = InMemoryPreferencesDataStore()
        val settings = DataStoreSettingsRepository(store)
        block(Fixture(store, settings, newClock(store, settings)))
    }

    private fun TestScope.newClock(
        store: DataStore<Preferences>,
        settings: DataStoreSettingsRepository,
    ) = DemoClock(settings, DemoDateStore(store), systemClock, backgroundScope)

    @Test
    fun demoModeStartsOnTheWorkedExamplesDate() = runClockTest { f ->
        assertEquals(DemoClock.DEMO_START_DATE, LocalDate.now(f.clock))
        assertEquals(DemoClock.DEMO_START_DATE, f.clock.today.first())
    }

    @Test
    fun settingTheDateMovesTodayImmediatelyAndPersists() = runClockTest { f ->
        val february = LocalDate.of(2027, 2, 28)

        f.clock.setDate(february)

        assertEquals(february, LocalDate.now(f.clock))
        assertEquals(february, f.clock.today.first())
        // A new clock (as after process death) reads the stored date back.
        val restarted = newClock(f.store, f.settings)
        assertEquals(february, restarted.today.first { it == february })
        assertEquals(february, LocalDate.now(restarted))
    }

    @Test
    fun resetReturnsToTheDemoStartOrTheRealDate() = runClockTest { f ->
        f.clock.setDate(LocalDate.of(2027, 3, 5))
        f.clock.reset()
        assertEquals(DemoClock.DEMO_START_DATE, LocalDate.now(f.clock))

        f.settings.setDemoMode(false)

        assertEquals(realToday, f.clock.today.first { it == realToday })
        assertEquals(realToday, LocalDate.now(f.clock))
    }

    @Test
    fun settingsHaveDefaultsAndPersistChanges() = runClockTest { f ->
        assertEquals(AppSettings(), f.settings.settings.first())

        f.settings.setVoiceReplies(false)
        f.settings.setSpeechLanguage(SpeechLanguage.ENGLISH_INDIA)
        f.settings.setLastAiPath(AiPath.RULES)

        assertEquals(
            AppSettings(
                demoMode = true,
                voiceReplies = false,
                speechLanguage = SpeechLanguage.ENGLISH_INDIA,
                lastAiPath = AiPath.RULES,
            ),
            f.settings.settings.first(),
        )
    }
}
