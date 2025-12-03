package com.github.damontecres.wholphin.services.tvprovider

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.LatestNextUpService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber

@HiltWorker
class TvProviderWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val serverRepository: ServerRepository,
        private val preferences: DataStore<AppPreferences>,
        private val latestNextUpService: LatestNextUpService,
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            Timber.d("Start")
            val currentUser = serverRepository.current.value
            if (currentUser == null) {
                Timber.w("No user found during run")
                return Result.failure() // TODO Retry?
            }
            val userId = inputData.getString(PARAM_USER_ID)?.toUUIDOrNull() ?: return Result.failure()
            val prefs = preferences.data.firstOrNull() ?: AppPreferences.getDefaultInstance()

            Timber.d("Completed successfully")
            return Result.success()
        }

        companion object {
            const val WORK_NAME = "com.github.damontecres.wholphin.services.tvprovider.TvProviderWorker"
            const val PARAM_USER_ID = "userId"
        }
    }
