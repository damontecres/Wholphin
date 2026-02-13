package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomePageSettings
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.SUPPORTED_HOME_PAGE_SETTINGS_VERSION
import com.github.damontecres.wholphin.preferences.DefaultUserConfiguration
import com.github.damontecres.wholphin.preferences.HomePagePreferences
import com.github.damontecres.wholphin.ui.DefaultItemFields
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.components.getGenreImageMap
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.main.settings.Library
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.util.GetGenresRequestHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.GetPersonsHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.HomeRowLoadingState.Error
import com.github.damontecres.wholphin.util.HomeRowLoadingState.Success
import com.github.damontecres.wholphin.util.supportedHomeCollectionTypes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.displayPreferencesApi
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.request.GetGenresRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.api.request.GetPersonsRequest
import org.jellyfin.sdk.model.api.request.GetRecommendedProgramsRequest
import org.jellyfin.sdk.model.api.request.GetRecordingsRequest
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
        private val serverRepository: ServerRepository,
        private val userPreferencesService: UserPreferencesService,
        private val navDrawerService: NavDrawerService,
        private val latestNextUpService: LatestNextUpService,
        private val imageUrlService: ImageUrlService,
        private val suggestionService: SuggestionService,
    ) {
        @OptIn(ExperimentalSerializationApi::class)
        val jsonParser =
            Json {
                isLenient = true
                ignoreUnknownKeys = true
                allowTrailingComma = true
            }

        val currentSettings = MutableStateFlow(HomePageResolvedSettings.EMPTY)

        /**
         * Saves a [HomePageSettings] to the server for the user under the display preference ID
         *
         * @see loadFromServer
         */
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

        /**
         * Reads a [HomePageSettings] from the server for the user and display preference ID
         *
         * Returns null if there is none saved
         *
         * @see saveToServer
         */
        suspend fun loadFromServer(
            userId: UUID,
            displayPreferencesId: String = DISPLAY_PREF_ID,
        ): HomePageSettings? {
            val current = getDisplayPreferences(userId, displayPreferencesId)
            return current.customPrefs[CUSTOM_PREF_ID]?.let {
                val jsonElement = jsonParser.parseToJsonElement(it)
                decode(jsonElement)
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

        /**
         * Computes the filename for locally saved [HomePageSettings]
         */
        private fun filename(userId: UUID) = "${CUSTOM_PREF_ID}_${userId.toServerString()}.json"

        /**
         * Save the [HomePageSettings] for the user locally on the device
         *
         * @see loadFromLocal
         */
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

        /**
         * Reads [HomePageSettings] for the user if it exists
         *
         * @see saveToLocal
         */
        @OptIn(ExperimentalSerializationApi::class)
        suspend fun loadFromLocal(userId: UUID): HomePageSettings? {
            val dir = File(context.filesDir, CUSTOM_PREF_ID)
            val file = File(dir, filename(userId))
            return if (file.exists()) {
                val fileContents = file.readText()
                val jsonElement = jsonParser.parseToJsonElement(fileContents)
                decode(jsonElement)
            } else {
                null
            }
        }

        /**
         * Decodes [HomePageSettings] from a [JsonElement] skipping any unknown/unparsable rows
         *
         * This is public only for testing
         */
        fun decode(element: JsonElement): HomePageSettings {
            val version = element.jsonObject["version"]?.jsonPrimitive?.intOrNull
            if (version == null || version > SUPPORTED_HOME_PAGE_SETTINGS_VERSION) {
                throw UnsupportedHomeSettingsVersionException(version)
            }
            val rowsElement = element.jsonObject["rows"]?.jsonArray
            val rows =
                rowsElement
                    ?.mapNotNull { row ->
                        try {
                            jsonParser.decodeFromJsonElement<HomeRowConfig>(row)
                        } catch (ex: Exception) {
                            Timber.w(ex, "Unknown row %s", row)
                            // TODO maybe use placeholder instead of null?
                            null
                        }
                    }.orEmpty()
            return HomePageSettings(rows, version)
        }

        /**
         * Loads [HomePageSettings] into [currentSettings]
         *
         * First checks locally, then on the server, and finally creates a default if needed
         *
         * Does not persist either the server nor default
         */
        suspend fun loadCurrentSettings(userId: UUID) {
            Timber.v("Getting setting for %s", userId)
            // User local then server/remote otherwise create a default
            val settings =
                try {
                    val local = loadFromLocal(userId)
                    Timber.v("Found local? %s", local != null)
                    local
                } catch (ex: Exception) {
                    Timber.w(ex, "Error loading local settings")
                    // TODO show toast?
                    null
                } ?: try {
                    val remote = loadFromServer(userId)
                    Timber.v("Found remote? %s", remote != null)
                    remote
                } catch (ex: Exception) {
                    Timber.w(ex, "Error loading remote settings")
                    null
                }
            val resolvedSettings =
                if (settings != null) {
                    Timber.v("Found settings")
                    // Resolve
                    val resolvedRows =
                        settings.rows.mapIndexed { index, config ->
                            resolve(index, config)
                        }
                    HomePageResolvedSettings(resolvedRows)
                } else {
                    createDefault(userId)
                }

            currentSettings.update { resolvedSettings }
        }

        suspend fun updateCurrent(settings: HomePageSettings) {
            val resolvedRows =
                settings.rows.mapIndexed { index, config ->
                    resolve(index, config)
                }
            val resolvedSettings = HomePageResolvedSettings(resolvedRows)
            currentSettings.update { resolvedSettings }
        }

        /**
         * Create a default [HomePageResolvedSettings] using the available libraries
         */
        suspend fun createDefault(userId: UUID): HomePageResolvedSettings {
            Timber.v("Creating default settings")
            val user = serverRepository.currentUser.value?.takeIf { it.id == userId }
            val userDto = serverRepository.currentUserDto.value?.takeIf { it.id == userId }
            val libraries =
                if (user != null) {
                    navDrawerService.getFilteredUserLibraries(user, userDto?.tvAccess ?: false)
                } else {
                    navDrawerService.getAllUserLibraries(userId, userDto?.tvAccess ?: false)
                }

            val prefs =
                userPreferencesService.getCurrent().appPreferences.homePagePreferences

            val includedIds =
                libraries
                    .mapIndexed { index, it ->
                        val parentId = it.itemId
                        val name = it.name.takeIf { it.isNotNullOrBlank() }
                        val title =
                            name?.let { context.getString(R.string.recently_added_in, it) }
                                ?: context.getString(R.string.recently_added)
                        HomeRowConfigDisplay(
                            id = index,
                            title = title,
                            config = HomeRowConfig.RecentlyAdded(parentId),
                        )
                    }
            val continueWatchingRows =
                if (prefs.combineContinueNext) { // TODO
                    listOf(
                        HomeRowConfigDisplay(
                            id = includedIds.size + 1,
                            title = context.getString(R.string.combine_continue_next),
                            config = HomeRowConfig.ContinueWatchingCombined(),
                        ),
                    )
                } else {
                    listOf(
                        HomeRowConfigDisplay(
                            id = includedIds.size + 1,
                            title = context.getString(R.string.continue_watching),
                            config = HomeRowConfig.ContinueWatching(),
                        ),
                        HomeRowConfigDisplay(
                            id = includedIds.size + 2,
                            title = context.getString(R.string.next_up),
                            config = HomeRowConfig.NextUp(),
                        ),
                    )
                }
            val rowConfig = continueWatchingRows + includedIds
            return HomePageResolvedSettings(rowConfig)
        }

        suspend fun parseFromWebConfig(userId: UUID): HomePageResolvedSettings? {
            val customPrefs =
                api.displayPreferencesApi
                    .getDisplayPreferences(
                        displayPreferencesId = "usersettings",
                        userId = userId,
                        client = "emby",
                    ).content.customPrefs
            val userDto by api.userApi.getUserById(userId)
            val config = userDto.configuration ?: DefaultUserConfiguration
            val libraries =
                api.userViewsApi
                    .getUserViews(userId = userId)
                    .content.items
                    .filter {
                        it.collectionType in supportedHomeCollectionTypes &&
                            it.id !in config.latestItemsExcludes
                    }

            return if (customPrefs.isNotEmpty()) {
                var id = 0
                val rowConfigs =
                    (0..9)
                        .mapNotNull { idx ->
                            val sectionType =
                                HomeSectionType.fromString(customPrefs["homesection$idx"]?.lowercase())
                            Timber.v(
                                "sectionType=$sectionType, %s",
                                customPrefs["homesection$idx"]?.lowercase(),
                            )
                            val config =
                                when (sectionType) {
                                    HomeSectionType.ACTIVE_RECORDINGS -> {
                                        HomeRowConfigDisplay(
                                            id = id++,
                                            title = context.getString(R.string.active_recordings),
                                            config = HomeRowConfig.Recordings(),
                                        )
                                    }

                                    HomeSectionType.RESUME -> {
                                        HomeRowConfigDisplay(
                                            id = id++,
                                            title = context.getString(R.string.continue_watching),
                                            config = HomeRowConfig.ContinueWatching(),
                                        )
                                    }

                                    HomeSectionType.NEXT_UP -> {
                                        HomeRowConfigDisplay(
                                            id = id++,
                                            title = context.getString(R.string.next_up),
                                            config = HomeRowConfig.NextUp(),
                                        )
                                    }

                                    HomeSectionType.LIVE_TV -> {
                                        if (userDto.policy?.enableLiveTvAccess == true) {
                                            HomeRowConfigDisplay(
                                                id = id++,
                                                title = context.getString(R.string.live_tv),
                                                config = HomeRowConfig.TvPrograms(),
                                            )
                                        } else {
                                            null
                                        }
                                    }

                                    HomeSectionType.LATEST_MEDIA -> {
                                        // Handled below
                                        null
                                    }

                                    // Unsupported
                                    HomeSectionType.RESUME_AUDIO,
                                    HomeSectionType.RESUME_BOOK,
                                    -> {
                                        null
                                    }

                                    HomeSectionType.SMALL_LIBRARY_TILES,
                                    HomeSectionType.LIBRARY_BUTTONS,
                                    HomeSectionType.NONE,
                                    null,
                                    -> {
                                        null
                                    }
                                }
                            if (sectionType == HomeSectionType.LATEST_MEDIA) {
                                libraries.map {
                                    HomeRowConfigDisplay(
                                        id = id++,
                                        title =
                                            context.getString(
                                                R.string.recently_added_in,
                                                it.name ?: "",
                                            ),
                                        config = HomeRowConfig.RecentlyAdded(it.id),
                                    )
                                }
                            } else if (config != null) {
                                listOf(config)
                            } else {
                                null
                            }
                        }.flatten()
                HomePageResolvedSettings(rowConfigs)
            } else {
                null
            }
        }

        /**
         * Converts a [HomeRowConfig] into [HomeRowConfigDisplay] for UI purposes
         */
        suspend fun resolve(
            id: Int,
            config: HomeRowConfig,
        ): HomeRowConfigDisplay =
            when (config) {
                is HomeRowConfig.ByParent -> {
                    val name =
                        api.userLibraryApi
                            .getItem(itemId = config.parentId)
                            .content.name ?: ""
                    HomeRowConfigDisplay(
                        id,
                        name,
                        config,
                    )
                }

                is HomeRowConfig.ContinueWatching -> {
                    HomeRowConfigDisplay(
                        id,
                        context.getString(R.string.continue_watching),
                        config,
                    )
                }

                is HomeRowConfig.ContinueWatchingCombined -> {
                    HomeRowConfigDisplay(
                        id,
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
                        id,
                        context.getString(R.string.genres_in, name),
                        config,
                    )
                }

                is HomeRowConfig.GetItems -> {
                    HomeRowConfigDisplay(id, config.name, config)
                }

                is HomeRowConfig.NextUp -> {
                    HomeRowConfigDisplay(
                        id,
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
                        id,
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
                        id,
                        context.getString(R.string.recently_released_in, name),
                        config,
                    )
                }

                is HomeRowConfig.Favorite -> {
                    val name = context.getString(R.string.favorites) // TODO "Favorite <type>"
                    HomeRowConfigDisplay(id, name, config)
                }

                is HomeRowConfig.Recordings -> {
                    HomeRowConfigDisplay(
                        id = id,
                        title = context.getString(R.string.active_recordings),
                        config,
                    )
                }

                is HomeRowConfig.TvPrograms -> {
                    HomeRowConfigDisplay(
                        id = id,
                        title = context.getString(R.string.live_tv),
                        config,
                    )
                }

                is HomeRowConfig.TvChannels -> {
                    HomeRowConfigDisplay(
                        id = id,
                        title = context.getString(R.string.channels),
                        config,
                    )
                }

                is HomeRowConfig.Suggestions -> {
                    val name =
                        api.userLibraryApi
                            .getItem(itemId = config.parentId)
                            .content.name ?: ""
                    HomeRowConfigDisplay(
                        id = id,
                        title = context.getString(R.string.suggestions_for, name),
                        config,
                    )
                }
            }

        /**
         * Fetch the data from the server for a given [HomeRowConfig]
         */
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
                    val resume =
                        latestNextUpService.getResume(
                            userDto.id,
                            limit,
                            true,
                            row.viewOptions.useSeries,
                        )

                    Success(
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
                            prefs.maxDaysNextUp,
                            row.viewOptions.useSeries,
                        )

                    Success(
                        title = context.getString(R.string.next_up),
                        items = nextUp,
                        viewOptions = row.viewOptions,
                    )
                }

                is HomeRowConfig.ContinueWatchingCombined -> {
                    val resume =
                        latestNextUpService.getResume(
                            userDto.id,
                            limit,
                            true,
                            row.viewOptions.useSeries,
                        )
                    val nextUp =
                        latestNextUpService.getNextUp(
                            userDto.id,
                            limit,
                            prefs.enableRewatchingNextUp,
                            false,
                            prefs.maxDaysNextUp,
                            row.viewOptions.useSeries,
                        )

                    Success(
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

                    Success(
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
                            .map { BaseItem.Companion.from(it, api, row.viewOptions.useSeries) }
                            .let {
                                Success(
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
                        .map { BaseItem.Companion.from(it, api, row.viewOptions.useSeries) }
                        .let {
                            Success(
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
                        .map { BaseItem(it, row.viewOptions.useSeries) }
                        .let {
                            Success(
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
                    GetItemsRequestHandler
                        .execute(api, request)
                        .content.items
                        .map { BaseItem(it, row.viewOptions.useSeries) }
                        .let {
                            Success(
                                row.name,
                                it,
                                row.viewOptions,
                            )
                        }
                }

                is HomeRowConfig.Favorite -> {
                    if (row.kind == BaseItemKind.PERSON) {
                        val request =
                            GetPersonsRequest(
                                userId = userDto.id,
                                limit = limit,
                                fields = DefaultItemFields,
                                isFavorite = true,
                                enableImages = true,
                                enableImageTypes = listOf(ImageType.PRIMARY),
                            )
                        GetPersonsHandler
                            .execute(api, request)
                            .content.items
                            .map { BaseItem(it, true) }
                            .let {
                                Success(
                                    context.getString(R.string.favorites), // TODO
                                    it,
                                    row.viewOptions,
                                )
                            }
                    } else {
                        val request =
                            GetItemsRequest(
                                userId = userDto.id,
                                recursive = true,
                                limit = limit,
                                fields = DefaultItemFields,
                                includeItemTypes = listOf(row.kind),
                                isFavorite = true,
                            )
                        GetItemsRequestHandler
                            .execute(api, request)
                            .content.items
                            .map { BaseItem(it, row.viewOptions.useSeries) }
                            .let {
                                Success(
                                    context.getString(R.string.favorites), // TODO
                                    it,
                                    row.viewOptions,
                                )
                            }
                    }
                }

                is HomeRowConfig.Recordings -> {
                    val request =
                        GetRecordingsRequest(
                            userId = userDto.id,
                            isInProgress = true,
                            fields = DefaultItemFields,
                            limit = limit,
                            enableImages = true,
                            enableUserData = true,
                        )
                    api.liveTvApi
                        .getRecordings(request)
                        .content.items
                        .map { BaseItem(it, row.viewOptions.useSeries) }
                        .let {
                            Success(
                                context.getString(R.string.active_recordings),
                                it,
                                row.viewOptions,
                            )
                        }
                }

                is HomeRowConfig.TvPrograms -> {
                    val request =
                        GetRecommendedProgramsRequest(
                            userId = userDto.id,
                            fields = DefaultItemFields,
                            limit = limit,
                            enableImages = true,
                            enableUserData = true,
                        )
                    api.liveTvApi
                        .getRecommendedPrograms(request)
                        .content.items
                        .map { BaseItem(it, row.viewOptions.useSeries) }
                        .let {
                            Success(
                                context.getString(R.string.live_tv),
                                it,
                                row.viewOptions,
                            )
                        }
                }

                is HomeRowConfig.TvChannels -> {
                    api.liveTvApi
                        .getLiveTvChannels(
                            userId = userDto.id,
                            fields = DefaultItemFields,
                            limit = limit,
                            enableImages = true,
                        ).toBaseItems(api, row.viewOptions.useSeries)
                        .let {
                            Success(
                                context.getString(R.string.channels),
                                it,
                                row.viewOptions,
                            )
                        }
                }

                is HomeRowConfig.Suggestions -> {
                    val library =
                        api.userLibraryApi
                            .getItem(itemId = row.parentId)
                            .content
                    val title = context.getString(R.string.suggestions_for, library.name ?: "")
                    val itemKind = SuggestionsWorker.getTypeForCollection(library.collectionType)
                    val suggestions =
                        itemKind?.let {
                            suggestionService
                                .getSuggestionsFlow(row.parentId, itemKind)
                                .firstOrNull()
                        }
                    if (suggestions != null && suggestions is SuggestionsResource.Success) {
                        Success(
                            title,
                            suggestions.items,
                            row.viewOptions,
                        )
                    } else if (suggestions is SuggestionsResource.Empty) {
                        Success(
                            title,
                            listOf(),
                            row.viewOptions,
                        )
                    } else {
                        Error(
                            title,
                            message = "Unsupported type ${library.collectionType}",
                        )
                    }
                }
            }

        companion object {
            const val DISPLAY_PREF_ID = "default"
            const val CUSTOM_PREF_ID = "home_settings"
        }
    }

/**
 * A [HomeRowConfig] with a resolved ID and title so it is usable in the UI
 */
data class HomeRowConfigDisplay(
    val id: Int,
    val title: String,
    val config: HomeRowConfig,
)

/**
 * List of resolved [HomeRowConfig]s as [HomeRowConfigDisplay]s
 *
 * @see HomePageSettings
 */
data class HomePageResolvedSettings(
    val rows: List<HomeRowConfigDisplay>,
) {
    companion object {
        val EMPTY = HomePageResolvedSettings(listOf())
    }
}

// https://github.com/jellyfin/jellyfin/blob/v10.11.6/src/Jellyfin.Database/Jellyfin.Database.Implementations/Enums/HomeSectionType.cs
enum class HomeSectionType(
    val serialName: String,
) {
    NONE("none"),
    SMALL_LIBRARY_TILES("smalllibrarytitles"),
    LIBRARY_BUTTONS("librarybuttons"),
    ACTIVE_RECORDINGS("activerecordings"),
    RESUME("resume"),
    RESUME_AUDIO("resumeaudio"),
    LATEST_MEDIA("latestmedia"),
    NEXT_UP("nextup"),
    LIVE_TV("livetv"),
    RESUME_BOOK("resumebook"),
    ;

    companion object {
        fun fromString(homeKey: String?) = homeKey?.let { entries.firstOrNull { it.serialName == homeKey } }
    }
}

class UnsupportedHomeSettingsVersionException(
    val unsupportedVersion: Int?,
    val maxSupportedVersion: Int = SUPPORTED_HOME_PAGE_SETTINGS_VERSION,
) : Exception("Unsupported version $unsupportedVersion, max supported is $maxSupportedVersion")
