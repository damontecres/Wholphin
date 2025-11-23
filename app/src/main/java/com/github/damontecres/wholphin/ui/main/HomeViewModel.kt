package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.NavDrawerItemRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.DatePlayedService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.nav.ServerNavDrawerItem
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.supportItemKinds
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import timber.log.Timber
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val api: ApiClient,
        val navigationManager: NavigationManager,
        val serverRepository: ServerRepository,
        val navDrawerItemRepository: NavDrawerItemRepository,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val datePlayedService: DatePlayedService,
    ) : ViewModel() {
        val loadingState = MutableLiveData<LoadingState>(LoadingState.Pending)
        val refreshState = MutableLiveData<LoadingState>(LoadingState.Pending)
        val watchingRows = MutableLiveData<List<HomeRowLoadingState>>(listOf())
        val latestRows = MutableLiveData<List<HomeRowLoadingState>>(listOf())

        private lateinit var preferences: UserPreferences

        init {
            datePlayedService.invalidateAll()
        }

        fun init(preferences: UserPreferences): Job {
            val reload = loadingState.value != LoadingState.Success
            if (reload) {
                loadingState.value = LoadingState.Loading
            }
            refreshState.value = LoadingState.Loading
            this.preferences = preferences
            val prefs = preferences.appPreferences.homePagePreferences
            val limit = prefs.maxItemsPerRow
            return viewModelScope.launch(
                Dispatchers.IO +
                    LoadingExceptionHandler(
                        loadingState,
                        "Error loading home page",
                    ),
            ) {
                Timber.d("init HomeViewModel")

                serverRepository.currentUserDto.value?.let { userDto ->
                    val includedIds =
                        navDrawerItemRepository
                            .getFilteredNavDrawerItems(navDrawerItemRepository.getNavDrawerItems())
                            .filter { it is ServerNavDrawerItem }
                            .map { (it as ServerNavDrawerItem).itemId }
                    // TODO data is fetched all together which may be slow for large servers
                    val resume = getResume(userDto.id, limit, true)
                    val nextUp =
                        getNextUp(
                            userDto.id,
                            limit,
                            prefs.enableRewatchingNextUp,
                            false,
                        )
                    val watching =
                        buildList {
                            if (prefs.combineContinueNext) {
                                val items = buildCombined(resume, nextUp)
                                add(
                                    HomeRowLoadingState.Success(
                                        title = context.getString(R.string.continue_watching),
                                        items = items,
                                    ),
                                )
                            } else {
                                if (resume.isNotEmpty()) {
                                    add(
                                        HomeRowLoadingState.Success(
                                            title = context.getString(R.string.continue_watching),
                                            items = resume,
                                        ),
                                    )
                                }
                                if (nextUp.isNotEmpty()) {
                                    add(
                                        HomeRowLoadingState.Success(
                                            title = context.getString(R.string.next_up),
                                            items = nextUp,
                                        ),
                                    )
                                }
                            }
                        }

                    val latest = getLatest(userDto, limit, includedIds)
                    val pendingLatest = latest.map { HomeRowLoadingState.Loading(it.title) }

                    withContext(Dispatchers.Main) {
                        this@HomeViewModel.watchingRows.value = watching
                        if (reload) {
                            this@HomeViewModel.latestRows.value = pendingLatest
                        }
                        loadingState.value = LoadingState.Success
                    }
                    loadLatest(latest)
                    refreshState.setValueOnMain(LoadingState.Success)
                }
            }
        }

        private suspend fun getResume(
            userId: UUID,
            limit: Int,
            includeEpisodes: Boolean,
        ): List<BaseItem> {
            val request =
                GetResumeItemsRequest(
                    userId = userId,
                    fields = SlimItemFields,
                    limit = limit,
                    includeItemTypes =
                        if (includeEpisodes) {
                            supportItemKinds
                        } else {
                            supportItemKinds
                                .toMutableSet()
                                .apply {
                                    remove(BaseItemKind.EPISODE)
                                }
                        },
                )
            val items =
                api.itemsApi
                    .getResumeItems(request)
                    .content
                    .items
                    .map { BaseItem.from(it, api, true) }
            return items
        }

        private suspend fun getNextUp(
            userId: UUID,
            limit: Int,
            enableRewatching: Boolean,
            enableResumable: Boolean,
        ): List<BaseItem> {
            val request =
                GetNextUpRequest(
                    userId = userId,
                    fields = SlimItemFields,
                    imageTypeLimit = 1,
                    parentId = null,
                    limit = limit,
                    enableResumable = enableResumable,
                    enableUserData = true,
                    enableRewatching = enableRewatching,
                )
            val nextUp =
                api.tvShowsApi
                    .getNextUp(request)
                    .content
                    .items
                    .map { BaseItem.from(it, api, true) }
            return nextUp
        }

        private suspend fun getLatest(
            user: UserDto,
            limit: Int,
            includedIds: List<UUID>,
        ): List<LatestData> {
            val excluded = user.configuration?.latestItemsExcludes.orEmpty()
            val views by api.userViewsApi.getUserViews()
            val latestData =
                views.items
                    .filter {
                        it.id in includedIds && it.id !in excluded &&
                            // Exclude Live TV because a recording folder view will be used instead
                            it.collectionType != CollectionType.LIVETV
                    }.map { view ->
                        val title =
                            view.name?.let { context.getString(R.string.recently_added_in, it) }
                                ?: context.getString(R.string.recently_added)
                        val request =
                            GetLatestMediaRequest(
                                fields = SlimItemFields,
                                imageTypeLimit = 1,
                                parentId = view.id,
                                groupItems = true,
                                limit = limit,
                                isPlayed = null, // Server will handle user's preference
                            )
                        LatestData(title, request)
                    }

            return latestData
        }

        private suspend fun loadLatest(latestData: List<LatestData>) {
            val rows =
                latestData.map { (title, request) ->
                    try {
                        val latest =
                            api.userLibraryApi
                                .getLatestMedia(request)
                                .content
                                .map { BaseItem.from(it, api, true) }
                        HomeRowLoadingState.Success(
                            title = title,
                            items = latest,
                        )
                    } catch (ex: Exception) {
                        Timber.e(ex, "Exception fetching %s", title)
                        HomeRowLoadingState.Error(
                            title = title,
                            exception = ex,
                        )
                    }
                }
            latestRows.setValueOnMain(rows)
        }

        private suspend fun buildCombined(
            resume: List<BaseItem>,
            nextUp: List<BaseItem>,
        ): List<BaseItem> =
            withContext(Dispatchers.IO) {
                val start = System.currentTimeMillis()
                val semaphore = Semaphore(3)
                val deferred =
                    nextUp
                        .filter { it.data.seriesId != null }
                        .map { item ->
                            viewModelScope.async(Dispatchers.IO) {
                                try {
                                    semaphore.withPermit {
                                        datePlayedService.getLastPlayed(item)
                                    }
                                } catch (ex: Exception) {
                                    Timber.e(ex, "Error fetching %s", item.id)
                                    null
                                }
                            }
                        }

                val nextUpLastPlayed = deferred.awaitAll()
                val timestamps = mutableMapOf<UUID, LocalDateTime?>()
                nextUp.map { it.id }.zip(nextUpLastPlayed).toMap(timestamps)
                resume.forEach { timestamps[it.id] = it.data.userData?.lastPlayedDate }
                val result = (resume + nextUp).sortedByDescending { timestamps[it.id] }
                val duration = (System.currentTimeMillis() - start).milliseconds
                Timber.v("buildCombined took %s", duration)
                return@withContext result
            }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            withContext(Dispatchers.Main) {
                init(preferences)
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            withContext(Dispatchers.Main) {
                init(preferences)
            }
        }
    }

val supportedLatestCollectionTypes =
    setOf(
        CollectionType.MOVIES,
        CollectionType.TVSHOWS,
        CollectionType.LIVETV,
    )

data class LatestData(
    val title: String,
    val request: GetLatestMediaRequest,
)
