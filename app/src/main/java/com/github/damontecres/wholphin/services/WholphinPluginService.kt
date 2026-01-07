package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service to interact with Wholphin plugin API endpoints on the Jellyfin server
 * 
 * Provides both authenticated and unauthenticated access to plugin endpoints:
 * - Use [getLoginBackground] for unauthenticated endpoints (like login screen)
 * - Use [makeAuthenticatedRequest] for endpoints that require user authentication
 */
@Singleton
class WholphinPluginService
    @Inject
    constructor(
        @param:StandardOkHttpClient private val standardOkHttpClient: OkHttpClient,
        @param:AuthOkHttpClient private val authOkHttpClient: OkHttpClient,
    ) {
        companion object {
            private const val PLUGIN_BASE_PATH = "/wholphin"
            private const val CAPABILITIES_ENDPOINT = "$PLUGIN_BASE_PATH/capabilities"
            private const val LOGIN_BACKGROUND_ENDPOINT = "$PLUGIN_BASE_PATH/loginbackground"
            private const val HOME_ENDPOINT = "$PLUGIN_BASE_PATH/home"
            private const val SETTINGS_ENDPOINT = "$PLUGIN_BASE_PATH/settings"
            private const val NAV_DRAWER_ENDPOINT = "$PLUGIN_BASE_PATH/navdrawer"
        }

        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        /**
         * Check which features/capabilities the Wholphin plugin supports
         * 
         * This should be called first to determine what endpoints are available.
         * The plugin should implement a /wholphin/capabilities endpoint that returns
         * a JSON object describing supported features.
         *
         * @param serverUrl The base URL of the Jellyfin server
         * @return PluginCapabilities object describing available features, or null if plugin is not available
         */
        suspend fun getPluginCapabilities(serverUrl: String): PluginCapabilities? =
            try {
                val normalizedUrl = serverUrl.trimEnd('/')
                val endpoint = "$normalizedUrl$CAPABILITIES_ENDPOINT"

                val request =
                    Request
                        .Builder()
                        .url(endpoint)
                        .get()
                        .build()

                val response = standardOkHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        try {
                            val capabilities = json.decodeFromString<PluginCapabilities>(body)
                            Timber.i("Wholphin plugin capabilities on $serverUrl: $capabilities")
                            capabilities
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to parse plugin capabilities response")
                            null
                        }
                    } else {
                        Timber.w("Plugin capabilities endpoint returned empty body")
                        null
                    }
                } else {
                    Timber.v("Wholphin plugin capabilities not available on $serverUrl (status: ${response.code})")
                    null
                }
            } catch (e: Exception) {
                Timber.v(e, "Error checking for Wholphin plugin capabilities on $serverUrl")
                null
            }

        /**
         * Check if the Wholphin plugin is available on the server and fetch the login background URL
         * 
         * This endpoint allows anonymous access since it's used on the login screen.
         *
         * @param serverUrl The base URL of the Jellyfin server
         * @return LoginBackgroundResult containing the background image URL if available, or null if the plugin is not available
         */
        suspend fun getLoginBackground(serverUrl: String): LoginBackgroundResult =
            try {
                val normalizedUrl = serverUrl.trimEnd('/')
                val endpoint = "$normalizedUrl$LOGIN_BACKGROUND_ENDPOINT"

                val request =
                    Request
                        .Builder()
                        .url(endpoint)
                        .get()
                        .build()

                val response = standardOkHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        try {
                            val backgroundData = json.decodeFromString<LoginBackgroundData>(body)
                            if (backgroundData.backgroundUrl.isNotBlank()) {
                                Timber.i(
                                    "Wholphin plugin found on $serverUrl, background URL: ${backgroundData.backgroundUrl}, alpha: ${backgroundData.alpha}, blur: ${backgroundData.blur}"
                                )
                                LoginBackgroundResult.Available(
                                    backgroundUrl = backgroundData.backgroundUrl,
                                    alpha = backgroundData.alpha,
                                    blur = backgroundData.blur,
                                )
                            } else {
                                Timber.w("Wholphin plugin returned empty background URL")
                                LoginBackgroundResult.NotAvailable
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to parse login background response")
                            LoginBackgroundResult.NotAvailable
                        }
                    } else {
                        Timber.w("Wholphin plugin returned empty body")
                        LoginBackgroundResult.NotAvailable
                    }
                } else {
                    Timber.v("Wholphin plugin not available on $serverUrl (status: ${response.code})")
                    LoginBackgroundResult.NotAvailable
                }
            } catch (e: Exception) {
                Timber.v(e, "Error checking for Wholphin plugin on $serverUrl")
                LoginBackgroundResult.NotAvailable
            }

        /**
         * Get home page configuration from the Wholphin plugin
         * 
         * This endpoint requires authentication and returns the dynamic home screen configuration.
         * The configuration defines which sections (rows) to display on the home page and how to fetch their data.
         *
         * @param serverUrl The base URL of the Jellyfin server
         * @return HomeConfiguration object with sections, or null if not available or on error
         */
        suspend fun getHomeConfiguration(serverUrl: String): HomeConfiguration? =
            try {
                val normalizedUrl = serverUrl.trimEnd('/')
                val endpoint = "$normalizedUrl$HOME_ENDPOINT"

                val request =
                    Request
                        .Builder()
                        .url(endpoint)
                        .get()
                        .build()

                val response = authOkHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        try {
                            val config = json.decodeFromString<HomeConfiguration>(body)
                            Timber.i(
                                "Loaded home configuration from $serverUrl: ${config.sections.size} sections"
                            )
                            config
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to parse home configuration response")
                            null
                        }
                    } else {
                        Timber.w("Home configuration endpoint returned empty body")
                        null
                    }
                } else {
                    Timber.v(
                        "Home configuration not available on $serverUrl (status: ${response.code})"
                    )
                    null
                }
            } catch (e: Exception) {
                Timber.v(e, "Error fetching home configuration from $serverUrl")
                null
            }

        /**
         * Get plugin settings from the Wholphin plugin
         * 
         * This endpoint requires authentication and returns general plugin settings.
         * Currently includes the Seerr URL for media requests integration.
         *
         * @param serverUrl The base URL of the Jellyfin server
         * @return PluginSettings object with settings, or null if not available or on error
         */
        suspend fun getPluginSettings(serverUrl: String): PluginSettings? =
            try {
                val normalizedUrl = serverUrl.trimEnd('/')
                val endpoint = "$normalizedUrl$SETTINGS_ENDPOINT"

                val request =
                    Request
                        .Builder()
                        .url(endpoint)
                        .get()
                        .build()

                val response = authOkHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        try {
                            val settings = json.decodeFromString<PluginSettings>(body)
                            Timber.i("Loaded plugin settings from $serverUrl")
                            settings
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to parse plugin settings response")
                            null
                        }
                    } else {
                        Timber.w("Plugin settings endpoint returned empty body")
                        null
                    }
                } else {
                    Timber.v("Plugin settings not available on $serverUrl (status: ${response.code})")
                    null
                }
            } catch (e: Exception) {
                Timber.v(e, "Error fetching plugin settings from $serverUrl")
                null
            }

        /**
         * Get navigation drawer configuration from the Wholphin plugin
         * 
         * This endpoint requires authentication and returns the configuration for items
         * displayed in the navigation drawer between Favorites and Settings.
         * Allows server administrators to control visibility, ordering, and add custom shortcuts.
         *
         * @param serverUrl The base URL of the Jellyfin server
         * @return NavDrawerConfiguration object with items, or null if not available or on error
         */
        suspend fun getNavDrawerConfiguration(serverUrl: String): NavDrawerConfiguration? =
            try {
                val normalizedUrl = serverUrl.trimEnd('/')
                val endpoint = "$normalizedUrl$NAV_DRAWER_ENDPOINT"

                val request =
                    Request
                        .Builder()
                        .url(endpoint)
                        .get()
                        .build()

                val response = authOkHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        try {
                            val config = json.decodeFromString<NavDrawerConfiguration>(body)
                            Timber.i(
                                "Loaded nav drawer configuration from $serverUrl: ${config.items.size} items"
                            )
                            config
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to parse nav drawer configuration response")
                            null
                        }
                    } else {
                        Timber.w("Nav drawer configuration endpoint returned empty body")
                        null
                    }
                } else {
                    Timber.v(
                        "Nav drawer configuration not available on $serverUrl (status: ${response.code})"
                    )
                    null
                }
            } catch (e: Exception) {
                Timber.v(e, "Error fetching nav drawer configuration from $serverUrl")
                null
            }

        /**
         * Make an authenticated request to a Wholphin plugin endpoint
         * 
         * This uses the authenticated HTTP client which includes the current user's access token.
         * Use this for plugin endpoints that require user authentication.
         *
         * @param serverUrl The base URL of the Jellyfin server
         * @param endpoint The plugin endpoint path (e.g., "/wholphin/myendpoint")
         * @param requestBuilder Optional function to customize the request (add body, method, headers, etc.)
         * @return The HTTP response if successful, null otherwise
         */
        suspend fun makeAuthenticatedRequest(
            serverUrl: String,
            endpoint: String,
            requestBuilder: (Request.Builder) -> Request.Builder = { it },
        ): okhttp3.Response? =
            try {
                val normalizedUrl = serverUrl.trimEnd('/')
                val fullEndpoint = "$normalizedUrl$endpoint"

                val baseRequest =
                    Request
                        .Builder()
                        .url(fullEndpoint)
                        .get()

                val request = requestBuilder(baseRequest).build()
                val response = authOkHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    response
                } else {
                    Timber.w("Authenticated request to $endpoint failed with status: ${response.code}")
                    response.close()
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "Error making authenticated request to $endpoint")
                null
            }
    }

