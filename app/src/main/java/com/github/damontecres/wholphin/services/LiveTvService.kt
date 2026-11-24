package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.toServerString
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.model.api.RecordingStatus
import org.jellyfin.sdk.model.api.TimerInfoDto
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Functions for recording live tv programs
 */
@Singleton
class LiveTvService
    @Inject
    constructor(
        private val api: ApiClient,
    ) {
        /**
         * Fetch a tv program by ID
         */
        suspend fun fetchProgramForDialog(programId: UUID): BaseItem {
            val result =
                api.liveTvApi
                    .getProgram(programId.toServerString())
                    .content
                    .let { BaseItem(it) }
            return result
        }

        suspend fun fetchRecordingForProgram(program: BaseItem): BaseItem? {
            val timerId = program.data.timerId
            val channelId = program.data.channelId
            return if (program.data.status == RecordingStatus.IN_PROGRESS.serialName &&
                timerId != null &&
                channelId != null
            ) {
                // Active recording
                val recordings by api.liveTvApi.getRecordings(
                    channelId = channelId.toString(),
                    isInProgress = true,
                )
                recordings.items.firstOrNull { it.timerId == timerId }?.let { BaseItem(it) }
            } else {
                null
            }
        }

        /**
         * Cancel a recording by [timerId]
         *
         * @param series whether the timerId is a series recording or not
         * @param timerId the recording ID
         */
        suspend fun cancelRecording(
            series: Boolean,
            timerId: String?,
        ): Boolean =
            if (timerId != null) {
                if (series) {
                    api.liveTvApi.cancelSeriesTimer(timerId)
                } else {
                    api.liveTvApi.cancelTimer(timerId)
                }
                true
            } else {
                false
            }

        /**
         * Record tv program
         *
         * @param programId the tv program ID
         * @param series whether to set up series recording or just a single recording
         */
        suspend fun record(
            programId: UUID,
            series: Boolean,
        ) {
            val d by api.liveTvApi.getDefaultTimer(programId.toServerString())
            if (series) {
                api.liveTvApi.createSeriesTimer(d)
            } else {
                val payload =
                    TimerInfoDto(
                        id = d.id,
                        type = d.type,
                        serverId = d.serverId,
                        externalId = d.externalId,
                        channelId = d.channelId,
                        externalChannelId = d.externalChannelId,
                        channelName = d.channelName,
                        programId = d.programId,
                        externalProgramId = d.externalProgramId,
                        name = d.name,
                        overview = d.overview,
                        startDate = d.startDate,
                        endDate = d.endDate,
                        serviceName = d.serviceName,
                        priority = d.priority,
                        prePaddingSeconds = d.prePaddingSeconds,
                        postPaddingSeconds = d.postPaddingSeconds,
                        isPrePaddingRequired = d.isPrePaddingRequired,
                        isPostPaddingRequired = d.isPostPaddingRequired,
                        keepUntil = d.keepUntil,
                    )
                api.liveTvApi.createTimer(payload)
            }
        }
    }
