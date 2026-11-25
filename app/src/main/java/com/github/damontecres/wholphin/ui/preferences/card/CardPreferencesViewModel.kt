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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import timber.log.Timber
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

                try {
                    val jobs = dataTypes.map { getExample(it) }
                    val results = jobs.awaitAll()
                    val data = results.associateBy { it.first().type }

                    _state.update { it.copy(loading = LoadingState.Success, data = data) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching data")
                    _state.update { it.copy(loading = LoadingState.Error(ex)) }
                }
            }
        }

        /**
         * Gets an example of the specified type, optionally overriding some of the properties
         */
        private fun CoroutineScope.getExample(
            type: BaseItemKind,
            imageType: ImageType? = ImageType.PRIMARY,
        ): Deferred<List<BaseItem>> =
            async {
                val items =
                    api.libraryApi
                        .getItems(
                            includeItemTypes = listOf(type),
                            imageTypes = imageType?.let { listOf(imageType) },
                            sortBy = listOf(ItemSortBy.RANDOM),
                            fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
                            limit = 3,
                        ).content.items
                        .toMutableList()
                        .apply {
                            // Ensure there are 3 items
                            while (size < 3) {
                                add(
                                    BaseItemDto(
                                        id = UUID.randomUUID(),
                                        name = "Example",
                                        type = type,
                                        indexNumber = Random.nextInt(3, 12),
                                        parentIndexNumber = Random.nextInt(1, 6),
                                        primaryImageAspectRatio =
                                            if (type == BaseItemKind.EPISODE) {
                                                AspectRatios.WIDE.toDouble()
                                            } else {
                                                AspectRatios.TALL.toDouble()
                                            },
                                    ),
                                )
                            }
                        }
                val item1 = items[0].copy(userData = unwatchedUserData())
                val item2 = items[1].copy(userData = inProgressUserData())
                val item3 = items[2].copy(userData = watchedUserData())
                listOf(
                    BaseItem(item1),
                    BaseItem(item2),
                    BaseItem(item3),
                )
            }

        private fun unwatchedUserData() =
            UserItemDataDto(
                playedPercentage = -1.0,
                playbackPositionTicks = 0L,
                playCount = 0,
                isFavorite = false,
                played = false,
                key = "",
                itemId = UUID.randomUUID(),
                unplayedItemCount = Random.nextInt(3, 10),
            )

        private fun watchedUserData() =
            UserItemDataDto(
                playedPercentage = -1.0,
                playbackPositionTicks = 0L,
                playCount = 2,
                isFavorite = true,
                played = true,
                key = "",
                itemId = UUID.randomUUID(),
                unplayedItemCount = 0,
            )

        private fun inProgressUserData() =
            UserItemDataDto(
                playedPercentage = Random.nextDouble(66.0, 80.0),
                playbackPositionTicks = 0L,
                playCount = 0,
                isFavorite = true,
                played = false,
                key = "",
                itemId = UUID.randomUUID(),
                unplayedItemCount = Random.nextInt(3, 10),
            )

        companion object {
            val dataTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.BOX_SET, BaseItemKind.SERIES, BaseItemKind.SEASON, BaseItemKind.EPISODE)
        }
    }

data class CardPreferencesState(
    val loading: LoadingState = LoadingState.Pending,
    val data: Map<BaseItemKind, List<BaseItem>> = emptyMap(),
)