@Serializable
data class PluginCapabilities(
    val version: String = "1.0.0",
    val features: Features = Features(),
) {
    @Serializable
    data class Features(
        val loginBackground: Boolean = false,
        val homeConfiguration: Boolean = false,
        // Add more features here as the plugin grows
        // val customThemes: Boolean = false,
        // val enhancedMetadata: Boolean = false,
        // val socialFeatures: Boolean = false,
    )

    /**
     * Check if a specific feature is supported
     */
    fun hasFeature(feature: PluginFeature): Boolean =
        when (feature) {
            PluginFeature.LOGIN_BACKGROUND -> features.loginBackground
            PluginFeature.HOME_CONFIGURATION -> features.homeConfiguration
        }
}

/**
 * Enum of all possible plugin features for type-safe feature checking
 */
enum class PluginFeature {
    LOGIN_BACKGROUND,
    HOME_CONFIGURATION,
    // Add more features here as needed
}

@Serializable
data class LoginBackgroundData(
    val backgroundUrl: String = "",
    val alpha: Float = 0.2f,
    val blur: Int = 1,
)

sealed class LoginBackgroundResult {
    data class Available(
        val backgroundUrl: String,
        val alpha: Float = 0.2f,
        val blur: Int = 1,
    ) : LoginBackgroundResult()

