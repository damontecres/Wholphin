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
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.DatePlayedService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.HomeScreenSectionsService
import com.github.damontecres.wholphin.services.LatestNextUpService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.ServerNavDrawerItem
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

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
        private val latestNextUpService: LatestNextUpService,
        private val backdropService: BackdropService,
        private val homeScreenSectionsService: HomeScreenSectionsService,
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
                if (reload) {
                    backdropService.clearBackdrop()
                }

                serverRepository.currentUserDto.value?.let { userDto ->
                    // Try to fetch custom sections from the plugin first (if enabled)
                    val enableCustomHomeRows = preferences.appPreferences.interfacePreferences.enableCustomHomeRows
                    Timber.d("HomeViewModel: Custom home rows enabled=$enableCustomHomeRows")
                    val customSections = if (enableCustomHomeRows) {
                        homeScreenSectionsService.getCustomSections(userDto.id)
                    } else {
                        null
                    }
                    
                    if (customSections != null) {
                        // Plugin sections are available, use them
                        // The plugin provides all sections in order, including continue watching and next up
                        Timber.i("HomeViewModel: Using custom home screen sections from plugin (${customSections.size} sections)")
                        withContext(Dispatchers.Main) {
                            // Plugin sections replace the entire home screen
                            // We'll put them all in latestRows and keep watchingRows empty
                            // since the plugin manages the order and includes watching sections
                            this@HomeViewModel.watchingRows.value = emptyList()
                            if (reload) {
                                this@HomeViewModel.latestRows.value = customSections.map { 
                                    if (it is HomeRowLoadingState.Success) {
                                        HomeRowLoadingState.Loading(it.title)
                                    } else {
                                        it
                                    }
                                }
                            }
                            loadingState.value = LoadingState.Success
                        }
                        refreshState.setValueOnMain(LoadingState.Success)
                        // Sections are already loaded, just set them
                        this@HomeViewModel.latestRows.setValueOnMain(customSections)
                    } else {
                        // Plugin not available, use default behavior
                        Timber.d("HomeViewModel: Plugin not available, using default home screen sections")
                        val includedIds =
                            navDrawerItemRepository
                                .getFilteredNavDrawerItems(navDrawerItemRepository.getNavDrawerItems())
                                .filter { it is ServerNavDrawerItem }
                                .map { (it as ServerNavDrawerItem).itemId }
                        val resume = latestNextUpService.getResume(userDto.id, limit, true)
                        val nextUp =
                            latestNextUpService.getNextUp(
                                userDto.id,
                                limit,
                                prefs.enableRewatchingNextUp,
                                false,
                            )
                        val watching =
                            buildList {
                                if (prefs.combineContinueNext) {
                                    val items = latestNextUpService.buildCombined(resume, nextUp)
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

                        val latest = latestNextUpService.getLatest(userDto, limit, includedIds)
                        val pendingLatest = latest.map { HomeRowLoadingState.Loading(it.title) }

                        withContext(Dispatchers.Main) {
                            this@HomeViewModel.watchingRows.value = watching
                            if (reload) {
                                this@HomeViewModel.latestRows.value = pendingLatest
                            }
                            loadingState.value = LoadingState.Success
                        }
                        refreshState.setValueOnMain(LoadingState.Success)
                        val loadedLatest = latestNextUpService.loadLatest(latest)
                        this@HomeViewModel.latestRows.setValueOnMain(loadedLatest)
                    }
                }
            }
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

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }
    }

val supportedLatestCollectionTypes =
    setOf(
        CollectionType.MOVIES,
        CollectionType.TVSHOWS,
        CollectionType.HOMEVIDEOS,
        // Exclude Live TV because a recording folder view will be used instead
        null, // Recordings & mixed collection types
    )

data class LatestData(
    val title: String,
    val request: GetLatestMediaRequest,
)
