package com.github.damontecres.wholphin.services.tvprovider

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.ExceptionHandler
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import timber.log.Timber
import javax.inject.Inject

@ActivityScoped
class TvProviderSchedulerService
    @Inject
    constructor(
        @param:ActivityContext private val context: Context,
        private val serverRepository: ServerRepository,
        private val tvProviderService: TvProviderService,
    ) {
        private val activity = (context as AppCompatActivity)
        private val workManager = WorkManager.getInstance(context)

        init {
            serverRepository.current.observe(activity) { user ->
                workManager.cancelUniqueWork(TvProviderWorker.WORK_NAME)
                if (user != null) {
                    activity.lifecycleScope.launchIO(ExceptionHandler()) {
                        val supportsTvProvider =
                            // TODO <=25 has limited support
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                        if (supportsTvProvider) {
                            Timber.i("Scheduling TvProviderWorker for ${user.user}")
                            workManager.enqueue(
                                OneTimeWorkRequestBuilder<TvProviderWorker>()
                                    .setInputData(
                                        workDataOf(
                                            TvProviderWorker.PARAM_USER_ID to user.user.id.toString(),
                                        ),
                                    ).build(),
                            )
//                            workManager
//                                .enqueueUniquePeriodicWork(
//                                    uniqueWorkName = TvProviderWorker.WORK_NAME,
//                                    existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
//                                    request =
//                                        PeriodicWorkRequestBuilder<TvProviderWorker>(
//                                            repeatInterval = 2.hours.toJavaDuration(),
//                                        ).setBackoffCriteria(
//                                            BackoffPolicy.EXPONENTIAL,
//                                            15.minutes.toJavaDuration(),
//                                        ).setInputData(
//                                            workDataOf(
//                                                TvProviderWorker.PARAM_USER_ID to user.user.id.toString(),
//                                            ),
//                                        ).build(),
//                                ).await()
                        }
                    }
                }
            }
        }
    }