    object NotAvailable : LoginBackgroundResult()
}

/**
 * General plugin settings
 * 
 * Example JSON from server:
 * ```json
 * {
 *   "seerrUrl": "https://your-seerr-instance.com"
 * }
 * ```
 */
@Serializable
data class PluginSettings(
    val seerrUrl: String? = null,
)

// ============================================================================
// Home Configuration Data Classes
// ============================================================================

/**
 * Complete home page configuration from the Wholphin plugin
 * 
 * The plugin version is checked via the capabilities endpoint.
 * This configuration is version-agnostic - unknown section types are simply ignored by the client.
 * 
 * Example JSON from server:
 * ```json
 * {
 *   "sections": [
 *     {
 *       "id": "continue-watching",
 *       "title": "Continue Watching",
 *       "type": "resume",
 *       "limit": 20
 *     },
 *     {
 *       "id": "trending",
 *       "title": "Trending Now",
 *       "type": "custom",
 *       "endpoint": "/wholphin/trending",
 *       "limit": 15
 *     }
 *   ]
 * }
 * ```
 */
@Serializable
data class HomeConfiguration(
    val sections: List<HomeSection> = emptyList(),
)

/**
 * A single section (row) in the home page
 * 
 * Each section can be of different types:
 * - RESUME: Continue watching items (GetResumeItems API)
 * - NEXT_UP: Next episodes to watch (GetNextUp API)
 * - LATEST: Recently added items (GetLatestMedia API)
 * - ITEMS: Custom query using GetItems API with filters
 * - CUSTOM: Custom endpoint defined by the plugin
 *
 * @param id Unique identifier for this section
 * @param title Display title for the row (e.g., "Continue Watching", "Trending")
 * @param type Type of section determining which API to use
 * @param limit Maximum number of items to display (default: 20)
 * @param query Optional query parameters for ITEMS and LATEST types
 * @param endpoint Optional custom endpoint path for CUSTOM type (e.g., "/wholphin/trending")
 */
@Serializable
data class HomeSection(
    val id: String,
    val title: String,
    val type: HomeSectionType,
    val limit: Int = 20,
    val query: HomeSectionQuery? = null,
    val endpoint: String? = null,
)

/**
 * Type of home section determining which Jellyfin API to use
 * 
 * Note: C# server sends these as integer values (0-4), not strings.
 * The order must match the C# enum exactly.
 */
@Serializable(with = HomeSectionTypeSerializer::class)
enum class HomeSectionType {
    /** Continue watching - uses GetResumeItems API */
    RESUME,      // 0

    /** Next episodes to watch - uses GetNextUp API */
    NEXT_UP,     // 1

