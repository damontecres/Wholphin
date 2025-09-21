package com.github.damontecres.dolphin.hilt

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.room.Room
import com.github.damontecres.dolphin.data.DolphinDatabase
import com.github.damontecres.dolphin.data.JellyfinServerDao
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.preferences.UserPreferencesSerializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): DolphinDatabase =
        Room
            .databaseBuilder(
                context,
                DolphinDatabase::class.java,
                "dolphin",
            ).build()

    @Provides
    @Singleton
    fun serverDao(db: DolphinDatabase): JellyfinServerDao = db.serverDao()

    @Provides
    @Singleton
    fun userPreferencesDataStore(
        @ApplicationContext context: Context,
        userPreferencesSerializer: UserPreferencesSerializer,
    ): DataStore<UserPreferences> =
        DataStoreFactory.create(
            serializer = userPreferencesSerializer,
            produceFile = { context.dataStoreFile("user_preferences.pb") },
            corruptionHandler =
                ReplaceFileCorruptionHandler(
                    produceNewData = { UserPreferences.getDefaultInstance() },
                ),
        )
}
