package com.github.damontecres.wholphin.services

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.LocalTrailer
import com.github.damontecres.wholphin.data.model.RemoteTrailer
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.ui.nav.Destination
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrailerService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
    ) {
        suspend fun getTrailers(item: BaseItem): List<Trailer> {
            val remoteTrailers =
                item.data.remoteTrailers
                    ?.mapNotNull { t ->
                        t.url?.let { url ->
                            val name =
                                t.name
                                    // TODO would be nice to clean up the trailer name
//                                                ?.replace(item.name ?: "", "")
//                                                ?.removePrefix(" - ")
                                    ?: context.getString(R.string.trailer)
                            val subtitle =
                                when (url.toUri().host) {
                                    "youtube.com", "www.youtube.com" -> "YouTube"
                                    else -> null
                                }
                            RemoteTrailer(name, url, subtitle)
                        }
                    }.orEmpty()
                    .sortedWith(
                        compareBy(
                            {
                                // Try to show official trailers first
                                if (it.name.contains("Official Trailer", ignoreCase = true) ||
                                    it.name.contains(
                                        "Official Theatrical Trailer",
                                        ignoreCase = true,
                                    )
                                ) {
                                    0
                                } else {
                                    1
                                }
                            },
                            {
                                it.name
                            },
                        ),
                    )
            val localTrailerCount = item.data.localTrailerCount ?: 0
            val localTrailers =
                if (localTrailerCount > 0) {
                    api.userLibraryApi.getLocalTrailers(item.id).content.map {
                        LocalTrailer(BaseItem.Companion.from(it, api))
                    }
                } else {
                    listOf()
                }
            return localTrailers + remoteTrailers
        }

        companion object {
            /**
             * Note: This is explicitly <b>not</b> a member function because the injected Context is not an Activity.
             * We want to start the intent without a new task which requires the Activity context
             *
             * This can be provided by LocalContext.current from Compose
             */
            fun onClick(
                context: Context,
                trailer: Trailer,
                navigateTo: (Destination) -> Unit,
            ) {
                when (trailer) {
                    is LocalTrailer -> {
                        navigateTo.invoke(
                            Destination.Playback(
                                itemId = trailer.baseItem.id,
                                item = trailer.baseItem,
                                positionMs = 0L,
                            ),
                        )
                    }

                    is RemoteTrailer -> {
                        val intent = Intent(Intent.ACTION_VIEW, trailer.url.toUri())
                        context.startActivity(intent)
                    }
                }
            }
        }
    }