    /** Recently added items - uses GetLatestMedia API */
    LATEST,      // 2

    /** Custom query - uses GetItems API with filters */
    ITEMS,       // 3

    /** Custom plugin endpoint - calls the specified endpoint */
    CUSTOM,      // 4
}



// ============================================================================
// Navigation Drawer Configuration Data Classes
// ============================================================================

/**
 * Navigation drawer configuration from the Wholphin plugin
 * 
 * Controls the items displayed in the navigation drawer between Favorites and Settings.
 * Allows server administrators to customize visibility, ordering, and add custom shortcuts
 * to collections or playlists.
 * 
 * Example JSON from server:
 * ```json
 * {
 *   "items": [
 *     {
 *       "id": "abc-123-library-id",
 *       "type": "library",
 *       "name": "Movies",
 *       "order": 0,
 *       "visible": true
 *     },
 *     {
 *       "id": "xyz-789-collection-id",
 *       "type": "collection",
 *       "name": "Netflix",
 *       "order": 1,
 *       "visible": true,
 *       "imageUrl": "/Items/xyz-789/Images/Primary"
 *     },
 *     {
 *       "id": "def-456-library-id",
 *       "type": "library",
 *       "order": 2,
 *       "visible": false
 *     }
 *   ]
 * }
 * ```
 */
@Serializable
data class NavDrawerConfiguration(
    val items: List<NavDrawerItemConfig> = emptyList(),
)

/**
 * Configuration for a single navigation drawer item
 * 
 * @param id UUID of the Jellyfin library, collection, or playlist
 * @param type Type of item (LIBRARY, COLLECTION, or PLAYLIST)
 * @param name Optional display name override (if null, uses Jellyfin item name)
 * @param order Sort order in the drawer (lower numbers appear first)
 * @param visible If true, shown in main list; if false, hidden behind "More" button
 * @param imageUrl Optional custom image URL for collection/playlist shortcuts (e.g., "/Items/{id}/Images/Primary")
 */
@Serializable
data class NavDrawerItemConfig(
    val id: String,
    val type: NavDrawerItemType,
    val name: String? = null,
    val order: Int,
    val visible: Boolean = true,
    val imageUrl: String? = null,
)

/**
 * Type of navigation drawer item
 * 
 * Serialized as lowercase strings: "library", "collection", "playlist"
 */
@Serializable
enum class NavDrawerItemType {
    /** Jellyfin library (Movies, TV Shows, Music, etc.) */
    @SerialName("library")
    LIBRARY,

    /** Collection shortcut with optional custom icon */
    @SerialName("collection")
    COLLECTION,

    /** Playlist shortcut with optional custom icon */
    @SerialName("playlist")
    PLAYLIST,
}
/**
 * Custom serializer for HomeSectionType that handles both integer and string values from C# server
 */
object HomeSectionTypeSerializer : KSerializer<HomeSectionType> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("HomeSectionType", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: HomeSectionType) {
        encoder.encodeInt(value.ordinal)
    }

    override fun deserialize(decoder: Decoder): HomeSectionType {
        val index = decoder.decodeInt()
        return HomeSectionType.entries.getOrNull(index) 
            ?: throw IllegalArgumentException("Unknown HomeSectionType ordinal: $index")
    }
}

/**
 * Query parameters for ITEMS and LATEST section types
 * 
 * These map to Jellyfin API parameters:
 * - parentId: Limit to specific library or collection
 * - filters: Item filters (e.g., "IsUnplayed", "IsFavorite")
 * - includeItemTypes: Item types to include (e.g., "Movie", "Series", "Episode")
 * - sortBy: Sort field (e.g., "DateCreated", "SortName", "CommunityRating")
 * - sortOrder: "Ascending" or "Descending"
 * - genres: Filter by genre names
 * - enableRewatching: For NEXT_UP type, allow already watched episodes
 * - enableResumable: For NEXT_UP type, include partially watched episodes
 *
 * Example:
 * ```json
 * {
 *   "parentId": "abc123",
 *   "filters": ["IsUnplayed"],
 *   "includeItemTypes": ["Movie"],
 *   "sortBy": ["DateCreated"],
 *   "limit": 25
 * }
 * ```
 */
@Serializable
data class HomeSectionQuery(
    val parentId: String? = null,
    val filters: List<String>? = null,
    val includeItemTypes: List<String>? = null,
    val sortBy: List<String>? = null,
    val sortOrder: String? = null,
    val genres: List<String>? = null,
    val enableRewatching: Boolean? = null,
    val enableResumable: Boolean? = null,
)
