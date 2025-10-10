package com.github.damontecres.dolphin.ui.detail

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.data.model.DolphinModel
import com.github.damontecres.dolphin.data.model.convertModel
import com.github.damontecres.dolphin.util.LoadingExceptionHandler
import com.github.damontecres.dolphin.util.LoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber
import java.util.UUID

/**
 * Basic [ViewModel] for a single fetchable item from the API
 */
abstract class ItemViewModel<T : DolphinModel>(
    val api: ApiClient,
) : ViewModel() {
    val item = MutableLiveData<BaseItem?>(null)
    val model = MutableLiveData<T?>(null)

    suspend fun fetchItem(
        itemId: UUID,
        potential: BaseItem?,
    ): BaseItem? =
        withContext(Dispatchers.IO) {
//            val fetchedItem =
//                when {
//                    item.value == null && potential?.id == itemId -> potential
//                    item.value?.id == itemId -> item.value
//                    else -> {
//                        val it = api.userLibraryApi.getItem(itemId).content
//                        BaseItem.from(it, api)
//                    }
//                }
            val it = api.userLibraryApi.getItem(itemId).content
            val fetchedItem = BaseItem.from(it, api)
            return@withContext fetchedItem?.let {
                val modelInstance = convertModel(fetchedItem.data, api)
                withContext(Dispatchers.Main) {
                    item.value = fetchedItem
                    model.value = modelInstance as T
                }
                fetchedItem
            }
        }

    fun imageUrl(
        itemId: UUID,
        type: ImageType,
    ): String? = api.imageApi.getItemImageUrl(itemId, type)
}

/**
 * Extends [ItemViewModel] to include a loading state tracking when the item has been fetched or if an error occurred
 */
abstract class LoadingItemViewModel<T : DolphinModel>(
    api: ApiClient,
) : ItemViewModel<T>(api) {
    val loading = MutableLiveData<LoadingState>(LoadingState.Loading)

    open fun init(
        itemId: UUID,
        potential: BaseItem?,
    ): Job? =
        viewModelScope.launch(
            LoadingExceptionHandler(
                loading,
                "Error loading item $itemId",
            ) + Dispatchers.IO,
        ) {
            try {
                val fetchedItem = api.userLibraryApi.getItem(itemId).content
                val modelInstance = convertModel(fetchedItem, api)
                withContext(Dispatchers.Main) {
                    item.value = BaseItem.from(fetchedItem, api)
                    model.value = modelInstance as T
                    loading.value = LoadingState.Success
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load item $itemId")
                item.value = null
                loading.value = LoadingState.Error("Error loading item $itemId", e)
            }
        }
}
