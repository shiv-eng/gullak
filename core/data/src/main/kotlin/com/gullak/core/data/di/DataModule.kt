package com.gullak.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.gullak.core.data.UuidIdGenerator
import com.gullak.core.data.analytics.LogcatAnalytics
import com.gullak.core.data.clock.DemoClock
import com.gullak.core.data.clock.SystemClock
import com.gullak.core.data.db.DebitDao
import com.gullak.core.data.db.GoalDao
import com.gullak.core.data.db.GullakDatabase
import com.gullak.core.data.demo.BundledFestivalCalendar
import com.gullak.core.data.demo.SeedData
import com.gullak.core.data.paytm.DemoAutoPayGateway
import com.gullak.core.data.paytm.DemoSavingsDestinationGateway
import com.gullak.core.data.repository.DataStoreSettingsRepository
import com.gullak.core.data.repository.RoomDebitRepository
import com.gullak.core.data.repository.RoomGoalRepository
import com.gullak.core.domain.analytics.Analytics
import com.gullak.core.domain.di.ApplicationScope
import com.gullak.core.domain.di.IoDispatcher
import com.gullak.core.domain.gateway.AutoPayGateway
import com.gullak.core.domain.gateway.DemoClockController
import com.gullak.core.domain.gateway.DemoSeedSource
import com.gullak.core.domain.gateway.FestivalCalendar
import com.gullak.core.domain.gateway.IdGenerator
import com.gullak.core.domain.gateway.SavingsDestinationGateway
import com.gullak.core.domain.repository.DebitRepository
import com.gullak.core.domain.repository.GoalRepository
import com.gullak.core.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataBindingsModule {
    @Binds
    abstract fun goalRepository(impl: RoomGoalRepository): GoalRepository

    @Binds
    abstract fun debitRepository(impl: RoomDebitRepository): DebitRepository

    @Binds
    abstract fun settingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    /** The app-wide Clock is the demo clock, so "today" can be moved for the demo. */
    @Binds
    abstract fun clock(impl: DemoClock): Clock

    @Binds
    abstract fun demoClockController(impl: DemoClock): DemoClockController

    @Binds
    abstract fun idGenerator(impl: UuidIdGenerator): IdGenerator

    @Binds
    abstract fun analytics(impl: LogcatAnalytics): Analytics

    @Binds
    abstract fun demoSeedSource(impl: SeedData): DemoSeedSource

    @Binds
    abstract fun festivalCalendar(impl: BundledFestivalCalendar): FestivalCalendar
}

/** The simulated Paytm integrations. Swap for real Paytm Gold / Paytm Money and UPI AutoPay clients here. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class PaytmGatewaysModule {
    @Binds
    abstract fun savingsDestinationGateway(impl: DemoSavingsDestinationGateway): SavingsDestinationGateway

    @Binds
    abstract fun autoPayGateway(impl: DemoAutoPayGateway): AutoPayGateway
}

@Module
@InstallIn(SingletonComponent::class)
internal object DataProvidersModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): GullakDatabase =
        Room.databaseBuilder(context, GullakDatabase::class.java, GullakDatabase.NAME).build()

    @Provides
    fun goalDao(database: GullakDatabase): GoalDao = database.goalDao()

    @Provides
    fun debitDao(database: GullakDatabase): DebitDao = database.debitDao()

    @Provides
    @Singleton
    fun preferences(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope + ioDispatcher,
        produceFile = { context.preferencesDataStoreFile("gullak_settings") },
    )

    @Provides
    @SystemClock
    fun systemClock(): Clock = Clock.systemDefaultZone()
}
