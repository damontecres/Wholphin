package com.github.damontecres.wholphin.ui.detail.music

import com.github.damontecres.wholphin.ui.DetailItemFields
import com.github.damontecres.wholphin.util.GetPlaylistItemsRequestHandler
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class PlaylistItemsRequestTest {
    private val playlistId = UUID.fromString("0f3c3a2e-6a52-4d3e-9a51-2b0b1c7d9e11")

    @Test
    fun `a playlist is read through the playlist endpoint with its id`() {
        val request = playlistItemsRequest(playlistId)

        assertEquals(playlistId, request.playlistId)
        assertEquals(DetailItemFields, request.fields)
    }

    @Test
    fun `pages of a playlist keep the requested window`() {
        val page = GetPlaylistItemsRequestHandler.prepare(playlistItemsRequest(playlistId), 100, 100, true)

        assertEquals(playlistId, page.playlistId)
        assertEquals(100, page.startIndex)
        assertEquals(100, page.limit)
    }
}
