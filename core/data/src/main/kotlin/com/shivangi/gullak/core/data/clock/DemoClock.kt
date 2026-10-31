package com.shivangi.gullak.core.data.clock

import com.shivangi.gullak.core.domain.di.ApplicationScope
import com.shivangi.gullak.core.domain.gateway.DemoClockController
import com.shivangi.gullak.core.domain.repository.SettingsRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** The device's real clock, as opposed to the app-wide [Clock] which is the [DemoClock]. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SystemClock

/**
 * The app's [Clock] and the demo's date control in one object, so `PlanEngine` and every flow agree on "today".
 *
 * Today is the stored override if there is one; otherwise [DEMO_START_DATE] in demo mode (so the worked
 * examples match the demo script), or the real date. The in-memory state updates synchronously when the date
 * is set, then persists to DataStore.
 */
@Singleton
class DemoClock @Inject internal constructor(
    settings: SettingsRepository,
    private val dateStore: DemoDateStore,
    @SystemClock private val systemClock: Clock,
    @ApplicationScope scope: CoroutineScope,
) : Clock(),
    DemoClockController {

    private val override = MutableStateFlow<LocalDate?>(null)
    private val demoMode = MutableStateFlow(true)

    init {
        scope.launch { dateStore.override.collect { override.value = it } }
        scope.launch { settings.settings.collect { demoMode.value = it.demoMode } }
    }

    override val today: Flow<LocalDate> =
        combine(override, demoMode) { date, demo -> resolve(date, demo) }.distinctUntilChanged()

    override suspend fun setDate(date: LocalDate) {
        override.value = date
        dateStore.set(date)
    }

    override suspend fun reset() {
        override.value = null
        dateStore.set(null)
    }

    override fun getZone(): ZoneId = systemClock.zone

    override fun withZone(zone: ZoneId): Clock = systemClock.withZone(zone)

    /** The current time of day on the demo date, so `LocalDate.now(this)` is the demo date. */
    override fun instant(): Instant {
        val date = resolve(override.value, demoMode.value)
        return date.atTime(LocalTime.now(systemClock)).atZone(systemClock.zone).toInstant()
    }

    private fun resolve(
        date: LocalDate?,
        demo: Boolean,
    ): LocalDate = date ?: if (demo) DEMO_START_DATE else LocalDate.now(systemClock)

    companion object {
        /** The worked examples' "today" (brief section 5). */
        val DEMO_START_DATE: LocalDate = LocalDate.of(2026, 10, 31)
    }
}
