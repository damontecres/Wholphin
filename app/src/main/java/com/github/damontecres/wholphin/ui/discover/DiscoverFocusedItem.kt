package com.github.damontecres.wholphin.ui.discover

import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.ui.util.ResArgStringProvider
import com.github.damontecres.wholphin.ui.util.StringProvider
import com.github.damontecres.wholphin.ui.util.StringStringProvider

sealed interface DiscoverFocusedItem {
    val id: Int
    val title: StringProvider?
    val subtitle: String?
    val overview: String?
    val backDropUrl: String?

    data class Item(
        val item: DiscoverItem,
    ) : DiscoverFocusedItem {
        override val id: Int
            get() = item.id
        override val title: StringProvider? = item.title?.let { StringStringProvider(it) }
        override val subtitle: String?
            get() = item.subtitle
        override val overview: String?
            get() = item.overview
        override val backDropUrl: String?
            get() = item.backDropUrl
    }

    data class Genre(
        override val id: Int,
        val name: String,
        val type: SeerrItemType,
        override val backDropUrl: String?,
    ) : DiscoverFocusedItem {
        override val title: StringProvider = discoverGenreTitle(name, type)
        override val subtitle: String?
            get() = null
        override val overview: String?
            get() = null
    }
}

fun discoverGenreTitle(
    name: String,
    type: SeerrItemType,
): ResArgStringProvider =
    when (type) {
        SeerrItemType.MOVIE -> ResArgStringProvider(R.string.genre_movies, name)
        SeerrItemType.TV -> ResArgStringProvider(R.string.genre_tv_shows, name)
        else -> throw UnsupportedOperationException("$type not supported")
    }
