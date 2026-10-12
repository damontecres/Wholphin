package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.annotation.StringRes
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.JellyfinServerDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.HomePageSettings
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.data.model.SUPPORTED_HOME_PAGE_SETTINGS_VERSION
import com.github.damontecres.wholphin.preferences.DefaultUserConfiguration
import com.github.damontecres.wholphin.ui.AspectRatio
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.formatTypeName
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.ui.util.ResArgStringProvider
import com.github.damontecres.wholphin.ui.util.ResProviderStringProvider
import com.github.damontecres.wholphin.ui.util.ResStringProvider
import com.github.damontecres.wholphin.ui.util.StringProvider
import com.github.damontecres.wholphin.ui.util.StringStringProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.CollectionType
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Handles getting home page settings and data
 */
@Singleton
class HomeSettingsService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val navDrawerService: NavDrawerService,
        private val displayPreferencesService: DisplayPreferencesService,
        private val serverPluginApi: ServerPluginApi,
        private val serverDao: JellyfinServerDao,
    ) {
        /**
         * The current home page settings
         */
        val currentSettings = MutableStateFlow(HomePageResolvedSettings.EMPTY)

        /**
         * Saves a [HomePageSettings] to the server for the user under the display preference ID
         *
         * @see loadFromServer
         */
        suspend fun saveToServer(
            userId: UUID,
            settings: HomePageSettings,
            displayPreferencesId: String = DisplayPreferencesService.DEFAULT_DISPLAY_PREF_ID,
        ) {
            displayPreferencesService.updateDisplayPreferences(userId, displayPreferencesId) {
                put(CUSTOM_PREF_ID, jsonParser.encodeToString(settings))
            }
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
            displayPreferencesId: String = DisplayPreferencesService.DEFAULT_DISPLAY_PREF_ID,
        ): HomePageSettings? =
            displayPreferencesService
                .getDisplayPreferences(userId, displayPreferencesId)
                .customPrefs[CUSTOM_PREF_ID]
                ?.let {
                    val jsonElement = jsonParser.parseToJsonElement(it)
                    decode(jsonElement)
                }

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

        private suspend fun tryLoad(block: suspend () -> HomePageSettings?): HomePageSettings? =
            try {
                block.invoke()
            } catch (ex: Exception) {
                Timber.w(ex, "Error loading settings")
                null
            }

        suspend fun fetchSettingsFor(
            userId: UUID,
            source: HomePageSettingsSource,
        ): HomePageResolvedSettings? {
            val (resolvedSource, settings) =
                when (source) {
                    HomePageSettingsSource.UNSET -> loadInOrder(userId)
                    HomePageSettingsSource.LOCAL -> source to loadFromLocal(userId)
                    HomePageSettingsSource.SERVER_PROFILE -> source to loadFromServer(userId)
                    HomePageSettingsSource.PLUGIN -> source to serverPluginApi.fetchHomePageSettings()
                    HomePageSettingsSource.DEFAULT -> source to createDefault(userId).asHomePageSettings()
                    HomePageSettingsSource.WEB_CONFIG -> source to parseFromWebConfig(userId)?.asHomePageSettings()
                }
            Timber.i(
                "fetch home settings: resolvedSource=%s, userId=%s, found=%s",
                resolvedSource,
                userId,
                settings != null,
            )
            return settings?.let {
                val resolvedRows =
                    settings.rows.mapIndexed { index, config ->
                        resolve(index, config)
                    }
                HomePageResolvedSettings(userId, resolvedSource, resolvedRows)
            }
        }

        /**
         * Loads [HomePageSettings] into [currentSettings]
         *
         * First checks locally, then on the server, and finally creates a default if needed
         *
         * Does not persist either the server nor default
         */
        suspend fun loadCurrentSettings(user: JellyfinUser) {
            currentSettings.update { HomePageResolvedSettings.EMPTY }

            Timber.v("Getting setting for %s", user.id)
            val settings =
                try {
                    fetchSettingsFor(user.id, user.config.homeSettingsSource)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error loading settings for %s", user.config.homeSettingsSource)
                    null
                }
            Timber.v("Found settings: %s", settings != null)
            val resolvedSettings = settings ?: createDefault(user.id)
            currentSettings.update { resolvedSettings }
        }

        /**
         * Tries to load settings from local->server->plugin
         */
        private suspend fun loadInOrder(userId: UUID): Pair<HomePageSettingsSource, HomePageSettings?> {
            Timber.v("loadInOrder: local")
            var settings =
                HomePageSettingsSource.LOCAL to
                    tryLoad {
                        loadFromLocal(userId)
                    }

            if (settings.second == null) {
                Timber.v("loadInOrder: server")
                settings =
                    HomePageSettingsSource.SERVER_PROFILE to
                    tryLoad {
                        loadFromServer(userId)
                    }
            }

            if (settings.second == null && serverRepository.serverPluginInstalled.value) {
                Timber.v("loadInOrder: plugin")
                settings = HomePageSettingsSource.PLUGIN to
                    tryLoad {
                        serverPluginApi.fetchHomePageSettings()
                    }
            }
            return settings
        }

        /**
         * Resolve the settings and set them to be the current settings
         */
        suspend fun updateCurrent(
            source: HomePageSettingsSource,
            settings: HomePageSettings,
        ) {
            val resolvedRows =
                settings.rows.mapIndexed { index, config ->
                    resolve(index, config)
                }
            currentSettings.update { it.copy(rows = resolvedRows) }
            serverRepository.currentUser?.let { user ->
                val toSave = user.updateConfig { it.copy(homeSettingsSource = source) }
                serverDao.updateUser(toSave)
            }
        }

        /**
         * Create a default [HomePageResolvedSettings] using the available libraries
         */
        suspend fun createDefault(userId: UUID): HomePageResolvedSettings {
            Timber.v("Creating default settings")
            val user = serverRepository.currentUser?.takeIf { it.id == userId }
            val userDto = serverRepository.currentUserDto?.takeIf { it.id == userId }
            val libraries =
                if (user != null) {
                    navDrawerService.getFilteredUserLibraries(user, userDto?.tvAccess ?: false)
                } else {
                    navDrawerService.getAllUserLibraries(userId, userDto?.tvAccess ?: false)
                }

            val includedIds =
                libraries
                    .mapIndexed { index, library ->
                        val parentId = library.itemId
                        val title = getRecentlyAddedTitle(library.name)
                        if (library.collectionType == CollectionType.LIVETV) {
                            HomeRowConfigDisplay(
                                id = index,
                                title = ResStringProvider(R.string.watch_live),
                                config = HomeRowConfig.TvPrograms(),
                            )
                        } else {
                            val viewOptions = viewOptionsForCollectionType(library.collectionType)
                            HomeRowConfigDisplay(
                                id = index,
                                title = title,
                                config =
                                    HomeRowConfig.RecentlyAdded(
                                        parentId = parentId,
                                        viewOptions = viewOptions,
                                    ),
                            )
                        }
                    }
            val continueWatchingRow =
                listOf(
                    HomeRowConfigDisplay(
                        id = includedIds.size + 1,
                        title = ResStringProvider(R.string.combine_continue_next),
                        config = HomeRowConfig.ContinueWatchingCombined(),
                    ),
                )
            val rowConfig = continueWatchingRow + includedIds
            return HomePageResolvedSettings(userId, HomePageSettingsSource.DEFAULT, rowConfig)
        }

        /**
         * Create home page settings from the user's web UI home page settings
         */
        suspend fun parseFromWebConfig(userId: UUID): HomePageResolvedSettings? {
            val customPrefs =
                displayPreferencesService
                    .getDisplayPreferences(
                        displayPreferencesId = "usersettings",
                        userId = userId,
                        client = "emby",
                    ).customPrefs
            val userDto by api.userApi.getUserById(userId)
            val config = userDto.configuration ?: DefaultUserConfiguration
            val libraries =
                navDrawerService
                    .getAllUserLibraries(userId, userDto.tvAccess)
                    .filterNot {
                        it.itemId in config.latestItemsExcludes
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
                                            title = ResStringProvider(R.string.active_recordings),
                                            config = HomeRowConfig.Recordings(),
                                        )
                                    }

                                    HomeSectionType.RESUME -> {
                                        HomeRowConfigDisplay(
                                            id = id++,
                                            title = ResStringProvider(R.string.continue_watching),
                                            config = HomeRowConfig.ContinueWatching(),
                                        )
                                    }

                                    HomeSectionType.NEXT_UP -> {
                                        HomeRowConfigDisplay(
                                            id = id++,
                                            title = ResStringProvider(R.string.next_up),
                                            config = HomeRowConfig.NextUp(),
                                        )
                                    }

                                    HomeSectionType.LIVE_TV -> {
                                        if (userDto.tvAccess) {
                                            HomeRowConfigDisplay(
                                                id = id++,
                                                title = ResStringProvider(R.string.watch_live),
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
                                libraries.map { library ->
                                    val viewOptions =
                                        viewOptionsForCollectionType(library.collectionType)
                                    HomeRowConfigDisplay(
                                        id = id++,
                                        title =
                                            ResArgStringProvider(
                                                R.string.recently_added_in,
                                                library.name ?: "",
                                            ),
                                        config =
                                            HomeRowConfig.RecentlyAdded(
                                                parentId = library.itemId,
                                                viewOptions = viewOptions,
                                            ),
                                    )
                                }
                            } else if (config != null) {
                                listOf(config)
                            } else {
                                null
                            }
                        }.flatten()
                HomePageResolvedSettings(userId, HomePageSettingsSource.WEB_CONFIG, rowConfigs)
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
                    val name = getItemName(null, config.parentId)
                    HomeRowConfigDisplay(
                        id,
                        name,
                        config,
                    )
                }

                is HomeRowConfig.ContinueWatching -> {
                    HomeRowConfigDisplay(
                        id,
                        ResStringProvider(R.string.continue_watching),
                        config,
                    )
                }

                is HomeRowConfig.ContinueWatchingCombined -> {
                    HomeRowConfigDisplay(
                        id,
                        ResStringProvider(R.string.combine_continue_next),
                        config,
                    )
                }

                is HomeRowConfig.Genres -> {
                    val title = getItemName(R.string.genres_in, config.parentId)
                    HomeRowConfigDisplay(
                        id,
                        title,
                        config,
                    )
                }

                is HomeRowConfig.Studios -> {
                    val title = getItemName(R.string.studios_in, config.parentId)
                    HomeRowConfigDisplay(
                        id,
                        title,
                        config,
                    )
                }

                is HomeRowConfig.GetItems -> {
                    HomeRowConfigDisplay(id, StringStringProvider(config.name), config)
                }

                is HomeRowConfig.NextUp -> {
                    HomeRowConfigDisplay(
                        id,
                        ResStringProvider(R.string.next_up),
                        config,
                    )
                }

                is HomeRowConfig.RecentlyAdded -> {
                    val title = getItemName(R.string.recently_added_in, config.parentId)
                    HomeRowConfigDisplay(
                        id,
                        title,
                        config,
                    )
                }

                is HomeRowConfig.RecentlyReleased -> {
                    val title = getItemName(R.string.recently_released_in, config.parentId)
                    HomeRowConfigDisplay(
                        id,
                        title,
                        config,
                    )
                }

                is HomeRowConfig.Favorite -> {
                    val name =
                        ResProviderStringProvider(
                            R.string.favorite_items_title,
                            ResStringProvider(formatTypeName(config.kind)),
                        )
                    HomeRowConfigDisplay(id, name, config)
                }

                is HomeRowConfig.Recordings -> {
                    HomeRowConfigDisplay(
                        id = id,
                        title = ResStringProvider(R.string.active_recordings),
                        config,
                    )
                }

                is HomeRowConfig.TvPrograms -> {
                    HomeRowConfigDisplay(
                        id = id,
                        title = ResStringProvider(R.string.watch_live),
                        config,
                    )
                }

                is HomeRowConfig.TvChannels -> {
                    HomeRowConfigDisplay(
                        id = id,
                        title = ResStringProvider(R.string.channels),
                        config,
                    )
                }

                is HomeRowConfig.Suggestions -> {
                    val title = getItemName(R.string.suggestions_for, config.parentId)
                    HomeRowConfigDisplay(
                        id = id,
                        title = title,
                        config,
                    )
                }
            }

        private suspend fun getItemName(
            @StringRes stringRes: Int?,
            itemId: UUID,
            default: StringProvider = StringStringProvider(""),
        ): StringProvider =
            try {
                api.userLibraryApi
                    .getItem(
                        userId = serverRepository.currentUser?.id,
                        itemId = itemId,
                    ).content.name
                    ?.let {
                        if (stringRes == null) {
                            StringStringProvider(it)
                        } else {
                            ResArgStringProvider(stringRes, it)
                        }
                    } ?: default
            } catch (ex: Exception) {
                Timber.e(ex, "Could not get name for %s", itemId)
                ResStringProvider(R.string.unknown)
            }

        companion object {
            const val CUSTOM_PREF_ID = "home_settings"

            @OptIn(ExperimentalSerializationApi::class)
            val jsonParser =
                Json {
                    isLenient = true
                    ignoreUnknownKeys = true
                    allowTrailingComma = true
                }
        }
    }

