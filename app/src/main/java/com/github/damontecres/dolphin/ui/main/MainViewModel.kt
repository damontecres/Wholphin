package com.github.damontecres.dolphin.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.ui.DefaultItemFields
import com.github.damontecres.dolphin.ui.isNotNullOrBlank
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.displayPreferencesApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        val api: ApiClient,
    ) : ViewModel() {
        val homeRows = MutableLiveData<List<HomeRow>>()

        init {
            viewModelScope.launch(Dispatchers.IO) {
                val user = api.userApi.getCurrentUser().content
                val displayPrefs =
                    api.displayPreferencesApi
                        .getDisplayPreferences(
                            displayPreferencesId = "usersettings",
                            client = "emby",
                        ).content
                val homeSections =
                    displayPrefs.customPrefs.entries
                        .filter { it.key.startsWith("homesection") && it.value.isNotNullOrBlank() }
                        .sortedBy { it.key }
                        .map { HomeSection.fromKey(it.value ?: "") }
                        .filterNot { it == HomeSection.NONE }

                val latestMediaIncludes =
                    user.configuration?.orderedViews.orEmpty().toMutableList().apply {
                        removeAll(user.configuration?.latestItemsExcludes.orEmpty())
                    }

                val views by api.userViewsApi.getUserViews()

                val homeRows =
                    homeSections
                        .mapNotNull { section ->
                            Timber.Forest.v("Loading section: %s", section.name)
                            when (section) {
                                HomeSection.LATEST_MEDIA -> {
                                    latestMediaIncludes.map { viewId ->
                                        val title =
                                            views.items.firstOrNull { it.id == viewId }?.name?.let {
                                                "Recently Added in $it"
                                            }
                                        val request =
                                            GetLatestMediaRequest(
                                                fields = DefaultItemFields,
                                                imageTypeLimit = 1,
                                                parentId = viewId,
                                                groupItems = true,
                                                limit = 25,
                                            )
                                        val latest =
                                            api.userLibraryApi
                                                .getLatestMedia(request)
                                                .content
                                                .map { BaseItem.Companion.from(it, api, true) }
                                        HomeRow(
                                            section = section,
                                            items = latest,
                                            title = title,
                                        )
                                    }
                                }

                                HomeSection.RESUME -> {
                                    val request =
                                        GetResumeItemsRequest(
                                            userId = user.id,
                                            fields = DefaultItemFields,
                                            // TODO, more params?
                                        )
                                    val items =
                                        api.itemsApi
                                            .getResumeItems(request)
                                            .content
                                            .items
                                            .map { BaseItem.Companion.from(it, api, true) }
                                    listOf(
                                        HomeRow(
                                            section = section,
                                            items = items,
                                        ),
                                    )
                                }

                                HomeSection.NEXT_UP -> {
                                    val request =
                                        GetNextUpRequest(
                                            fields = DefaultItemFields,
                                            imageTypeLimit = 1,
                                            parentId = null,
                                            limit = 25,
                                            enableResumable = false,
                                        )
                                    val nextUp =
                                        api.tvShowsApi
                                            .getNextUp(request)
                                            .content
                                            .items
                                            .map { BaseItem.Companion.from(it, api) }
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
                    this@MainViewModel.homeRows.value = homeRows
                }
            }
        }
    }
