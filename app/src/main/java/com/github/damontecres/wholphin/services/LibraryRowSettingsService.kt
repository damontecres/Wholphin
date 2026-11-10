package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.annotation.StringRes
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.ui.toServerString
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the display settings for each row on a library's recommended tab, per user and library.
 *
 * Rows without saved settings use the library's defaults.
 */
@Singleton
class LibraryRowSettingsService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val keyValueService: KeyValueService,
    ) {
        /**
         * A stable key for a row, based on the resource name of its title (for example
         * "recently_added"), which does not change between app versions
         */
        fun rowKey(
            @StringRes title: Int,
        ): String = context.resources.getResourceEntryName(title)

        fun flow(
            userId: UUID,
            libraryId: UUID,
        ): Flow<Map<String, HomeRowViewOptions>> = keyValueService.get(userId, key(libraryId), emptyMap<String, HomeRowViewOptions>())

        suspend fun get(
            userId: UUID,
            libraryId: UUID,
        ): Map<String, HomeRowViewOptions> = flow(userId, libraryId).first()

        /**
         * Save the settings for one row, or remove them (go back to the defaults) if null
         */
        suspend fun save(
            userId: UUID,
            libraryId: UUID,
            rowKey: String,
            viewOptions: HomeRowViewOptions?,
        ) {
            val current = get(userId, libraryId)
            val updated =
                if (viewOptions == null) {
                    current - rowKey
                } else {
                    current + (rowKey to viewOptions)
                }
            keyValueService.save(userId, key(libraryId), updated)
        }

        private fun key(libraryId: UUID) = "library_row_settings_${libraryId.toServerString()}"
    }
