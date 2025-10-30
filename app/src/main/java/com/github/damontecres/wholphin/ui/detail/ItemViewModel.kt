package com.github.damontecres.wholphin.ui.detail

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
abstract class ItemViewModel(
    val api: ApiClient,
) : ViewModel() {
    val item = MutableLiveData<BaseItem?>(null)

    suspend fun fetchItem(
        itemId: UUID,
        potential: BaseItem?,
    ): BaseItem =
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
            return@withContext fetchedItem.let {
                withContext(Dispatchers.Main) {
                    item.value = fetchedItem
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
abstract class LoadingItemViewModel(
    api: ApiClient,
) : ItemViewModel(api) {
    val loading = MutableLiveData<LoadingState>(LoadingState.Pending)

    open fun init(
        itemId: UUID,
        potential: BaseItem?,
    ): Deferred<BaseItem?> =
        viewModelScope.async(
            LoadingExceptionHandler(
                loading,
                "Error loading item $itemId",
            ) + Dispatchers.IO,
        ) {
            loading.setValueOnMain(LoadingState.Loading)
            try {
                fetchAndSetItem(itemId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load item $itemId")
                withContext(Dispatchers.Main) {
                    item.value = null
                    loading.value = LoadingState.Error("Error loading item $itemId", e)
                }
                null
            }
        }

    open suspend fun fetchAndSetItem(itemId: UUID): BaseItem =
        withContext(Dispatchers.IO) {
            val fetchedItem = api.userLibraryApi.getItem(itemId).content
            val item = BaseItem.from(fetchedItem, api)
            withContext(Dispatchers.Main) {
                this@LoadingItemViewModel.item.value = item
                loading.value = LoadingState.Success
            }
            return@withContext item
        }
}
