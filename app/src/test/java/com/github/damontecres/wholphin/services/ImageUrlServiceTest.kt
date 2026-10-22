package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.operations.ImageApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.serializer.toUUID
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class ImageUrlServiceTest {
    private val mockApiClient = mockk<ApiClient>()
    private val imageUrlService = ImageUrlService(mockApiClient)

    private val itemId = UUID.randomUUID()
    private val seriesId = UUID.randomUUID()
    private val seasonId = UUID.randomUUID()

    @Before
    fun setup() {
        every { mockApiClient.baseUrl } returns "http://localhost"
        every { mockApiClient.createUrl(any(), any(), any(), any()) } answers { callOriginal() }
        every { mockApiClient.imageApi } returns ImageApi(mockApiClient)
    }

    private fun episode(
        imageTags: Map<ImageType, String> = emptyMap(),
        parentLogoItemId: UUID? = null,
        parentThumbItemId: UUID? = null,
        parentBackdropItemId: UUID? = null,
    ) = BaseItemDto(
        id = itemId,
        type = BaseItemKind.EPISODE,
        seriesId = seriesId,
        seasonId = seasonId,
        imageTags = imageTags,
        seriesPrimaryImageTag = "seriesPrimary",
        parentLogoItemId = parentLogoItemId,
        parentLogoImageTag = parentLogoItemId?.let { "parentLogo" },
        parentThumbItemId = parentThumbItemId,
        parentThumbImageTag = parentThumbItemId?.let { "parentThumb" },
        parentBackdropItemId = parentBackdropItemId,
        parentBackdropImageTags = parentBackdropItemId?.let { listOf("parentBackdrop0", "parentBackdrop1") },
    )

    private fun movie(
        imageTags: Map<ImageType, String> = emptyMap(),
        backdropImageTags: List<String>? = null,
    ) = BaseItemDto(
        id = itemId,
        type = BaseItemKind.MOVIE,
        imageTags = imageTags,
        backdropImageTags = backdropImageTags,
    )

    private fun assertImage(
        url: String?,
        expectedId: UUID,
        expectedType: ImageType,
        expectedTag: String?,
    ) {
        Assert.assertNotNull(url)
        val path = url!!.substringBefore('?')
        Assert.assertEquals(expectedId, path.substringAfter("/Items/").substringBefore('/').toUUID())
        Assert.assertEquals(expectedType.serialName, path.substringAfter("/Images/"))
        val tag =
            url
                .substringAfter('?', "")
                .split('&')
                .firstOrNull { it.startsWith("tag=") }
                ?.substringAfter("tag=")
        Assert.assertEquals(expectedTag, tag)
    }

    @Test
    fun `Own images use own tags`() {
        val item =
            BaseItem(
                movie(
                    imageTags =
                        mapOf(
                            ImageType.PRIMARY to "primary",
                            ImageType.LOGO to "logo",
                            ImageType.THUMB to "thumb",
                            ImageType.BANNER to "banner",
                            ImageType.ART to "art",
                        ),
                    backdropImageTags = listOf("backdrop0", "backdrop1"),
                ),
            )
        assertImage(imageUrlService.getItemImageUrl(item, ImageType.PRIMARY), itemId, ImageType.PRIMARY, "primary")
        assertImage(imageUrlService.getItemImageUrl(item, ImageType.LOGO), itemId, ImageType.LOGO, "logo")
        assertImage(imageUrlService.getItemImageUrl(item, ImageType.THUMB), itemId, ImageType.THUMB, "thumb")
        assertImage(imageUrlService.getItemImageUrl(item, ImageType.BANNER), itemId, ImageType.BANNER, "banner")
        assertImage(imageUrlService.getItemImageUrl(item, ImageType.ART), itemId, ImageType.ART, "art")
        assertImage(imageUrlService.getItemImageUrl(item, ImageType.BACKDROP), itemId, ImageType.BACKDROP, "backdrop0")
    }

    @Test
    fun `Missing tags are omitted`() {
        val item = BaseItem(movie())
        assertImage(imageUrlService.getItemImageUrl(item, ImageType.PRIMARY), itemId, ImageType.PRIMARY, null)
        assertImage(imageUrlService.getItemImageUrl(item, ImageType.LOGO), itemId, ImageType.LOGO, null)
        Assert.assertNull(imageUrlService.getItemImageUrl(item, ImageType.BACKDROP))
    }

    @Test
    fun `Thumb falls back to own backdrop`() {
        val item = BaseItem(movie(backdropImageTags = listOf("backdrop0")))
        assertImage(imageUrlService.getItemImageUrl(item, ImageType.THUMB), itemId, ImageType.BACKDROP, "backdrop0")
    }

    @Test
    fun `Episode primary uses series tag`() {
        val item = BaseItem(episode(imageTags = mapOf(ImageType.PRIMARY to "primary")), useSeriesForPrimary = true)
        assertImage(imageUrlService.getItemImageUrl(item, ImageType.PRIMARY), seriesId, ImageType.PRIMARY, "seriesPrimary")
        assertImage(imageUrlService.getItemImageUrl(item, ImageType.BANNER), seriesId, ImageType.BANNER, null)
        assertImage(
            imageUrlService.getItemImageUrl(item, ImageType.PRIMARY, useSeriesForPrimary = false),
            itemId,
            ImageType.PRIMARY,
            "primary",
        )
    }

    @Test
    fun `Season without primary uses series tag`() {
        val item = BaseItem(episode().copy(id = seasonId, type = BaseItemKind.SEASON, seasonId = null))
        assertImage(imageUrlService.getItemImageUrl(item, ImageType.PRIMARY), seriesId, ImageType.PRIMARY, "seriesPrimary")
    }

    @Test
    fun `Episode logo uses parent logo tag only if from series`() {
        assertImage(
            imageUrlService.getItemImageUrl(BaseItem(episode(parentLogoItemId = seriesId)), ImageType.LOGO),
            seriesId,
            ImageType.LOGO,
            "parentLogo",
        )
        assertImage(
            imageUrlService.getItemImageUrl(BaseItem(episode(parentLogoItemId = seasonId)), ImageType.LOGO),
            seriesId,
            ImageType.LOGO,
            null,
        )
    }

    @Test
    fun `Episode backdrop uses parent backdrop tag only if from series`() {
        assertImage(
            imageUrlService.getItemImageUrl(BaseItem(episode(parentBackdropItemId = seriesId)), ImageType.BACKDROP),
            seriesId,
            ImageType.BACKDROP,
            "parentBackdrop0",
        )
        assertImage(
            imageUrlService.getItemImageUrl(BaseItem(episode(parentBackdropItemId = seasonId)), ImageType.BACKDROP),
            seriesId,
            ImageType.BACKDROP,
            null,
        )
    }

    @Test
    fun `Episode thumb uses parent tags`() {
        assertImage(
            imageUrlService.getItemImageUrl(
                BaseItem(episode(parentThumbItemId = seriesId, parentBackdropItemId = seriesId), useSeriesForPrimary = true),
                ImageType.THUMB,
            ),
            seriesId,
            ImageType.THUMB,
            "parentThumb",
        )
        assertImage(
            imageUrlService.getItemImageUrl(
                BaseItem(episode(parentBackdropItemId = seriesId), useSeriesForPrimary = true),
                ImageType.THUMB,
            ),
            seriesId,
            ImageType.BACKDROP,
            "parentBackdrop0",
        )
        assertImage(
            imageUrlService.getItemImageUrl(
                BaseItem(episode(imageTags = mapOf(ImageType.PRIMARY to "primary")), useSeriesForPrimary = true),
                ImageType.THUMB,
            ),
            itemId,
            ImageType.PRIMARY,
            "primary",
        )
    }

    @Test
    fun `Season without thumb uses parent thumb tag`() {
        val item = BaseItem(episode(parentThumbItemId = seriesId).copy(id = seasonId, type = BaseItemKind.SEASON))
        assertImage(imageUrlService.getItemImageUrl(item, ImageType.THUMB), seriesId, ImageType.THUMB, "parentThumb")
    }
}
