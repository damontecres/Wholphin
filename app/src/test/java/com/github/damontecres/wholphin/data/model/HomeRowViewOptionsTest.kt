package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.ui.AspectRatio
import com.github.damontecres.wholphin.ui.Cards
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

/**
 * A row stores only what the user chose, so everything else is decided here, on every load.
 */
class HomeRowViewOptionsTest {
    private val parentId = UUID.randomUUID()

    /** Matches the encoding used to persist settings, which is what makes an absent key mean null */
    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
        }

    @Test
    fun `a music row is square album art, not a stretched poster`() {
        val row = HomeRowConfig.RecentlyAdded(parentId)
        assertEquals(
            HomeRowViewOptions(
                heightDp = Cards.HEIGHT_EPISODE,
                aspectRatio = AspectRatio.SQUARE,
            ),
            row.resolveViewOptions(CollectionType.MUSIC),
        )
    }

    @Test
    fun `the same row in a movie library keeps the poster shape`() {
        val row = HomeRowConfig.RecentlyAdded(parentId)
        assertEquals(HomeRowViewOptions(), row.resolveViewOptions(CollectionType.MOVIES))
    }

    @Test
    fun `a row pointed at a music collection is square too`() {
        val row = HomeRowConfig.ByParent(parentId)
        assertEquals(HomeRowViewOptions.musicDefault, row.resolveViewOptions(CollectionType.MUSIC))
    }

    @Test
    fun `video libraries are wide`() {
        val row = HomeRowConfig.RecentlyAdded(parentId)
        assertEquals(AspectRatio.WIDE, row.resolveViewOptions(CollectionType.HOMEVIDEOS).aspectRatio)
        assertEquals(AspectRatio.WIDE, row.resolveViewOptions(CollectionType.MUSICVIDEOS).aspectRatio)
    }

    @Test
    fun `a choice the user made wins over the default`() {
        val chosen = HomeRowViewOptions(heightDp = 200, aspectRatio = AspectRatio.TALL)
        val row = HomeRowConfig.RecentlyAdded(parentId, chosen)
        assertEquals(chosen, row.resolveViewOptions(CollectionType.MUSIC))
    }

    @Test
    fun `rows whose shape does not come from a library ignore the collection type`() {
        assertEquals(
            HomeRowViewOptions.genreDefault,
            HomeRowConfig.Genres(parentId).resolveViewOptions(CollectionType.MUSIC),
        )
        assertEquals(
            HomeRowViewOptions.liveTvDefault,
            HomeRowConfig.TvChannels().resolveViewOptions(CollectionType.MUSIC),
        )
        assertEquals(
            HomeRowViewOptions.episodeDefault,
            HomeRowConfig.Favorite(BaseItemKind.EPISODE).resolveViewOptions(CollectionType.MUSIC),
        )
        assertEquals(
            HomeRowViewOptions(),
            HomeRowConfig.Favorite(BaseItemKind.MOVIE).resolveViewOptions(CollectionType.MUSIC),
        )
    }

    @Test
    fun `a row nobody sized stores nothing, so it picks up whatever the default is now`() {
        val stored = json.encodeToString<HomeRowConfig>(HomeRowConfig.RecentlyAdded(parentId))
        assertFalse("should not persist an unchosen value", stored.contains("viewOptions"))

        val decoded = json.decodeFromString<HomeRowConfig>(stored)
        assertNull(decoded.viewOptions)
        assertEquals(HomeRowViewOptions.musicDefault, decoded.resolveViewOptions(CollectionType.MUSIC))
    }

    @Test
    fun `a row the user sized is stored and comes back unchanged`() {
        val chosen = HomeRowViewOptions(heightDp = 200, aspectRatio = AspectRatio.SQUARE)
        val stored = json.encodeToString<HomeRowConfig>(HomeRowConfig.RecentlyAdded(parentId, chosen))
        assertEquals(chosen, json.decodeFromString<HomeRowConfig>(stored).viewOptions)
    }
}
