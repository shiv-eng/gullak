package com.shivangi.gullak.core.domain.testing

import com.shivangi.gullak.core.domain.gateway.DemoClockController
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A [Clock] whose date tests can move. Always midnight UTC on [date]. */
class MutableClock(var date: LocalDate) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = date.atStartOfDay(ZoneOffset.UTC).toInstant()
}

/** Demo clock backed by a [MutableClock], so the engine and the flows always agree on "today". */
class FakeDemoClockController(
    val clock: MutableClock,
    private val resetDate: LocalDate = clock.date,
) : DemoClockController {
    private val state = MutableStateFlow(clock.date)

    override val today: StateFlow<LocalDate> = state

    override suspend fun setDate(date: LocalDate) {
        clock.date = date
        state.value = date
    }

    override suspend fun reset() = setDate(resetDate)
}
