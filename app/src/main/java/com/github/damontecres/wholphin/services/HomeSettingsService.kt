package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.NavDrawerItemRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomePageSettings
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.data.model.SUPPORTED_HOME_PAGE_SETTINGS_VERSION
import com.github.damontecres.wholphin.preferences.HomePagePreferences
import com.github.damontecres.wholphin.ui.DefaultItemFields
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.components.getGenreImageMap
import com.github.damontecres.wholphin.ui.main.settings.Library
import com.github.damontecres.wholphin.ui.nav.ServerNavDrawerItem
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.util.GetGenresRequestHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.displayPreferencesApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.request.GetGenresRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeSettingsService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val userPreferencesService: UserPreferencesService,
        private val navDrawerItemRepository: NavDrawerItemRepository,
        private val latestNextUpService: LatestNextUpService,
        private val imageUrlService: ImageUrlService,
    ) {
        val jsonParser =
            Json {
                isLenient = true
                ignoreUnknownKeys = true
            }

        val currentSettings = MutableStateFlow(HomePageResolvedSettings.EMPTY)

        suspend fun saveToServer(
            userId: UUID,
            settings: HomePageSettings,
            displayPreferencesId: String = DISPLAY_PREF_ID,
        ) {
            val current = getDisplayPreferences(userId, DISPLAY_PREF_ID)
            val customPrefs =
                current.customPrefs.toMutableMap().apply {
                    put(CUSTOM_PREF_ID, jsonParser.encodeToString(settings))
                }
            api.displayPreferencesApi.updateDisplayPreferences(
                displayPreferencesId = displayPreferencesId,
                userId = userId,
                client = context.getString(R.string.app_name),
                data = current.copy(customPrefs = customPrefs),
            )
        }

        suspend fun loadFromServer(
            userId: UUID,
            displayPreferencesId: String = DISPLAY_PREF_ID,
        ): HomePageSettings? {
            val current = getDisplayPreferences(userId, displayPreferencesId)
            return current.customPrefs[DISPLAY_PREF_ID]?.let {
                Json
                    .decodeFromString<HomePageSettings>(it)
                    .takeIf { it.version <= SUPPORTED_HOME_PAGE_SETTINGS_VERSION }
            }
        }

        private suspend fun getDisplayPreferences(
            userId: UUID,
            displayPreferencesId: String,
        ) = api.displayPreferencesApi
            .getDisplayPreferences(
                userId = userId,
                displayPreferencesId = displayPreferencesId,
                client = context.getString(R.string.app_name),
            ).content

        private fun filename(userId: UUID) = "${CUSTOM_PREF_ID}_${userId.toServerString()}.json"

        @OptIn(ExperimentalSerializationApi::class)
        suspend fun saveToLocal(
            userId: UUID,
            settings: HomePageSettings,
        ) {
            val dir = File(context.filesDir, CUSTOM_PREF_ID)
            dir.mkdirs()
            File(dir, filename(userId)).outputStream().use {
                jsonParser.encodeToStream(settings, it)
            }
        }

        @OptIn(ExperimentalSerializationApi::class)
        suspend fun loadFromLocal(userId: UUID): HomePageSettings? {
            val dir = File(context.filesDir, CUSTOM_PREF_ID)
            val file = File(dir, filename(userId))
            return if (file.exists()) {
                file.inputStream().use {
                    jsonParser
                        .decodeFromStream<HomePageSettings>(it)
                        .takeIf { it.version <= SUPPORTED_HOME_PAGE_SETTINGS_VERSION }
                }
            } else {
                null
            }
        }

        suspend fun updateCurrent(userId: UUID) {
            Timber.v("Getting setting for %s", userId)
            // User local then server/remote otherwise create a default
            val settings = loadFromLocal(userId) ?: loadFromServer(userId)
            val resolvedSettings =
                if (settings != null) {
                    Timber.v("Found settings")
                    // Resolve
                    val resolvedRows = settings.rows.map { convert(it) }
                    HomePageResolvedSettings(resolvedRows)
                } else {
                    createDefault(userId)
                }

            currentSettings.update { resolvedSettings }
        }

        suspend fun createDefault(userId: UUID): HomePageResolvedSettings {
            Timber.v("Creating default settings")
            val navDrawerItems = navDrawerItemRepository.getNavDrawerItems()
            val libraries =
                navDrawerItems
                    .filter { it is ServerNavDrawerItem }
                    .map {
                        it as ServerNavDrawerItem
                        Library(it.itemId, it.name, it.type)
                    }
            val prefs =
                userPreferencesService.getCurrent().appPreferences.homePagePreferences
            val includedIds =
                navDrawerItemRepository
                    .getFilteredNavDrawerItems(navDrawerItems)
                    .filter { it is ServerNavDrawerItem }
                    .mapIndexed { index, it ->
                        val id = (it as ServerNavDrawerItem).itemId
                        val name = libraries.firstOrNull { it.itemId == id }?.name
                        val title =
                            name?.let { context.getString(R.string.recently_added_in, it) }
                                ?: context.getString(R.string.recently_added)
                        HomeRowConfigDisplay(
                            title,
                            HomeRowConfig.RecentlyAdded(
                                index,
                                id,
                                HomeRowViewOptions(),
                            ),
                        )
                    }
            val continueWatchingRows =
                if (prefs.combineContinueNext) {
                    listOf(
                        HomeRowConfigDisplay(
                            context.getString(R.string.combine_continue_next),
                            HomeRowConfig.ContinueWatchingCombined(
                                includedIds.size + 1,
                                HomeRowViewOptions(),
                            ),
                        ),
                    )
                } else {
                    listOf(
                        HomeRowConfigDisplay(
                            context.getString(R.string.continue_watching),
                            HomeRowConfig.ContinueWatching(
                                includedIds.size + 1,
                                HomeRowViewOptions(),
                            ),
                        ),
                        HomeRowConfigDisplay(
                            context.getString(R.string.next_up),
                            HomeRowConfig.NextUp(
                                includedIds.size + 2,
                                HomeRowViewOptions(),
                            ),
                        ),
                    )
                }
            val rowConfig =
                continueWatchingRows + includedIds +
                    // TODO remove after testing
                    listOf(
                        HomeRowConfigDisplay(
                            "Collection",
                            HomeRowConfig.ByParent(
                                id = 100,
                                parentId = "34ab6fd1f51c41bb014981f2e334f465".toUUID(),
                                recursive = true,
                                viewOptions = HomeRowViewOptions(),
                            ),
                        ),
                        HomeRowConfigDisplay(
                            "Playlist",
                            HomeRowConfig.ByParent(
                                id = 101,
                                parentId = "f94be36e9836127a0bccfc7843b19e5b".toUUID(),
                                recursive = true,
                                viewOptions = HomeRowViewOptions(),
                            ),
                        ),
                    )
            return HomePageResolvedSettings(rowConfig)
        }

        suspend fun convert(config: HomeRowConfig): HomeRowConfigDisplay =
            when (config) {
                is HomeRowConfig.ByParent -> {
                    val name =
                        api.userLibraryApi
                            .getItem(itemId = config.parentId)
                            .content.name ?: ""
                    HomeRowConfigDisplay(
                        name,
                        config,
                    )
                }

                is HomeRowConfig.ContinueWatching -> {
                    HomeRowConfigDisplay(
                        context.getString(R.string.continue_watching),
                        config,
                    )
                }

                is HomeRowConfig.ContinueWatchingCombined -> {
                    HomeRowConfigDisplay(
                        context.getString(R.string.combine_continue_next),
                        config,
                    )
                }

                is HomeRowConfig.Genres -> {
                    val name =
                        api.userLibraryApi
                            .getItem(itemId = config.parentId)
                            .content.name ?: ""
                    HomeRowConfigDisplay(
                        context.getString(R.string.genres_in, name),
                        config,
                    )
                }

                is HomeRowConfig.GetItems -> {
                    HomeRowConfigDisplay(config.name, config)
                }

                is HomeRowConfig.NextUp -> {
                    HomeRowConfigDisplay(
                        context.getString(R.string.next_up),
                        config,
                    )
                }

                is HomeRowConfig.RecentlyAdded -> {
                    val name =
                        api.userLibraryApi
                            .getItem(itemId = config.parentId)
                            .content.name ?: ""
                    HomeRowConfigDisplay(
                        context.getString(R.string.recently_added_in, name),
                        config,
                    )
                }

                is HomeRowConfig.RecentlyReleased -> {
                    val name =
                        api.userLibraryApi
                            .getItem(itemId = config.parentId)
                            .content.name ?: ""
                    HomeRowConfigDisplay(
                        context.getString(R.string.recently_released_in, name),
                        config,
                    )
                }
            }

        suspend fun fetchDataForRow(
            row: HomeRowConfig,
            scope: CoroutineScope,
            prefs: HomePagePreferences,
            userDto: UserDto,
            libraries: List<Library>,
            limit: Int = prefs.maxItemsPerRow,
        ): HomeRowLoadingState =
            when (row) {
                is HomeRowConfig.ContinueWatching -> {
                    val resume = latestNextUpService.getResume(userDto.id, limit, true)

                    HomeRowLoadingState.Success(
                        title = context.getString(R.string.continue_watching),
                        items = resume,
                        viewOptions = row.viewOptions,
                    )
                }

                is HomeRowConfig.NextUp -> {
                    val nextUp =
                        latestNextUpService.getNextUp(
                            userDto.id,
                            limit,
                            prefs.enableRewatchingNextUp,
                            false,
                        )

                    HomeRowLoadingState.Success(
                        title = context.getString(R.string.next_up),
                        items = nextUp,
                        viewOptions = row.viewOptions,
                    )
                }

                is HomeRowConfig.ContinueWatchingCombined -> {
                    val resume =
                        latestNextUpService.getResume(userDto.id, limit, true)
                    val nextUp =
                        latestNextUpService.getNextUp(
                            userDto.id,
                            limit,
                            prefs.enableRewatchingNextUp,
                            false,
                        )

                    HomeRowLoadingState.Success(
                        title = context.getString(R.string.continue_watching),
                        items =
                            latestNextUpService.buildCombined(
                                resume,
                                nextUp,
                            ),
                        viewOptions = row.viewOptions,
                    )
                }

                is HomeRowConfig.Genres -> {
                    val request =
                        GetGenresRequest(
                            parentId = row.parentId,
                            userId = userDto.id,
                            limit = limit,
                        )
                    val items =
                        GetGenresRequestHandler
                            .execute(api, request)
                            .content.items
                    val genreIds = items.map { it.id }
                    val genreImages =
                        getGenreImageMap(
                            api = api,
                            scope = scope,
                            imageUrlService = imageUrlService,
                            genres = genreIds,
                            parentId = row.parentId,
                            includeItemTypes = null,
                            cardWidthPx = null,
                        )
                    val genres =
                        items.map {
                            BaseItem(it, false, genreImages[it.id])
                        }

                    val name =
                        libraries
                            .firstOrNull { it.itemId == row.parentId }
                            ?.name
                    val title =
                        name?.let { context.getString(R.string.genres_in, it) }
                            ?: context.getString(R.string.genres)

                    HomeRowLoadingState.Success(
                        title,
                        genres,
                        viewOptions = row.viewOptions,
                    )
                }

                is HomeRowConfig.RecentlyAdded -> {
                    val name =
                        libraries
                            .firstOrNull { it.itemId == row.parentId }
                            ?.name
                    val title =
                        name?.let { context.getString(R.string.recently_added_in, it) }
                            ?: context.getString(R.string.recently_added)
                    val request =
                        GetLatestMediaRequest(
                            fields = SlimItemFields,
                            imageTypeLimit = 1,
                            parentId = row.parentId,
                            groupItems = true,
                            limit = limit,
                            isPlayed = null, // Server will handle user's preference
                        )
                    val latest =
                        api.userLibraryApi
                            .getLatestMedia(request)
                            .content
                            .map { BaseItem.Companion.from(it, api, true) }
                            .let {
                                HomeRowLoadingState.Success(
                                    title,
                                    it,
                                    row.viewOptions,
                                )
                            }
                    latest
                }

                is HomeRowConfig.RecentlyReleased -> {
                    val name =
                        libraries
                            .firstOrNull { it.itemId == row.parentId }
                            ?.name
                    val title =
                        name?.let {
                            context.getString(R.string.recently_released_in, it)
                        } ?: context.getString(R.string.recently_released)
                    val request =
                        GetItemsRequest(
                            parentId = row.parentId,
                            limit = limit,
                            sortBy = listOf(ItemSortBy.PREMIERE_DATE),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            fields = DefaultItemFields,
                            recursive = true,
                        )
                    GetItemsRequestHandler
                        .execute(api, request)
                        .content.items
                        .map { BaseItem.Companion.from(it, api, true) }
                        .let {
                            HomeRowLoadingState.Success(
                                title,
                                it,
                                row.viewOptions,
                            )
                        }
                }

                is HomeRowConfig.ByParent -> {
                    val request =
                        GetItemsRequest(
                            userId = userDto.id,
                            parentId = row.parentId,
                            recursive = row.recursive,
                            sortBy = row.sort?.let { listOf(it.sort) },
                            sortOrder = row.sort?.let { listOf(it.direction) },
                            limit = limit,
                            fields = DefaultItemFields,
                        )
                    val name =
                        api.userLibraryApi
                            .getItem(itemId = row.parentId)
                            .content.name
                    GetItemsRequestHandler
                        .execute(api, request)
                        .content.items
                        .map { BaseItem(it, true) }
                        .let {
                            HomeRowLoadingState.Success(
                                name ?: context.getString(R.string.collection),
                                it,
                                row.viewOptions,
                            )
                        }
                }

                is HomeRowConfig.GetItems -> {
                    val request =
                        row.getItems.let {
                            if (it.limit == null) {
                                it.copy(
                                    userId = userDto.id,
                                    limit = limit,
                                )
                            } else {
                                it.copy(
                                    userId = userDto.id,
                                )
                            }
                        }
                    val name =
                        if (row.name == null && request.parentId != null) {
                            // If a name was not provided, use the parent's name if available
                            api.userLibraryApi
                                .getItem(itemId = request.parentId!!)
                                .content.name
                        } else {
                            row.name
                        }
                    GetItemsRequestHandler
                        .execute(api, request)
                        .content.items
                        .map { BaseItem.Companion.from(it, api, true) }
                        .let {
                            HomeRowLoadingState.Success(
                                name ?: context.getString(R.string.collection),
                                it,
                                row.viewOptions,
                            )
                        }
                }
            }

        companion object {
            const val DISPLAY_PREF_ID = "default"
            const val CUSTOM_PREF_ID = "home_settings"
        }
    }

data class HomeRowConfigDisplay(
    val title: String,
    val config: HomeRowConfig,
)

data class HomePageResolvedSettings(
    val rows: List<HomeRowConfigDisplay>,
) {
    companion object {
        val EMPTY = HomePageResolvedSettings(listOf())
    }
}
