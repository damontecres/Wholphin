package com.github.damontecres.wholphin.data.model

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class AlphabetSortNameTest {
    @Test
    fun prefersSortNameOverName() {
        val dto =
            BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.MUSIC_ARTIST,
                name = "The Beatles",
                sortName = "beatles",
            )

        assertEquals("beatles", dto.alphabetSortName)
        assertEquals("beatles", BaseItem(dto).sortName)
    }

    @Test
    fun fallsBackToNameWhenSortNameMissing() {
        val dto =
            BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.STUDIO,
                name = "The Walt Disney Company",
                sortName = null,
            )

        assertEquals("The Walt Disney Company", dto.alphabetSortName)
    }
}
