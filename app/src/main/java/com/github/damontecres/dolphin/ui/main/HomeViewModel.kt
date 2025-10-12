package com.github.damontecres.dolphin.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.DefaultItemFields
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.dolphin.util.LoadingExceptionHandler
import com.github.damontecres.dolphin.util.LoadingState
import com.github.damontecres.dolphin.util.supportItemKinds
import com.github.damontecres.dolphin.util.supportedCollectionTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userApi
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
    ) : ViewModel() {
        val loadingState = MutableLiveData<LoadingState>(LoadingState.Loading)
        val homeRows = MutableLiveData<List<HomeRow>>()

        fun init(preferences: UserPreferences) {
            val limit = preferences.appPreferences.homePagePreferences.maxItemsPerRow
            viewModelScope.launch(
                Dispatchers.IO +
                    LoadingExceptionHandler(
                        loadingState,
                        "Error loading home page",
                    ),
            ) {
                Timber.d("init HomeViewModel")
                val user by api.userApi.getCurrentUser()
//                val displayPrefs =
//                    api.displayPreferencesApi
//                        .getDisplayPreferences(
//                            displayPreferencesId = "usersettings",
//                            client = "emby",
//                        ).content
                val homeSections =
                    listOf(
                        HomeSection.RESUME,
                        HomeSection.NEXT_UP,
                        HomeSection.LATEST_MEDIA,
                    )
                // TODO use display preferences?

//                    displayPrefs.customPrefs.entries
//                        .filter { it.key.startsWith("homesection") && it.value.isNotNullOrBlank() }
//                        .sortedBy { it.key }
//                        .map { HomeSection.fromKey(it.value ?: "") }
//                        .filter {
//                            it in
//                                setOf(
//                                    HomeSection.LATEST_MEDIA,
//                                    HomeSection.NEXT_UP,
//                                    HomeSection.RESUME,
//                                )
//                        }

                // TODO data is fetched all together which may be slow for large servers
                val homeRows =
                    homeSections
                        .mapNotNull { section ->
                            Timber.Forest.v("Loading section: %s", section.name)
                            when (section) {
                                HomeSection.LATEST_MEDIA -> {
                                    getLatest(user, limit)
                                }

                                HomeSection.RESUME -> {
                                    val items = getResume(user.id, limit)
                                    listOf(
                                        HomeRow(
                                            section = section,
                                            items = items,
                                        ),
                                    )
                                }

                                HomeSection.NEXT_UP -> {
                                    val nextUp =
                                        getNextUp(
                                            user.id,
                                            limit,
                                            preferences.appPreferences.homePagePreferences.enableRewatchingNextUp,
                                        )
                                    listOf(
                                        HomeRow(
                                            section = section,
                                            items = nextUp,
                                        ),
                                    )
                                }

                                // TODO
                                HomeSection.LIVE_TV -> null
                                HomeSection.ACTIVE_RECORDINGS -> null

                                // TODO Not supported?
                                HomeSection.LIBRARY_TILES_SMALL -> null
                                HomeSection.LIBRARY_BUTTONS -> null
                                HomeSection.RESUME_AUDIO -> null
                                HomeSection.RESUME_BOOK -> null
                                HomeSection.NONE -> null
                            }
                        }.flatten()
                        .filter { it.items.isNotEmpty() }
                withContext(Dispatchers.Main) {
                    this@HomeViewModel.homeRows.value = homeRows
                    loadingState.value = LoadingState.Success
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
                    fields = DefaultItemFields,
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
                    fields = DefaultItemFields,
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
        ): List<HomeRow> {
            val latestMediaIncludes =
                user.configuration?.orderedViews.orEmpty().toMutableList().apply {
                    removeAll(user.configuration?.latestItemsExcludes.orEmpty())
                }

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
                                fields = DefaultItemFields,
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
