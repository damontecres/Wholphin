package com.github.damontecres.wholphin.ui.detail.metadata

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.showToast
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemLookupApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.remoteImageApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BoxSetInfo
import org.jellyfin.sdk.model.api.BoxSetInfoRemoteSearchQuery
import org.jellyfin.sdk.model.api.ImageInfo
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MetadataRefreshMode
import org.jellyfin.sdk.model.api.MovieInfo
import org.jellyfin.sdk.model.api.MovieInfoRemoteSearchQuery
import org.jellyfin.sdk.model.api.RemoteImageInfo
import org.jellyfin.sdk.model.api.RemoteSearchResult
import org.jellyfin.sdk.model.api.SeriesInfo
import org.jellyfin.sdk.model.api.SeriesInfoRemoteSearchQuery
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Item types that can be identified (matched to a metadata provider result)
 */
val identifiableTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.BOX_SET)

/**
 * The refresh options, matching the Jellyfin web UI
 */
enum class RefreshOption {
    /** Scan for new and updated files */
    SCAN,

    /** Search for missing metadata */
    MISSING,

    /** Replace all metadata */
    REPLACE_ALL,
}

/**
 * The result of a request that loads a list
 */
sealed interface ListState<out T> {
    data object NotLoaded : ListState<Nothing>

    data object Loading : ListState<Nothing>

    data class Success<T>(
        val items: List<T>,
    ) : ListState<T>

    data class Error(
        val message: String?,
    ) : ListState<Nothing>
}

data class ManageMetadataState(
    val searchResults: ListState<RemoteSearchResult> = ListState.NotLoaded,
    val currentImages: ListState<ImageInfo> = ListState.NotLoaded,
    val remoteImages: ListState<RemoteImageInfo> = ListState.NotLoaded,
)

/**
 * Refreshes metadata, identifies items, and manages images. These require the user to be a
 * Jellyfin administrator.
 */
