package com.github.damontecres.wholphin.services

import android.content.Context
import android.content.res.Resources
import com.github.damontecres.wholphin.data.ExtrasItem
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.successResponse
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.operations.UserLibraryApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ExtraType
import org.jellyfin.sdk.model.api.ImageType
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class ExtrasServiceTest {
    private val testDispatcher = StandardTestDispatcher()

    private val mockContext = mockk<Context>()
    private val mockApiClient = mockk<ApiClient>()
    private val mockImageUrlService = mockk<ImageUrlService>()

    private val mockUserLibraryApi = mockk<UserLibraryApi>()
    private val mockResources = mockk<Resources>()

    private val extrasService = ExtrasService(mockApiClient, mockContext, mockImageUrlService)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        WholphinDispatchers.configure(testDispatcher)

        every { mockApiClient.userLibraryApi } returns mockUserLibraryApi
        every { mockContext.resources } returns mockResources
        every { mockResources.getString(any()) } returns "Interviews"
        every { mockResources.getQuantityString(any(), any()) } returns "Interviews"
        every { mockResources.getQuantityString(any(), any(), any<Int>()) } returns "Interviews"

        every { mockImageUrlService.getItemImageUrl(any<BaseItem>(), ImageType.PRIMARY, any(), any(), any()) } returns
            "https://localhost/image.jpg"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        WholphinDispatchers.reset()
    }

    @Test
    fun `Test grouping extras`() =
        runTest {
            val extrasDtos =
                List(3) {
                    BaseItemDto(
                        id = UUID.randomUUID(),
                        type = BaseItemKind.VIDEO,
                        extraType = ExtraType.INTERVIEW,
                    )
                }
            coEvery { mockUserLibraryApi.getSpecialFeatures(any(), any()) } returns
                successResponse(extrasDtos)

            val extras = extrasService.getExtras(UUID.randomUUID())
            Assert.assertEquals(1, extras.size)
            val extra = extras.first()
            Assert.assertTrue(extra is ExtrasItem.Group)
            extra as ExtrasItem.Group
            val extrasIds = extra.items.map { it.id }
            Assert.assertEquals(extrasDtos.map { it.id }, extrasIds)
            Assert.assertEquals(extra.type, ExtraType.INTERVIEW)
        }

    @Test
    fun `Test grouping extras with multiple types`() =
        runTest {
            val extrasDtos =
                List(3) {
                    BaseItemDto(
                        id = UUID.randomUUID(),
                        type = BaseItemKind.VIDEO,
                        extraType = ExtraType.INTERVIEW,
                    )
                } +
                    List(3) {
                        BaseItemDto(
                            id = UUID.randomUUID(),
                            type = BaseItemKind.VIDEO,
                            extraType = ExtraType.CLIP,
                        )
                    }

            coEvery { mockUserLibraryApi.getSpecialFeatures(any(), any()) } returns
                successResponse(extrasDtos)

            val extras = extrasService.getExtras(UUID.randomUUID())
            Assert.assertEquals(2, extras.size)
            extras.forEachIndexed { index, item ->
                Assert.assertTrue("index=$index", item is ExtrasItem.Group)
            }
            val extrasIds = extras.flatMap { (it as ExtrasItem.Group).items.map { it.id } }.toSet()
            Assert.assertEquals(extrasDtos.map { it.id }.toSet(), extrasIds)
            Assert.assertEquals(extras[0].type, ExtraType.CLIP)
            Assert.assertEquals(extras[1].type, ExtraType.INTERVIEW)
        }

    @Test
    fun `Test extras not grouped`() =
        runTest {
            val extrasDtos =
                listOf(
                    BaseItemDto(
                        id = UUID.randomUUID(),
                        type = BaseItemKind.VIDEO,
                        extraType = ExtraType.INTERVIEW,
                    ),
                    BaseItemDto(
                        id = UUID.randomUUID(),
                        type = BaseItemKind.VIDEO,
                        extraType = ExtraType.CLIP,
                    ),
                )

            coEvery { mockUserLibraryApi.getSpecialFeatures(any(), any()) } returns
                successResponse(extrasDtos)

            val extras = extrasService.getExtras(UUID.randomUUID())
            Assert.assertEquals(2, extras.size)
            extras.forEachIndexed { index, item ->
                Assert.assertTrue("index=$index", item is ExtrasItem.Single)
            }
            val extrasIds = extras.map { (it as ExtrasItem.Single).item.id }.toSet()
            Assert.assertEquals(extrasDtos.map { it.id }.toSet(), extrasIds)
            Assert.assertEquals(extras[0].type, ExtraType.CLIP)
            Assert.assertEquals(extras[1].type, ExtraType.INTERVIEW)
        }

    @Test
    fun `Test mixed grouping`() =
        runTest {
            val extrasDtos =
                listOf(
                    BaseItemDto(
                        id = UUID.randomUUID(),
                        type = BaseItemKind.VIDEO,
                        extraType = ExtraType.CLIP,
                    ),
                ) +
                    List(3) {
                        BaseItemDto(
                            id = UUID.randomUUID(),
                            type = BaseItemKind.VIDEO,
                            extraType = ExtraType.INTERVIEW,
                        )
                    }

            coEvery { mockUserLibraryApi.getSpecialFeatures(any(), any()) } returns
                successResponse(extrasDtos)

            val extras = extrasService.getExtras(UUID.randomUUID())
            Assert.assertEquals(2, extras.size)
            Assert.assertTrue(extras[0] is ExtrasItem.Single)
            Assert.assertTrue(extras[1] is ExtrasItem.Group)

            val extrasIds = listOf((extras[0] as ExtrasItem.Single).item.id) + (extras[1] as ExtrasItem.Group).items.map { it.id }
            Assert.assertEquals(extrasDtos.map { it.id }, extrasIds)
            Assert.assertEquals(extras[0].type, ExtraType.CLIP)
            Assert.assertEquals(extras[1].type, ExtraType.INTERVIEW)
        }
}
