package com.github.damontecres.wholphin.services.tvprovider

import android.content.ContentUris
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.hilt.work.HiltWorker
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.NavDrawerItemRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.LatestNextUpService
import com.github.damontecres.wholphin.ui.nav.ServerNavDrawerItem
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber

@HiltWorker
class TvProviderWorker
    @AssistedInject
    constructor(
        @Assisted private val context: Context,
        @Assisted workerParams: WorkerParameters,
        private val serverRepository: ServerRepository,
        private val preferences: DataStore<AppPreferences>,
        private val navDrawerItemRepository: NavDrawerItemRepository,
        private val latestNextUpService: LatestNextUpService,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            Timber.d("Start")
            val serverId =
                inputData.getString(PARAM_SERVER_ID)?.toUUIDOrNull() ?: return Result.failure()
            val userId =
                inputData.getString(PARAM_USER_ID)?.toUUIDOrNull() ?: return Result.failure()

            val sharedPrefs =
                context.getSharedPreferences(
                    "tvprovider",
                    Context.MODE_PRIVATE,
                )

            var currentUser = serverRepository.current.value
            if (currentUser == null) {
                serverRepository.restoreSession(serverId, userId)
                currentUser = serverRepository.current.value
            }
            if (currentUser == null) {
                Timber.w("No user found during run")
                return Result.failure()
            }
            val prefs = preferences.data.firstOrNull() ?: AppPreferences.getDefaultInstance()
            val resume = latestNextUpService.getResume(userId, 5, true)
            val nextUp =
                latestNextUpService.getNextUp(
                    userId,
                    5,
                    prefs.homePagePreferences.enableRewatchingNextUp,
                    false,
                )
            val combined =
                if (prefs.homePagePreferences.combineContinueNext) {
                    latestNextUpService.buildCombined(resume, nextUp)
                } else {
                    null
                }
            val includedIds =
                navDrawerItemRepository
                    .getFilteredNavDrawerItems(navDrawerItemRepository.getNavDrawerItems())
                    .filter { it is ServerNavDrawerItem }
                    .map { (it as ServerNavDrawerItem).itemId }
            val latest = latestNextUpService.getLatest(currentUser.userDto, 10, includedIds)
            val latestResults = latestNextUpService.loadLatest(latest)

            val channels =
                buildMap {
                    if (combined != null) {
                        put(
                            "next_up",
                            HomeRowLoadingState.Success(
                                context.getString(R.string.next_up),
                                combined,
                            ),
                        )
                    } else {
                        put(
                            "resume",
                            HomeRowLoadingState.Success(context.getString(R.string.resume), resume),
                        )
                        put(
                            "next_up",
                            HomeRowLoadingState.Success(context.getString(R.string.next_up), nextUp),
                        )
                    }
                    latestResults
                        .forEach {
                            (it as? HomeRowLoadingState.Success)?.let {
                                put(it.title, it.items)
                            }
                        }
                }
            channels.forEach { (title, items) ->
                val channel =
                    Channel
                        .Builder()
                        .setDisplayName(title)
                        .setType(TvContractCompat.Channels.TYPE_PREVIEW)
                        .build()

                // Create
                val channelUri =
                    context.contentResolver.insert(
                        TvContractCompat.Channels.CONTENT_URI,
                        channel.toContentValues(),
                    )
                // Update
                context.contentResolver.update(
                    TvContractCompat.buildChannelUri(channelId),
                    channel.toContentValues(),
                    null,
                    null,
                )

                if (channelUri != null) {
                    val channelId = ContentUris.parseId(channelUri)
                    // TODO save this
                }
            }

            Timber.d("Completed successfully")
            return Result.success()
        }

        companion object {
            const val WORK_NAME = "com.github.damontecres.wholphin.services.tvprovider.TvProviderWorker"
            const val PARAM_USER_ID = "userId"
            const val PARAM_SERVER_ID = "serverId"
        }
    }