@HiltViewModel
class ManageMetadataViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ManageMetadataState())
        val state: StateFlow<ManageMetadataState> = _state

        /**
         * Managing metadata requires a Jellyfin administrator
         */
        fun canManageMetadata(): Boolean = serverRepository.currentUserDto?.policy?.isAdministrator == true

        fun reset() {
            _state.value = ManageMetadataState()
        }

        fun refresh(
            itemId: UUID,
            option: RefreshOption,
            replaceImages: Boolean,
        ) {
            viewModelScope.launchIO {
                runAndReport(R.string.metadata_refresh_started) {
                    val mode =
                        when (option) {
                            RefreshOption.SCAN -> MetadataRefreshMode.DEFAULT
                            RefreshOption.MISSING, RefreshOption.REPLACE_ALL -> MetadataRefreshMode.FULL_REFRESH
                        }
                    api.libraryApi.refreshItem(
                        itemId = itemId,
                        metadataRefreshMode = mode,
                        imageRefreshMode = mode,
                        replaceAllMetadata = option == RefreshOption.REPLACE_ALL,
                        replaceAllImages = option != RefreshOption.SCAN && replaceImages,
                    )
                }
            }
        }

        fun search(
            item: BaseItem,
            name: String,
            year: Int?,
        ) {
            _state.update { it.copy(searchResults = ListState.Loading) }
            viewModelScope.launchIO {
                val result =
                    try {
                        val providerIds = item.data.providerIds
                        val results =
                            when (item.type) {
                                BaseItemKind.MOVIE -> {
                                    api.itemLookupApi
                                        .getMovieRemoteSearchResults(
                                            MovieInfoRemoteSearchQuery(
                                                searchInfo =
                                                    MovieInfo(
                                                        name = name,
                                                        year = year,
                                                        providerIds = providerIds,
                                                        isAutomated = false,
                                                    ),
                                                itemId = item.id,
                                                includeDisabledProviders = false,
                                            ),
                                        ).content
                                }

                                BaseItemKind.SERIES -> {
                                    api.itemLookupApi
                                        .getSeriesRemoteSearchResults(
                                            SeriesInfoRemoteSearchQuery(
                                                searchInfo =
                                                    SeriesInfo(
                                                        name = name,
                                                        year = year,
                                                        providerIds = providerIds,
                                                        isAutomated = false,
                                                    ),
                                                itemId = item.id,
                                                includeDisabledProviders = false,
                                            ),
                                        ).content
                                }

                                BaseItemKind.BOX_SET -> {
                                    api.itemLookupApi
                                        .getBoxSetRemoteSearchResults(
                                            BoxSetInfoRemoteSearchQuery(
                                                searchInfo =
                                                    BoxSetInfo(
                                                        name = name,
                                                        year = year,
                                                        providerIds = providerIds,
                                                        isAutomated = false,
                                                    ),
                                                itemId = item.id,
                                                includeDisabledProviders = false,
                                            ),
                                        ).content
                                }

                                else -> {
                                    emptyList()
                                }
                            }
                        ListState.Success(results)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error searching for %s", item.id)
                        ListState.Error(ex.localizedMessage)
                    }
                _state.update { it.copy(searchResults = result) }
            }
        }

        fun applyMatch(
            itemId: UUID,
            result: RemoteSearchResult,
            replaceImages: Boolean,
        ) {
            viewModelScope.launchIO {
                runAndReport(R.string.metadata_match_applied) {
                    api.itemLookupApi.applySearchCriteria(
                        itemId = itemId,
                        replaceAllImages = replaceImages,
                        data = result,
                    )
                }
            }
        }

        fun loadCurrentImages(itemId: UUID) {
            _state.update { it.copy(currentImages = ListState.Loading) }
            viewModelScope.launchIO {
                val result =
                    try {
                        ListState.Success(api.imageApi.getItemImageInfos(itemId).content)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error loading images for %s", itemId)
                        ListState.Error(ex.localizedMessage)
                    }
                _state.update { it.copy(currentImages = result) }
            }
        }

        fun loadRemoteImages(
            itemId: UUID,
            type: ImageType,
        ) {
            _state.update { it.copy(remoteImages = ListState.Loading) }
            viewModelScope.launchIO {
                val result =
                    try {
                        val images =
                            api.remoteImageApi
                                .getRemoteImages(itemId = itemId, type = type, includeAllLanguages = false)
                                .content.images
                                .orEmpty()
                        ListState.Success(images)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error loading remote images for %s", itemId)
                        ListState.Error(ex.localizedMessage)
                    }
                _state.update { it.copy(remoteImages = result) }
            }
        }

        /**
         * Download the chosen provider image to the item. For backdrops this adds an image,
         * otherwise it replaces the current one.
         */
        fun setImage(
            itemId: UUID,
            type: ImageType,
            url: String,
        ) {
            viewModelScope.launchIO {
                runAndReport(R.string.image_saved) {
                    api.remoteImageApi.downloadRemoteImage(itemId = itemId, type = type, imageUrl = url)
                }
                loadCurrentImages(itemId)
            }
        }

        fun deleteImage(
            itemId: UUID,
            image: ImageInfo,
        ) {
            viewModelScope.launchIO {
                runAndReport(R.string.image_removed) {
                    api.imageApi.deleteItemImage(itemId = itemId, imageType = image.imageType, imageIndex = image.imageIndex)
                }
                loadCurrentImages(itemId)
            }
        }

        /**
         * Run a request, then show a toast with [successMessage] or the error
         */
        private suspend fun runAndReport(
            successMessage: Int,
            block: suspend () -> Unit,
        ) {
            try {
                block.invoke()
                showToast(context, context.getString(successMessage))
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error managing metadata")
                showToast(context, context.getString(R.string.error) + ": " + ex.localizedMessage)
            }
        }
    }
