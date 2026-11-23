package com.github.damontecres.wholphin.ui.preferences.card

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.UserItemDataDto
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class CardPreferencesViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        val preferenceDataStore: DataStore<AppPreferences>,
    ) : ViewModel() {
        private val _state = MutableStateFlow(CardPreferencesState())
        val state = _state.asStateFlow()

        init {
            viewModelScope.launchIO {
                _state.update { it.copy(loading = LoadingState.Loading) }
                val movieJob = getExample(BaseItemKind.MOVIE, playedPercentage = -1.0)
                val movie2Job = getExample(BaseItemKind.MOVIE, versions = 3, favorite = false)
                val tvShowJob = getExample(BaseItemKind.SERIES)
                val tvSeasonJob = getExample(BaseItemKind.SEASON, favorite = false)
                val collectionJob = getExample(BaseItemKind.BOX_SET, null)
                val episode = getExample(BaseItemKind.EPISODE, favorite = false)

                movieJob.await().let { item -> _state.update { it.copy(movie = item) } }
                movie2Job.await().let { item -> _state.update { it.copy(movie2 = item) } }
                tvShowJob.await().let { item -> _state.update { it.copy(tvShow = item) } }
                tvSeasonJob.await().let { item -> _state.update { it.copy(tvSeason = item) } }
                collectionJob.await().let { item -> _state.update { it.copy(collection = item) } }
                episode.await().let { item -> _state.update { it.copy(episode = item) } }
                _state.update { it.copy(loading = LoadingState.Success) }
            }
        }

        /**
         * Gets an example of the specified type, optionally overriding some of the properties
         */
        private fun CoroutineScope.getExample(
            type: BaseItemKind,
            imageType: ImageType? = ImageType.PRIMARY,
            playedPercentage: Double = Random.nextDouble(66.0, 80.0),
            favorite: Boolean = true,
            versions: Int = 1,
        ): Deferred<BaseItem> =
            async {
                val item =
                    api.libraryApi
                        .getItems(
                            includeItemTypes = listOf(type),
                            imageTypes = imageType?.let { listOf(imageType) },
                            sortBy = listOf(ItemSortBy.RANDOM),
                            fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
                            limit = 1,
                        ).content.items
                        .firstOrNull()
                        // Fallback if no item with an image found
                        ?: BaseItemDto(
                            id = UUID.randomUUID(),
                            type = type,
                            primaryImageAspectRatio =
                                if (type == BaseItemKind.EPISODE) {
                                    AspectRatios.WIDE.toDouble()
                                } else {
                                    AspectRatios.TALL.toDouble()
                                },
                        )

                item.let {
                    val newItem =
                        it.copy(
                            mediaSourceCount = versions,
                            userData =
                                UserItemDataDto(
                                    playedPercentage = playedPercentage,
                                    playbackPositionTicks = 0L,
                                    playCount = 2,
                                    isFavorite = favorite,
                                    played = true,
                                    key = it.id.toString(),
                                    itemId = it.id,
                                    unplayedItemCount = Random.nextInt(3, 10),
                                ),
                        )
                    BaseItem(newItem, useSeriesForPrimary = false)
                }
            }
    }

data class CardPreferencesState(
    val loading: LoadingState = LoadingState.Pending,
    val movie: BaseItem? = null,
    val movie2: BaseItem? = null,
    val collection: BaseItem? = null,
    val tvShow: BaseItem? = null,
    val tvSeason: BaseItem? = null,
    val episode: BaseItem? = null,
)
