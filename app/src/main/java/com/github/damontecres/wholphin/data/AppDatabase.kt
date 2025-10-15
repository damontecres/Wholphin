package com.github.damontecres.wholphin.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [JellyfinServer::class, JellyfinUser::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): JellyfinServerDao
}
