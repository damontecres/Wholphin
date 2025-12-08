package com.github.damontecres.wholphin.api.seerr

import com.github.damontecres.wholphin.api.seerr.infrastructure.ApiClient
import okhttp3.Call
import okhttp3.OkHttpClient
import timber.log.Timber

class SeerrApiClient(
    val baseUrl: String,
    val apiKey: String,
    okHttpClient: OkHttpClient,
) {
    private val client =
        okHttpClient
            .newBuilder()
            .addInterceptor {
                Timber.d("SeerrApiClient: ${it.request().method} ${it.request().url}")
                it.proceed(
                    it
                        .request()
                        .newBuilder()
                        .header("X-Api-Key", apiKey)
                        .build(),
                )
            }.build()

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
