package com.github.damontecres.wholphin.ui.components

import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.ui.AspectRatio
import com.github.damontecres.wholphin.ui.Cards
import org.jellyfin.sdk.model.api.CollectionType
import java.util.UUID

/**
 * The title of each row on the recommended tab for a type of library, in order. Empty if that
 * type of library has no recommended tab.
 */
fun recommendedRowTitles(type: CollectionType): List<Int> {
    // Only the titles are used, so the library ID does not matter
    val anyId = UUID(0, 0)
    val rows =
        when (type) {
            CollectionType.MOVIES -> getRecommendedMovieRows(anyId)
            CollectionType.TVSHOWS -> getRecommendedTvShowRows(anyId, enableRewatching = false)
            CollectionType.MUSIC -> getRecommendedMusicRows(anyId)
            else -> return emptyList()
        }
    return rows.map { it.title } + R.string.suggestions
}

/**
 * The default display settings for the rows on the recommended tab for a type of library
 */
fun recommendedRowDefaultViewOptions(type: CollectionType): HomeRowViewOptions =
    when (type) {
        CollectionType.MUSIC -> {
            HomeRowViewOptions(
                aspectRatio = AspectRatio.SQUARE,
                heightDp = Cards.HEIGHT_EPISODE,
                showTitles = true,
            )
        }

        else -> {
            HomeRowViewOptions()
        }
    }
