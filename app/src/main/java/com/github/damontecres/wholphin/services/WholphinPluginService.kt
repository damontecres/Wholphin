package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import kotlinx.serialization.Serializable
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
        }
}

/**
 * Enum of all possible plugin features for type-safe feature checking
 */
enum class PluginFeature {
    LOGIN_BACKGROUND,
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
