package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.lifecycleScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.util.ExceptionHandler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

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

        companion object {
            const val WORK_NAME = "com.github.damontecres.wholphin.services.LatestNextUpWorker"
            const val PARAM_USER_ID = "userId"
            const val PARAM_SERVER_ID = "serverId"
        }
    }

@ActivityScoped
class LatestNextUpSchedulerService
    @Inject
    constructor(
        @param:ActivityContext private val context: Context,
        private val serverRepository: ServerRepository,
        private val workManager: WorkManager,
    ) {
        private val activity =
            (context as? AppCompatActivity)
                ?: throw IllegalStateException(
                    "SuggestionsSchedulerService requires an AppCompatActivity context, but received: ${context::class.java.name}",
                )

        // Exposed for testing
        internal var dispatcher: CoroutineDispatcher = Dispatchers.IO

        init {
            serverRepository.current.observe(activity) { user ->
                Timber.v("New user %s", user?.user?.id)
                if (user == null) {
                    workManager.cancelUniqueWork(LatestNextUpWorker.WORK_NAME)
                } else {
                    activity.lifecycleScope.launch(dispatcher + ExceptionHandler()) {
                        scheduleWork(user.user.id, user.server.id)
                    }
                }
            }
        }

        private suspend fun scheduleWork(
            userId: UUID,
            serverId: UUID,
        ) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            val periodicWorkRequestBuilder =
                PeriodicWorkRequestBuilder<LatestNextUpWorker>(
                    repeatInterval = 4.hours.toJavaDuration(),
                ).setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15.minutes.toJavaDuration(),
                ).setConstraints(constraints)
                    .setInputData(
                        workDataOf(
                            LatestNextUpWorker.PARAM_USER_ID to userId.toString(),
                            LatestNextUpWorker.PARAM_SERVER_ID to serverId.toString(),
                        ),
                    )
            Timber.i("Scheduling periodic LatestNextUpWorker")

            workManager.enqueueUniquePeriodicWork(
                uniqueWorkName = LatestNextUpWorker.WORK_NAME,
                existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.REPLACE,
                request = periodicWorkRequestBuilder.build(),
            )
        }
    }
