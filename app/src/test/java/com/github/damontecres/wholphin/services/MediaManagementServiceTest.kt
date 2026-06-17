package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.InterfacePreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.operations.LibraryApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.UserPolicy
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class MediaManagementServiceTest {
    private val testDispatcher = StandardTestDispatcher()

    private val mockContext = mockk<Context>()
    private val mockApiClient = mockk<ApiClient>()
    private val mockServerRepository = mockk<ServerRepository>()
    private val mockUserPreferencesService = mockk<UserPreferencesService>()

    private val mockLibraryApi = mockk<LibraryApi>()

    private val mediaManagementService =
        MediaManagementService(mockContext, mockApiClient, mockServerRepository, mockUserPreferencesService)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
//        mockkStatic(Dispatchers::class)
//        every { Dispatchers.IO } returns testDispatcher
//        every { Dispatchers.Default } returns testDispatcher

        every { mockApiClient.libraryApi } returns mockLibraryApi
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
//        unmockkStatic(Dispatchers::class)
    }

    private val movie =
        BaseItem(
            BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.MOVIE,
                canDelete = true,
            ),
        )
    private val recording =
        BaseItem(
            BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.RECORDING,
                canDelete = true,
            ),
        )

    private val movieCannotDelete =
        BaseItem(
            BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.MOVIE,
                canDelete = false,
            ),
        )

    private val userWithLiveTv =
        UserDto(
            id = UUID.randomUUID(),
            hasPassword = true,
            hasConfiguredPassword = true,
            hasConfiguredEasyPassword = false,
            policy =
                mockk<UserPolicy> {
                    every { enableLiveTvManagement } returns true
                },
        )

    private val userWithoutLiveTv =
        UserDto(
            id = UUID.randomUUID(),
            hasPassword = true,
            hasConfiguredPassword = true,
            hasConfiguredEasyPassword = false,
            policy =
                mockk<UserPolicy> {
                    every { enableLiveTvManagement } returns false
                },
        )

    private val prefsEnabled =
        AppPreferences
            .newBuilder()
            .apply {
                interfacePreferences =
                    InterfacePreferences
                        .newBuilder()
                        .apply {
                            enableMediaManagement = true
                        }.build()
            }.build()

    private val prefsDisabled =
        AppPreferences
            .newBuilder()
            .apply {
                interfacePreferences =
                    InterfacePreferences
                        .newBuilder()
                        .apply {
                            enableMediaManagement = false
                        }.build()
            }.build()

    @Test
    fun `Test canDelete`() {
        Assert.assertTrue(mediaManagementService.canDelete(movie, prefsEnabled))
        Assert.assertFalse(mediaManagementService.canDelete(movie, prefsDisabled))
        Assert.assertFalse(mediaManagementService.canDelete(movieCannotDelete, prefsEnabled))
    }

    @Test
    fun `Test canDelete recording`() {
        every { mockServerRepository.currentUserDto } returns userWithLiveTv
        Assert.assertTrue(mediaManagementService.canDelete(recording, prefsEnabled))
        Assert.assertFalse(mediaManagementService.canDelete(recording, prefsDisabled))
    }

    @Test
    fun `Test canDelete recording no permission`() {
        every { mockServerRepository.currentUserDto } returns userWithoutLiveTv
        Assert.assertFalse(mediaManagementService.canDelete(recording, prefsEnabled))
        Assert.assertFalse(mediaManagementService.canDelete(recording, prefsDisabled))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Test successful delete`() =
        runTest {
            coEvery { mockLibraryApi.deleteItem(any()) } returns Response(Unit, 200, emptyMap())

            val deletedItems = mutableListOf<DeletedItem>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                mediaManagementService.deletedItemFlow.toList(deletedItems)
            }
            val result = mediaManagementService.deleteItem(movie)
            Assert.assertTrue(result is DeleteResult.Success)
            coVerify(exactly = 1) { mockLibraryApi.deleteItem(movie.id) }
            Assert.assertEquals(1, deletedItems.size)
            Assert.assertEquals(deletedItems[0].item.id, movie.id)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Test unsuccessful delete`() =
        runTest {
            coEvery { mockLibraryApi.deleteItem(any()) } throws InvalidStatusException(403)

            val deletedItems = mutableListOf<DeletedItem>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                mediaManagementService.deletedItemFlow.toList(deletedItems)
            }
            val result = mediaManagementService.deleteItem(movie)
            Assert.assertTrue(result is DeleteResult.Error)
            coVerify(exactly = 1) { mockLibraryApi.deleteItem(movie.id) }
            Assert.assertEquals(0, deletedItems.size)
        }
}
