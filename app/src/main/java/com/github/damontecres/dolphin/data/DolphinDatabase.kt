package com.github.damontecres.dolphin.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [JellyfinServer::class, JellyfinUser::class], version = 1, exportSchema = false)
abstract class DolphinDatabase : RoomDatabase() {
    abstract fun serverDao(): JellyfinServerDao
}
