package com.shivangi.gullak.core.data.clock

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Persists the demo clock's date override so the demo survives process death. */
internal class DemoDateStore @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val override: Flow<LocalDate?> = dataStore.data.map { prefs -> prefs[DEMO_DATE]?.let(LocalDate::parse) }

    suspend fun set(date: LocalDate?) {
        dataStore.edit { prefs ->
            if (date == null) prefs.remove(DEMO_DATE) else prefs[DEMO_DATE] = date.toString()
        }
    }

    private companion object {
        val DEMO_DATE = stringPreferencesKey("demo_date")
    }
}
