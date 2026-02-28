package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.showToast
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
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
        private val userPreferencesService: UserPreferencesService,
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

        suspend fun canDelete(item: BaseItem): Boolean {
            Timber.v("canDelete %s: %s", item.id, item.canDelete)
            val enabled =
                userPreferencesService
                    .getCurrent()
                    .appPreferences.interfacePreferences.enableMediaManagement
            return enabled &&
                item.canDelete &&
                if (item.type == BaseItemKind.RECORDING) {
                    serverRepository.currentUserDto.value
                        ?.policy
                        ?.enableLiveTvManagement == true
                } else {
                    true
                }
        }

        suspend fun deleteItem(item: BaseItem): DeleteResult {
            try {
                Timber.i("Deleting %s", item.id)
                // TODO enable
                api.libraryApi.deleteItem(item.id)
                _deletedItemFlow.emit(DeletedItem(item))
                return DeleteResult.Success
            } catch (ex: Exception) {
                Timber.e(ex, "Error deleting %s", item.id)
                return DeleteResult.Error(ex)
            }
        }
    }

data class DeletedItem(
    val item: BaseItem,
)

sealed interface DeleteResult {
    data object Success : DeleteResult

    data class Error(
        val ex: Exception,
    ) : DeleteResult
}

fun ViewModel.deleteItem(
    context: Context,
    mediaManagementService: MediaManagementService,
    item: BaseItem,
    onSuccess: () -> Unit = {},
) = viewModelScope.launchIO {
    when (val r = mediaManagementService.deleteItem(item)) {
        is DeleteResult.Error -> {
            showToast(
                context,
                "Error deleting item: ${r.ex.localizedMessage}",
            )
        }

        DeleteResult.Success -> {
            showToast(context, "Deleted")
            onSuccess.invoke()
        }
    }
}
