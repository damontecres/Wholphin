package com.github.damontecres.wholphin.ui.main.settings

import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.ui.AspectRatio
import com.github.damontecres.wholphin.ui.Cards
import org.jellyfin.sdk.model.api.CollectionType
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRowPresetsTest {
    @Test
    fun defaultMusicRowsMatchTheMusicLibraryCardSize() {
        val options = HomeRowPresets.WholphinDefault.getByCollectionType(CollectionType.MUSIC)

        assertEquals(
            HomeRowViewOptions(
                heightDp = Cards.HEIGHT_EPISODE,
                aspectRatio = AspectRatio.SQUARE,
            ),
            options,
        )
    }
}
