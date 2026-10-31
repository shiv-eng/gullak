package com.shivangi.gullak.core.data.demo

import android.content.Context
import com.shivangi.gullak.core.data.R
import com.shivangi.gullak.core.domain.di.IoDispatcher
import com.shivangi.gullak.core.domain.gateway.FestivalCalendar
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Festival dates from `res/raw/festivals.json`, which holds only dates checked against the Government of
 * India's gazetted holiday lists. An unknown festival or a year not yet listed returns null, so the user is
 * asked for the date instead.
 */
@Singleton
internal class BundledFestivalCalendar @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FestivalCalendar {

    private class Festival(
        val aliases: Set<String>,
        val dates: List<LocalDate>,
    )

    private val mutex = Mutex()
    private var festivals: List<Festival>? = null

    override suspend fun nextOccurrence(
        festival: String,
        after: LocalDate,
    ): LocalDate? {
        val key = festival.trim().lowercase()
        return load()
            .firstOrNull { key in it.aliases }
            ?.dates
            ?.filter { it > after }
            ?.minOrNull()
    }

    private suspend fun load(): List<Festival> = mutex.withLock {
        festivals ?: withContext(ioDispatcher) { parse(readJson()) }.also { festivals = it }
    }

    private fun readJson(): String =
        context.resources.openRawResource(R.raw.festivals).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun parse(json: String): List<Festival> {
        val array = JSONObject(json).getJSONArray("festivals")
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val aliases = item.getJSONArray("aliases")
            val dates = item.getJSONArray("dates")
            Festival(
                aliases = (
                    (0 until aliases.length()).map { aliases.getString(it).lowercase() } +
                        item.getString("name").lowercase()
                    ).toSet(),
                dates = (0 until dates.length()).map { LocalDate.parse(dates.getString(it)) },
            )
        }
    }
}
