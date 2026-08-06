package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.data.SeriesTrackChoiceDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.data.model.SeriesTrackChoiceType
import com.github.damontecres.wholphin.services.StreamChoiceService
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Test

class TestSeriesTrackChoice {
    @Test
    fun `Test StreamChoiceService call correct dao function`() =
        runTest {
            val mockServerRepository = mockk<ServerRepository>()
            every { mockServerRepository.currentUser } returns JellyfinUser(1, UUID.randomUUID(), null, UUID.randomUUID(), null)
            val mockSeriesTrackChoiceDao = mockk<SeriesTrackChoiceDao>(relaxed = true)

            val streamChoiceService = StreamChoiceService(mockServerRepository, mockSeriesTrackChoiceDao)

            val seriesId = UUID.randomUUID()
            val seasonId = UUID.randomUUID()

            // Has both series & season
            BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.EPISODE,
                seriesId = seriesId,
                parentId = seasonId,
            ).let { item ->
                streamChoiceService.getSeriesTrackChoices(item, MediaStreamType.SUBTITLE)
                coVerify(exactly = 1) {
                    mockSeriesTrackChoiceDao.get(any(), seriesId = seriesId, seasonId = seasonId, type = SeriesTrackChoiceType.SUBTITLE)
                }
            }

            // Has only series
            BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.EPISODE,
                seriesId = seriesId,
                parentId = null,
            ).let { item ->
                streamChoiceService.getSeriesTrackChoices(item, MediaStreamType.SUBTITLE)
                coVerify(exactly = 1) {
                    mockSeriesTrackChoiceDao.getBySeriesId(any(), seriesId = seriesId, type = SeriesTrackChoiceType.SUBTITLE)
                }
            }

            // Has only season
            BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.EPISODE,
                seriesId = null,
                parentId = seasonId,
            ).let { item ->
                streamChoiceService.getSeriesTrackChoices(item, MediaStreamType.SUBTITLE)
                coVerify(exactly = 1) {
                    mockSeriesTrackChoiceDao.getBySeasonId(any(), seasonId = seasonId, type = SeriesTrackChoiceType.SUBTITLE)
                }
            }
        }
}
