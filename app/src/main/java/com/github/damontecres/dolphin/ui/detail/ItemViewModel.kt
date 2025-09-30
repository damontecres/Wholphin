package com.github.damontecres.dolphin.ui.detail

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.data.model.DolphinModel
import com.github.damontecres.dolphin.data.model.convertModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber
import java.util.UUID

abstract class ItemViewModel<T : DolphinModel>(
    val api: ApiClient,
) : ViewModel() {
    val item = MutableLiveData<BaseItem?>(null)
    val model = MutableLiveData<T?>(null)

    open fun init(
        itemId: UUID,
        potential: BaseItem?,
    ): Job? {
        if (item.value == null && potential?.id == itemId) {
            item.value = potential
            return null
        }
        if (item.value?.id == itemId) {
            return null
        }
        return viewModelScope.launch {
            try {
                val fetchedItem = api.userLibraryApi.getItem(itemId).content
                val modelInstance = convertModel(fetchedItem, api)
                item.value = BaseItem.from(fetchedItem, api)
                model.value = modelInstance as T
            } catch (e: Exception) {
                Timber.e(e, "Failed to load item $itemId")
                item.value = null
            }
        }
    }

    fun imageUrl(
        itemId: UUID,
        type: ImageType,
    ): String? = api.imageApi.getItemImageUrl(itemId, type)
}
