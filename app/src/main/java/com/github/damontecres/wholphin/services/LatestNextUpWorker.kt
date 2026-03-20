package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.tvprovider.TvProviderWorker.Companion.PARAM_SERVER_ID
import com.github.damontecres.wholphin.services.tvprovider.TvProviderWorker.Companion.PARAM_USER_ID
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber

@HiltWorker
class LatestNextUpWorker
    @AssistedInject
    constructor(
        @Assisted private val context: Context,
        @Assisted workerParams: WorkerParameters,
        private val serverRepository: ServerRepository,
        private val api: ApiClient,
        private val latestNextUpService: LatestNextUpService,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            Timber.d("Start")
            val serverId =
                inputData.getString(PARAM_SERVER_ID)?.toUUIDOrNull() ?: return Result.failure()
            val userId =
                inputData.getString(PARAM_USER_ID)?.toUUIDOrNull() ?: return Result.failure()

            try {
                if (api.baseUrl.isNullOrBlank() || api.accessToken.isNullOrBlank()) {
                    // Not active
                    var currentUser = serverRepository.current.value
                    if (currentUser == null) {
                        serverRepository.restoreSession(serverId, userId)
                        currentUser = serverRepository.current.value
                    }
                    if (currentUser == null) {
                        Timber.w("No user found during run")
                        return Result.failure()
                    }
                }
                latestNextUpService.updateRemovedFromNextUp(userId)
                return Result.success()
            } catch (ex: Exception) {
                Timber.e(ex, "Error during updateRemovedFromNextUp")
                return Result.retry()
            }
        }
    }
