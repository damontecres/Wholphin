package com.github.damontecres.wholphin.ui.detail

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.toServerString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

/**
 * Basic [ViewModel] for a single fetchable item from the API
 */
abstract class ItemViewModel(
    val api: ApiClient,
) : ViewModel() {
    val item = MutableLiveData<BaseItem?>(null)
    lateinit var itemId: String
    var itemUuid: UUID? = null

    suspend fun fetchItem(itemId: UUID): BaseItem =
        withContext(Dispatchers.IO) {
            this@ItemViewModel.itemId = itemId.toServerString()
            this@ItemViewModel.itemUuid = itemId
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