/**
 * A [HomeRowConfig] with a resolved ID and title so it is usable in the UI
 */
data class HomeRowConfigDisplay(
    val id: Int,
    val title: StringProvider,
    val config: HomeRowConfig,
)

/**
 * List of resolved [HomeRowConfig]s as [HomeRowConfigDisplay]s
 *
 * @see HomePageSettings
 */
data class HomePageResolvedSettings(
    val userId: UUID,
    val source: HomePageSettingsSource,
    val rows: List<HomeRowConfigDisplay>,
) {
    fun asHomePageSettings(): HomePageSettings = HomePageSettings(rows.map { it.config }, SUPPORTED_HOME_PAGE_SETTINGS_VERSION)

    companion object {
        val EMPTY =
            HomePageResolvedSettings(UUID.randomUUID(), HomePageSettingsSource.UNSET, emptyList())
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

fun getRecentlyAddedTitle(name: String?): StringProvider =
    name?.let { ResArgStringProvider(R.string.recently_added_in, it) }
        ?: ResStringProvider(R.string.recently_added)

fun viewOptionsForCollectionType(collectionType: CollectionType?): HomeRowViewOptions =
    when (collectionType) {
        CollectionType.MUSIC,
        -> {
            HomeRowViewOptions(
                heightDp = Cards.HEIGHT_EPISODE,
                aspectRatio = AspectRatio.SQUARE,
            )
        }

        CollectionType.PHOTOS,
        CollectionType.HOMEVIDEOS,
        CollectionType.MUSICVIDEOS,
        CollectionType.TRAILERS,
        -> {
            HomeRowViewOptions(
                heightDp = Cards.HEIGHT_EPISODE,
                aspectRatio = AspectRatio.WIDE,
            )
        }

        CollectionType.LIVETV,
        -> {
            HomeRowViewOptions.liveTvDefault
        }

        CollectionType.MOVIES,
        CollectionType.TVSHOWS,
        CollectionType.BOXSETS,
        CollectionType.BOOKS,
        CollectionType.PLAYLISTS,
        CollectionType.FOLDERS,
        CollectionType.UNKNOWN,
        null,
        -> {
            HomeRowViewOptions()
        }
    }

enum class HomePageSettingsSource(
    @param:StringRes val stringResId: Int,
) {
    UNSET(R.string.unknown),
    LOCAL(R.string.home_settings_source_local),
    SERVER_PROFILE(R.string.home_settings_source_server_profile),
    PLUGIN(R.string.home_settings_source_plugin),
    DEFAULT(R.string.home_settings_source_unset),
    WEB_CONFIG(R.string.home_settings_source_web),
}
