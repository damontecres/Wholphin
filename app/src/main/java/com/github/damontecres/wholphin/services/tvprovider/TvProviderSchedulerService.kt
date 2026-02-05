package com.github.damontecres.wholphin.services.tvprovider

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.ExceptionHandler
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

@ActivityScoped
class TvProviderSchedulerService
    @Inject
    constructor(
        @param:ActivityContext private val context: Context,
        private val serverRepository: ServerRepository,
        private val workManager: WorkManager,
    ) {
        private val activity = (context as AppCompatActivity)

        private val supportsTvProvider =
            // TODO <=25 has limited support
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

        init {
            serverRepository.current.observe(activity) { user ->
                workManager.cancelUniqueWork(TvProviderWorker.WORK_NAME)
                if (supportsTvProvider) {
                    if (user != null) {
                        activity.lifecycleScope.launchIO(ExceptionHandler()) {
                            Timber.i("Scheduling TvProviderWorker for ${user.user}")
                            workManager
                                .enqueueUniquePeriodicWork(
                                    uniqueWorkName = TvProviderWorker.WORK_NAME,
                                    existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
                                    request =
                                        PeriodicWorkRequestBuilder<TvProviderWorker>(
                                            repeatInterval = 1.hours.toJavaDuration(),
                                        ).setBackoffCriteria(
                                            BackoffPolicy.LINEAR,
                                            15.minutes.toJavaDuration(),
                                        ).setInputData(
                                            workDataOf(
                                                TvProviderWorker.PARAM_USER_ID to user.user.id.toString(),
                                                TvProviderWorker.PARAM_SERVER_ID to user.server.id.toString(),
                                            ),
                                        ).build(),
                                ).await()
                        }
                    }
                }
            }
        }

        fun launchOneTimeRefresh() {
            if (supportsTvProvider) {
                activity.lifecycleScope.launchIO(ExceptionHandler()) {
                    serverRepository.current.value?.let { user ->
                        Timber.i("Scheduling on-time TvProviderWorker for ${user.user}")
                        workManager.enqueue(
                            OneTimeWorkRequestBuilder<TvProviderWorker>()
                                .setInputData(
                                    workDataOf(
                                        TvProviderWorker.PARAM_USER_ID to user.user.id.toString(),
                                        TvProviderWorker.PARAM_SERVER_ID to user.server.id.toString(),
                                    ),
                                ).build(),
                        )
                    }
                }
            }
        }
    }
