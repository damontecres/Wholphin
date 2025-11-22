package com.github.damontecres.wholphin.data.filter

import androidx.annotation.StringRes
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import java.util.UUID

sealed interface ItemFilterBy<T> {
    @get:StringRes
    val stringRes: Int

    val supportMultiple: Boolean

    fun get(filter: GetItemsFilter): T?

    fun set(
        value: T?,
        filter: GetItemsFilter,
    ): GetItemsFilter
}

data object GenreFilter : ItemFilterBy<List<UUID>> {
    override val stringRes: Int = R.string.genres

    override val supportMultiple: Boolean = true

    override fun get(filter: GetItemsFilter): List<UUID>? = filter.genres

    override fun set(
        value: List<UUID>?,
        filter: GetItemsFilter,
    ): GetItemsFilter = filter.copy(genres = value)
}

data object PlayedFilter : ItemFilterBy<Boolean> {
    override val stringRes: Int = R.string.played

    override val supportMultiple: Boolean = false

    override fun get(filter: GetItemsFilter): Boolean? = filter.played

    override fun set(
        value: Boolean?,
        filter: GetItemsFilter,
    ): GetItemsFilter = filter.copy(played = value)
}
