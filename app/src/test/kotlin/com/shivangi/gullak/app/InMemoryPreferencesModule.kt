package com.shivangi.gullak.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.shivangi.gullak.core.data.di.PreferencesModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The file-backed store cannot overwrite its file on Windows JVMs. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PreferencesModule::class])
object InMemoryPreferencesModule {
    @Provides
    @Singleton
    fun preferences(): DataStore<Preferences> = object : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())

        override val data: StateFlow<Preferences> = state

        @Synchronized
        private fun current() = state.value

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(current()).also { state.value = it }
    }
}
