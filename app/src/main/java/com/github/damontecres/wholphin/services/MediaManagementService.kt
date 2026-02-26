package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.showToast
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaManagementService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) {
        private val _deletedItemFlow =
            MutableSharedFlow<DeletedItem>(
                replay = 1,
                extraBufferCapacity = 0,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )

        /**
         * Listen for recently deleted items. Useful for ViewModels to react and refresh data
         */
        val deletedItemFlow: SharedFlow<DeletedItem> = _deletedItemFlow

        fun canDelete(item: BaseItem): Boolean =
            // TODO check app preferences
            item.canDelete &&
                if (item.type == BaseItemKind.RECORDING) {
                    serverRepository.currentUserDto.value
                        ?.policy
                        ?.enableLiveTvManagement == true
                } else {
                    true
                }

        suspend fun deleteItem(item: BaseItem): Boolean {
            try {
                // TODO enable
//                api.libraryApi.deleteItem(item.id)
                _deletedItemFlow.emit(DeletedItem(item))
                return true
            } catch (ex: Exception) {
                Timber.e(ex, "Error deleting %s", item.id)
                showToast(context, "Could not delete item")
                return false
            }
        }
    }

data class DeletedItem(
    val item: BaseItem,
)
