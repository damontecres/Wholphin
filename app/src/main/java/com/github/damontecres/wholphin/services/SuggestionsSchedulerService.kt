package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.util.ExceptionHandler
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

@ActivityScoped
class SuggestionsSchedulerService
    @Inject
    constructor(
        @param:ActivityContext private val context: Context,
        private val serverRepository: ServerRepository,
        private val cache: SuggestionsCache,
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
                    workManager.cancelUniqueWork(SuggestionsWorker.WORK_NAME)
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

            val inputData =
                workDataOf(
                    SuggestionsWorker.PARAM_USER_ID to userId.toString(),
                    SuggestionsWorker.PARAM_SERVER_ID to serverId.toString(),
                )

            if (cache.isEmpty()) {
                Timber.i("Suggestions cache empty, scheduling immediate fetch")
                workManager.enqueue(
                    OneTimeWorkRequestBuilder<SuggestionsWorker>()
                        .setConstraints(constraints)
                        .setInputData(inputData)
                        .build(),
                )
            }

            Timber.i("Scheduling periodic SuggestionsWorker")
            workManager.enqueueUniquePeriodicWork(
                uniqueWorkName = SuggestionsWorker.WORK_NAME,
                existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
                request =
                    PeriodicWorkRequestBuilder<SuggestionsWorker>(
                        repeatInterval = 12.hours.toJavaDuration(),
                    ).setConstraints(constraints)
                        .setBackoffCriteria(
                            BackoffPolicy.EXPONENTIAL,
                            15.minutes.toJavaDuration(),
                        ).setInputData(inputData)
                        .build(),
            )
        }
    }
