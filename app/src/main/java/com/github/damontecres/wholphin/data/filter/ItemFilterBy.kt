package com.github.damontecres.wholphin.data.filter

import androidx.annotation.StringRes
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import java.util.UUID

val DefaultFilterOptions =
    listOf(
        PlayedFilter,
        FavoriteFilter,
        GenreFilter,
        CommunityRatingFilter,
        OfficialRatingFilter,
        VideoTypeFilter,
        YearFilter,
        DecadeFilter,
    )

val DefaultForFavoritesFilterOptions =
    listOf(
        PlayedFilter,
        GenreFilter,
        CommunityRatingFilter,
        OfficialRatingFilter,
        VideoTypeFilter,
        YearFilter,
        DecadeFilter,
    )

val DefaultForGenresFilterOptions =
    listOf(
        PlayedFilter,
        FavoriteFilter,
        CommunityRatingFilter,
        OfficialRatingFilter,
        VideoTypeFilter,
        YearFilter,
        DecadeFilter,
    )

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

data object FavoriteFilter : ItemFilterBy<Boolean> {
    override val stringRes: Int = R.string.favorites

    override val supportMultiple: Boolean = false

    override fun get(filter: GetItemsFilter): Boolean? = filter.favorite

    override fun set(
        value: Boolean?,
        filter: GetItemsFilter,
    ): GetItemsFilter = filter.copy(favorite = value)
}

data object OfficialRatingFilter : ItemFilterBy<List<String>> {
    override val stringRes: Int = R.string.official_rating

    override val supportMultiple: Boolean = true

    override fun get(filter: GetItemsFilter): List<String>? = filter.officialRatings

    override fun set(
        value: List<String>?,
        filter: GetItemsFilter,
    ): GetItemsFilter = filter.copy(officialRatings = value)
}

enum class FilterVideoType(
    val readable: String,
) {
    FOUR_K("4K"),
    HD("HD"),
    SD("SD"),
    THREE_D("3D"),
    BLU_RAY("Blu-Ray"),
    DVD("DVD"),
}

data object VideoTypeFilter : ItemFilterBy<List<FilterVideoType>> {
    override val stringRes: Int = R.string.video

    override val supportMultiple: Boolean = true

    override fun get(filter: GetItemsFilter): List<FilterVideoType>? = filter.videoTypes

    override fun set(
        value: List<FilterVideoType>?,
        filter: GetItemsFilter,
    ): GetItemsFilter = filter.copy(videoTypes = value)
}

data object YearFilter : ItemFilterBy<List<Int>> {
    override val stringRes: Int = R.string.year

    override val supportMultiple: Boolean = true

    override fun get(filter: GetItemsFilter): List<Int>? = filter.years

    override fun set(
        value: List<Int>?,
        filter: GetItemsFilter,
    ): GetItemsFilter = filter.copy(years = value)
}

data object DecadeFilter : ItemFilterBy<List<Int>> {
    override val stringRes: Int = R.string.decade

    override val supportMultiple: Boolean = true

    override fun get(filter: GetItemsFilter): List<Int>? = filter.decades

    override fun set(
        value: List<Int>?,
        filter: GetItemsFilter,
    ): GetItemsFilter = filter.copy(decades = value)
}

data object CommunityRatingFilter : ItemFilterBy<Int> {
    override val stringRes: Int = R.string.community_rating

    override val supportMultiple: Boolean = true

    override fun get(filter: GetItemsFilter): Int? = filter.minCommunityRating?.toInt()

    override fun set(
        value: Int?,
        filter: GetItemsFilter,
    ): GetItemsFilter = filter.copy(minCommunityRating = value?.toDouble())
}
