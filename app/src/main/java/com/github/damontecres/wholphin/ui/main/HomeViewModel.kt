package com.github.damontecres.wholphin.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.NavDrawerItemRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.nav.NavigationManager
import com.github.damontecres.wholphin.ui.nav.ServerNavDrawerItem
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.supportItemKinds
import com.github.damontecres.wholphin.util.supportedCollectionTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        val api: ApiClient,
        val navigationManager: NavigationManager,
        val serverRepository: ServerRepository,
        val navDrawerItemRepository: NavDrawerItemRepository,
    ) : ViewModel() {
        val loadingState = MutableLiveData<LoadingState>(LoadingState.Pending)
        val homeRows = MutableLiveData<List<HomeRow>>()

        fun init(preferences: UserPreferences): Job {
            loadingState.value = LoadingState.Loading
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

                serverRepository.currentUserDto?.let { userDto ->
                    val includedIds =
                        navDrawerItemRepository
                            .getFilteredNavDrawerItems(navDrawerItemRepository.getNavDrawerItems())
                            .filter { it is ServerNavDrawerItem }
                            .map { (it as ServerNavDrawerItem).itemId }
                    // TODO data is fetched all together which may be slow for large servers
                    val resume = getResume(userDto.id, limit)
                    val nextUp = getNextUp(userDto.id, limit, prefs.enableRewatchingNextUp)
                    val latest = getLatest(userDto, limit, includedIds)

                    val homeRows =
                        if (prefs.combineContinueNext) {
                            listOf(
                                HomeRow(
                                    section = HomeSection.NEXT_UP,
                                    items = resume + nextUp,
                                ),
                                *latest.toTypedArray(),
                            )
                        } else {
                            listOf(
                                HomeRow(
                                    section = HomeSection.RESUME,
                                    items = resume,
                                ),
                                HomeRow(
                                    section = HomeSection.NEXT_UP,
                                    items = nextUp,
                                ),
                                *latest.toTypedArray(),
                            )
                        }
                    withContext(Dispatchers.Main) {
                        this@HomeViewModel.homeRows.value = homeRows
                        loadingState.value = LoadingState.Success
                    }
                }
            }
        }

        private suspend fun getResume(
            userId: UUID,
            limit: Int,
        ): List<BaseItem> {
            val request =
                GetResumeItemsRequest(
                    userId = userId,
                    fields = SlimItemFields,
                    limit = limit,
                    includeItemTypes = supportItemKinds,
                )
            val items =
                api.itemsApi
                    .getResumeItems(request)
                    .content
                    .items
                    .map { BaseItem.Companion.from(it, api, true) }
            return items
        }

        private suspend fun getNextUp(
            userId: UUID,
            limit: Int,
            enableRewatching: Boolean,
        ): List<BaseItem> {
            val request =
                GetNextUpRequest(
                    userId = userId,
                    fields = SlimItemFields,
                    imageTypeLimit = 1,
                    parentId = null,
                    limit = limit,
                    enableResumable = false,
                    enableUserData = true,
                    enableRewatching = enableRewatching,
                )
            val nextUp =
                api.tvShowsApi
                    .getNextUp(request)
                    .content
                    .items
                    .map { BaseItem.Companion.from(it, api, true) }
            return nextUp
        }

        private suspend fun getLatest(
            user: UserDto,
            limit: Int,
            includedIds: List<UUID>,
        ): List<HomeRow> {
            val latestMediaIncludes =
                user.configuration
                    ?.orderedViews
                    .orEmpty()
                    .toMutableList()
                    .apply {
                        removeAll(user.configuration?.latestItemsExcludes.orEmpty())
                    }.filter { includedIds.contains(it) }

            val views by api.userViewsApi.getUserViews()
            val rows =
                latestMediaIncludes
                    .mapNotNull { viewId -> views.items.firstOrNull { it.id == viewId } }
                    .filter { it.collectionType in supportedCollectionTypes }
                    .map { view ->
                        val title =
                            view.name?.let { "Recently Added in $it" }
                        val request =
                            GetLatestMediaRequest(
                                fields = SlimItemFields,
                                imageTypeLimit = 1,
                                parentId = view.id,
                                groupItems = true,
                                limit = limit,
                                isPlayed = null, // Server will handle user's preference
                            )
                        val latest =
                            api.userLibraryApi
                                .getLatestMedia(request)
                                .content
                                .map { BaseItem.from(it, api, true) }
                        HomeRow(
                            section = HomeSection.LATEST_MEDIA,
                            items = latest,
                            title = title,
                        )
                    }
            return rows
        }
    }
