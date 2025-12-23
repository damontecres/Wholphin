package com.github.damontecres.wholphin.api.seerr

import com.github.damontecres.wholphin.api.seerr.infrastructure.ApiClient
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import okhttp3.Call
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import timber.log.Timber

class SeerrApiClient(
    val baseUrl: String,
    private val apiKey: String?,
    okHttpClient: OkHttpClient,
) {
    private val cookieJar = SeerrCookieJar()

    private val client =
        okHttpClient
            .newBuilder()
            .cookieJar(cookieJar)
            .addInterceptor {
                Timber.d("SeerrApiClient: ${it.request().method} ${it.request().url}")
                it.proceed(
                    it
                        .request()
                        .newBuilder()
                        .apply {
                            if (apiKey.isNotNullOrBlank()) header("X-Api-Key", apiKey)
                        }.build(),
                )
            }.build()

    val hasValidCredentials: Boolean
        get() =
            apiKey.isNotNullOrBlank() ||
                cookieJar.hasValidCredentials(baseUrl)

    private fun <T : ApiClient> create(initializer: (String, Call.Factory) -> T): Lazy<T> =
        lazy {
            initializer.invoke(baseUrl, client)
        }

    val authApi by create(::AuthApi)
    val blacklistApi by create(::BlacklistApi)
    val collectionApi by create(::CollectionApi)
    val issueApi by create(::IssueApi)
    val mediaApi by create(::MediaApi)
    val moviesApi by create(::MoviesApi)
    val otherApi by create(::OtherApi)
    val overrideruleApi by create(::OverrideruleApi)
    val personApi by create(::PersonApi)
    val publicApi by create(::PublicApi)
    val requestApi by create(::RequestApi)
    val searchApi by create(::SearchApi)
    val serviceApi by create(::ServiceApi)
    val settingsApi by create(::SettingsApi)
    val tmdbApi by create(::TmdbApi)
    val tvApi by create(::TvApi)
    val usersApi by create(::UsersApi)
    val watchlistApi by create(::WatchlistApi)
}

private class SeerrCookieJar : CookieJar {
    private val cookies = mutableMapOf<String, List<Cookie>>()

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        cookies
            .filter { it.name == "connect.sid" }
            .groupBy { it.domain }
            .forEach { (domain, cookies) ->
                this.cookies[domain] = cookies
            }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = this.cookies[url.host].orEmpty()

    fun hasValidCredentials(baseUrl: String): Boolean =
        baseUrl.toHttpUrlOrNull()?.host?.let { domain ->
            cookies[domain]?.any { it.expiresAt > System.currentTimeMillis() }
        } == true
}
