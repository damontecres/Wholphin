package com.github.damontecres.wholphin.test

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.updateHomePagePreferences
import com.github.damontecres.wholphin.services.DatePlayedService
import com.github.damontecres.wholphin.services.LatestNextUpService
import com.github.damontecres.wholphin.services.mockQueryResult
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.operations.TvShowsApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class NextUpTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private val mockTvShowsApi = mockk<TvShowsApi>()
    private val mockApi = mockk<ApiClient>(relaxed = true)
    private val mockContext = mockk<Context>()
    private val mockDatePlayedService = mockk<DatePlayedService>()

    private val latestNextUpService =
        LatestNextUpService(mockContext, mockApi, mockDatePlayedService)

    @Before
    fun setUp() {
        every { mockApi.tvShowsApi } returns mockTvShowsApi
    }

    @Test
    fun `Test max 30 days in next up`() =
        runTest {
            val maxDays = 30
            val nextUpSlot = CapturingSlot<GetNextUpRequest>()
            coEvery { mockTvShowsApi.getNextUp(capture(nextUpSlot)) } returns mockQueryResult()
            latestNextUpService.getNextUp(
                userId = UUID.randomUUID(),
                limit = 10,
                enableRewatching = true,
                enableResumable = true,
                maxDays = maxDays,
            )
            Assert.assertEquals(10, nextUpSlot.captured.limit)
            val expected = LocalDate.now().minusDays(maxDays.toLong())
            Assert.assertEquals(expected, nextUpSlot.captured.nextUpDateCutoff?.toLocalDate())
        }

    @Test
    fun `Test no limit in next up`() =
        runTest {
            val nextUpSlot = CapturingSlot<GetNextUpRequest>()
            coEvery { mockTvShowsApi.getNextUp(capture(nextUpSlot)) } returns mockQueryResult()
            latestNextUpService.getNextUp(
                userId = UUID.randomUUID(),
                limit = 10,
                enableRewatching = true,
                enableResumable = true,
                maxDays = -1,
            )
            Assert.assertEquals(10, nextUpSlot.captured.limit)
            Assert.assertNull(nextUpSlot.captured.nextUpDateCutoff)
        }

    @Test
    fun `Test storing preference`() {
        AppPreference.MaxDaysNextUp.setter.invoke(AppPreferences.getDefaultInstance(), 0).let {
            Assert.assertEquals(7, it.homePagePreferences.maxDaysNextUp)
        }

        AppPreference.MaxDaysNextUp.setter
            .invoke(
                AppPreferences.getDefaultInstance(),
                AppPreference.MaxDaysNextUpOptions.lastIndex.toLong(),
            ).let {
                Assert.assertEquals(365, it.homePagePreferences.maxDaysNextUp)
            }

        AppPreference.MaxDaysNextUp.setter
            .invoke(AppPreferences.getDefaultInstance(), 3)
            .let {
                Assert.assertEquals(60, it.homePagePreferences.maxDaysNextUp)
            }

        AppPreference.MaxDaysNextUp.setter
            .invoke(
                AppPreferences.getDefaultInstance(),
                AppPreference.MaxDaysNextUpOptions.lastIndex + 1L,
            ).let {
                Assert.assertEquals(-1, it.homePagePreferences.maxDaysNextUp)
            }
    }

    @Test
    fun `Test getting preference`() {
        AppPreferences
            .getDefaultInstance()
            .updateHomePagePreferences { maxDaysNextUp = 7 }
            .let {
                val result = AppPreference.MaxDaysNextUp.getter.invoke(it)
                Assert.assertEquals(0, result)
            }

        AppPreferences
            .getDefaultInstance()
            .updateHomePagePreferences { maxDaysNextUp = 60 }
            .let {
                val result = AppPreference.MaxDaysNextUp.getter.invoke(it)
                Assert.assertEquals(3, result)
            }

        AppPreferences
            .getDefaultInstance()
            .updateHomePagePreferences { maxDaysNextUp = -1 }
            .let {
                val result = AppPreference.MaxDaysNextUp.getter.invoke(it)
                Assert.assertEquals(AppPreference.MaxDaysNextUpOptions.lastIndex + 1L, result)
            }
    }
}
