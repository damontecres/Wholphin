package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.nav.Destination
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class BaseItemDestinationTest {
    @Test
    fun audioWithAlbumId_navigatesToAlbumWithInitialSongId() {
        val songId = UUID.randomUUID()
        val albumId = UUID.randomUUID()
        val item = BaseItem(song(id = songId, albumId = albumId), false)

        val dest = item.destination()

        assertEquals(Destination.MediaItem::class, dest::class)
        val media = dest as Destination.MediaItem
        assertEquals(albumId, media.itemId)
        assertEquals(BaseItemKind.MUSIC_ALBUM, media.type)
        assertEquals(songId, media.initialSongId)
    }

    @Test
    fun audioWithoutAlbumId_returnsSelfAsMediaItem() {
        val songId = UUID.randomUUID()
        val item = BaseItem(song(id = songId, albumId = null), false)

        val dest = item.destination()

        assertEquals(songId, (dest as Destination.MediaItem).itemId)
        assertEquals(BaseItemKind.AUDIO, dest.type)
        assertNull(dest.initialSongId)
    }

    @Test
    fun movie_usesStandardDestination() {
        val movieId = UUID.randomUUID()
        val item = BaseItem(movie(id = movieId), false)

        val dest = item.destination()

        assertEquals(movieId, (dest as Destination.MediaItem).itemId)
        assertEquals(BaseItemKind.MOVIE, dest.type)
    }
}
