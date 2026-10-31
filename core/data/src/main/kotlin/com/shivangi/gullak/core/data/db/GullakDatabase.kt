package com.shivangi.gullak.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [GoalEntity::class, DebitEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
internal abstract class GullakDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao

    abstract fun debitDao(): DebitDao

    companion object {
        const val NAME = "gullak.db"
    }
}
