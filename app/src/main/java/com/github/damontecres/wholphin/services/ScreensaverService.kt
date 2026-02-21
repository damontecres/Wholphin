package com.github.damontecres.wholphin.services

import android.content.Context
import android.view.WindowManager
import coil3.imageLoader
import coil3.request.ImageRequest
import com.github.damontecres.wholphin.MainActivity
import com.github.damontecres.wholphin.services.hilt.DefaultCoroutineScope
import com.github.damontecres.wholphin.ui.components.CurrentItem
import com.github.damontecres.wholphin.ui.formatDate
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class ScreensaverService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
        private val api: ApiClient,
        private val userPreferencesService: UserPreferencesService,
        private val imageUrlService: ImageUrlService,
    ) {
        private val _state = MutableStateFlow(ScreensaverState(false, false, false, false))
        val state: StateFlow<ScreensaverState> = _state

        private var waitJob: Job? = null

        init {
            userPreferencesService.flow
                .onEach { prefs ->
                    _state.update {
                        val enabled =
                            prefs.appPreferences.interfacePreferences.screensaverPreference.enabled
                        keepScreenOnInternal(enabled)
                        ScreensaverState(enabled, false, false, false)
                    }
                }.launchIn(scope)
        }

        fun pulse() {
            waitJob?.cancel()
            if (_state.value.enabled) {
//                Timber.v("pulse")
                _state.update {
                    if (!it.active) {
                        it.copy(active = false)
                    } else {
                        it
                    }
                }

                if (!_state.value.paused) {
                    waitJob =
                        scope.launch(ExceptionHandler()) {
                            val startDelay =
                                userPreferencesService
                                    .getCurrent()
                                    .appPreferences.interfacePreferences.screensaverPreference.startDelay.milliseconds
                            delay(startDelay)
                            _state.update {
                                it.copy(active = true)
                            }
                        }
                }
            }
        }

        fun start() {
            _state.update {
                it.copy(
                    enabledTemp = true,
                    active = true,
                )
            }
        }

        fun stop(cancelJob: Boolean) {
            _state.update {
                it.copy(
                    enabledTemp = false,
                    active = false,
                )
            }
            if (cancelJob) waitJob?.cancel()
        }

        fun keepScreenOn(keep: Boolean) {
            scope.launch {
                val screensaverEnabled = _state.value.enabled
                Timber.d("Keep screen on: %s, screensaverEnabled=%s", keep, screensaverEnabled)
                if (screensaverEnabled) {
                    // Page is requesting to keep screen on, so we don't wait to show the screensaver
                    _state.update {
                        it.copy(active = false, paused = keep)
                    }
                    if (!keep) {
                        pulse()
                    }
                } else {
                    keepScreenOnInternal(keep)
                }
            }
        }

        private suspend fun keepScreenOnInternal(keep: Boolean) =
            withContext(Dispatchers.Main) {
                val window = MainActivity.instance.window
                if (keep) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

        fun createItemFlow(scope: CoroutineScope): Flow<CurrentItem?> =
            flow {
                val prefs =
                    userPreferencesService.flow
                        .first()
                        .appPreferences
                        .interfacePreferences.screensaverPreference
                val maxAge = prefs.maxAgeFilter.takeIf { it >= 0 }
                val itemTypes = prefs.itemTypesList.map { BaseItemKind.fromName(it) }
                val request =
                    GetItemsRequest(
                        recursive = true,
                        includeItemTypes = itemTypes,
                        imageTypes = if (BaseItemKind.PHOTO in itemTypes) null else listOf(ImageType.BACKDROP),
                        sortBy = listOf(ItemSortBy.RANDOM),
                        maxOfficialRating = maxAge?.toString(),
                        hasParentalRating = maxAge?.let { true },
                    )
                val pager =
                    ApiRequestPager(api, request, GetItemsRequestHandler, scope).init()
                Timber.v("Got %s items", pager.size)
                var index = 0
                if (pager.isEmpty()) {
                    emit(null)
                } else {
                    while (true) {
                        val item = pager.getBlocking(index)
                        Timber.v("Next index=%s, item=%s", index, item?.id)
                        if (item != null) {
                            val backdropUrl =
                                if (item.type == BaseItemKind.PHOTO) {
                                    api.libraryApi.getDownloadUrl(item.id)
                                } else {
                                    imageUrlService.getItemImageUrl(item, ImageType.BACKDROP)
                                }
                            val title =
                                if (item.type == BaseItemKind.PHOTO) {
                                    item.data.premiereDate?.let {
                                        formatDate(it.toLocalDate())
                                    }
                                } else {
                                    item.title
                                }
                            val logoUrl = imageUrlService.getItemImageUrl(item, ImageType.LOGO)
                            if (backdropUrl != null) {
                                val result =
                                    context.imageLoader
                                        .enqueue(
                                            ImageRequest
                                                .Builder(context)
                                                .data(backdropUrl)
                                                .build(),
                                        ).job
                                        .await()
                                try {
                                    emit(CurrentItem(item, backdropUrl, logoUrl, title ?: ""))
                                } catch (_: CancellationException) {
                                    break
                                }
                                val duration =
                                    userPreferencesService
                                        .getCurrent()
                                        .appPreferences
                                        .interfacePreferences.screensaverPreference.duration.milliseconds
                                delay(duration)
                            }
                        }
                        index++
                    }
                }
            }.cancellable()
    }

data class ScreensaverState(
    val enabled: Boolean,
    val enabledTemp: Boolean,
    val active: Boolean,
    val paused: Boolean,
) {
    val show get() = (enabled || enabledTemp) && active && !paused
}
