package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.PageConfig
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import org.jellyfin.sdk.model.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-lifetime in-memory cache for custom-page rows.
 *
 * The Wholphin home page benefits from a long-lived state because its [Destination] sits at index 0
 * of the back stack and the HomeViewModel is never recreated. Custom pages get a fresh ViewModel on
 * every navigation (the back stack pops and re-pushes the entry), so without this cache every visit
 * re-fetches all rows. Keyed by userId to avoid leaking content across user switches.
 */
@Singleton
class CustomPageRowsCache
    @Inject
    constructor() {
        private val cache = ConcurrentHashMap<String, CachedPageData>()

        fun get(
            userId: UUID,
            pageId: String,
        ): CachedPageData? = cache[key(userId, pageId)]

        fun put(
            userId: UUID,
            pageId: String,
            data: CachedPageData,
        ) {
            cache[key(userId, pageId)] = data
        }

        fun clear() {
            cache.clear()
        }

        private fun key(
            userId: UUID,
            pageId: String,
        ) = "$userId:$pageId"
    }

data class CachedPageData(
    val page: PageConfig,
    val rows: List<HomeRowLoadingState>,
)
