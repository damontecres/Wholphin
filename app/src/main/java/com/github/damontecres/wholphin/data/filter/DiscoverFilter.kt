package com.github.damontecres.wholphin.data.filter

import androidx.annotation.StringRes
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.SearchApi
import com.github.damontecres.wholphin.api.seerr.SearchApi.CertificationModeDiscoverTvGet
import com.github.damontecres.wholphin.api.seerr.model.DiscoverMoviesGet200Response
import com.github.damontecres.wholphin.api.seerr.model.DiscoverTvGet200Response
import com.github.damontecres.wholphin.api.seerr.model.TvDetails
import com.github.damontecres.wholphin.ui.data.flip
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.SortOrder
import java.time.format.DateTimeFormatter
import kotlin.time.Duration

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-M-d")

enum class DiscoverSort(
    val key: String,
    @StringRes val stringRes: Int,
) {
    POPULARITY("popularity", R.string.popularity),
    RELEASE_DATE("release_date", R.string.sort_by_date_released),
    TMDB_VOTE("vote_average", R.string.community_rating),
    ALPHABETICAL("original_title", R.string.sort_by_name),
}

private val SortOrder.key get() =
    when (this) {
        SortOrder.ASCENDING -> "asc"
        SortOrder.DESCENDING -> "desc"
    }

@Serializable
data class DiscoverSortAndDirection(
    val sort: DiscoverSort = DiscoverSort.POPULARITY,
    val direction: SortOrder = SortOrder.DESCENDING,
) {
    val key = "${sort.key}.${direction.key}"

    fun flip() = copy(direction = direction.flip())
}

@Serializable
data class DiscoverFilter(
    // /api/v1/languages
    val language: String? = null,
    val genreIds: List<Int>? = null,
    val networkIds: List<Int>? = null,
    // /api/v1/search/keyword
    val keywordIds: List<Int>? = null,
    val excludeKeywordIds: List<Int>? = null,
    val sortBy: DiscoverSortAndDirection? = null,
//    val firstAirDateGte: LocalDate? = null,
//    val firstAirDateLte: LocalDate? = null,
    val withRuntimeGte: Duration? = null,
    val withRuntimeLte: Duration? = null,
    val voteAverageGte: Int? = null,
    val voteAverageLte: Int? = null,
    val voteCountGte: Int? = null,
    val voteCountLte: Int? = null,
    // /api/v1/watchproviders/regions
    val watchRegion: String? = null, // US
    // networkIds?
    // val watchProviders: String? = null,
    val status: List<TvDetails.Status>? = null,
    val certification: List<String>? = null,
//    val certificationGte: String? = null,
//    val certificationLte: String? = null,
//    val certificationCountry: String? = null, // US
//    val certificationMatchExact: Boolean? = null,
) {
    /**
     * Returns how many of filters are actually being used in this
     */
    fun countFilters(filterOptions: List<DiscoverFilterBy<*>>): Int {
        var count = 0
        filterOptions.forEach {
            if (it.get(this) != null) count++
        }
        return count
    }

    /**
     * Clear all the values for the given filters
     */
    fun delete(filterOptions: List<DiscoverFilterBy<*>>): DiscoverFilter {
        var newFilter = this
        filterOptions.forEach {
            newFilter = it.set(null, newFilter)
        }
        return newFilter
    }

    suspend fun discoverTv(
        searchApi: SearchApi,
        page: Int,
    ): DiscoverTvGet200Response =
        searchApi.discoverTvGet(
            page = page,
            language = language,
            genre = genreIds?.joinToString(",") { it.toString() },
            network = null,
            keywords = keywordIds?.joinToString(",") { it.toString() },
            excludeKeywords = excludeKeywordIds?.joinToString(",") { it.toString() },
            sortBy = sortBy?.key,
//            firstAirDateGte = firstAirDateGte?.let { dateFormatter.format(it) },
//            firstAirDateLte = firstAirDateLte?.let { dateFormatter.format(it) },
            withRuntimeGte = withRuntimeGte?.inWholeMinutes?.toInt(),
            withRuntimeLte = withRuntimeLte?.inWholeMinutes?.toInt(),
            voteAverageGte = voteAverageGte,
            voteAverageLte = voteAverageLte,
            voteCountGte = voteCountGte,
            voteCountLte = voteCountLte,
            // TODO
//            watchRegion = watchRegion,
            watchRegion = networkIds?.let { "US" },
            watchProviders = networkIds?.joinToString(",") { it.toString() },
            status = status?.joinToString("|") { it.ordinal.toString() },
            certification = certification?.joinToString("|"),
//            certificationGte = certificationGte,
//            certificationLte = certificationLte,
            certificationCountry = certification?.let { "US" },
            certificationMode = certification?.let { CertificationModeDiscoverTvGet.EXACT },
        )

    suspend fun discoverMovies(
        searchApi: SearchApi,
        page: Int,
    ): DiscoverMoviesGet200Response =
        searchApi.discoverMoviesGet(
            page = page,
            language = language,
            genre = genreIds?.joinToString(",") { it.toString() },
//            network = null,
            keywords = keywordIds?.joinToString(",") { it.toString() },
            excludeKeywords = excludeKeywordIds?.joinToString(",") { it.toString() },
            sortBy = sortBy?.key,
//            firstAirDateGte = firstAirDateGte?.let { dateFormatter.format(it) },
//            firstAirDateLte = firstAirDateLte?.let { dateFormatter.format(it) },
            withRuntimeGte = withRuntimeGte?.inWholeMinutes?.toInt(),
            withRuntimeLte = withRuntimeLte?.inWholeMinutes?.toInt(),
            voteAverageGte = voteAverageGte,
            voteAverageLte = voteAverageLte,
            voteCountGte = voteCountGte,
            voteCountLte = voteCountLte,
            // TODO
//            watchRegion = watchRegion,
            watchRegion = networkIds?.let { "US" },
            watchProviders = networkIds?.joinToString(",") { it.toString() },
//            status = status,
            certification = certification?.joinToString("|"),
//            certificationGte = certificationGte,
//            certificationLte = certificationLte,
            certificationCountry = certification?.let { "US" },
            certificationMode = certification?.let { SearchApi.CertificationModeDiscoverMoviesGet.EXACT },
        )
}

val discoverMovieFilters =
    listOf(
        DiscoverMovieGenreFilter,
        DiscoverMovieStudiosFilter,
        DiscoverRatingFilter,
        DiscoverMovieContentRatingFilter,
    )

val discoverTvFilters =
    listOf(
        DiscoverTvGenreFilter,
        DiscoverTvStudiosFilter,
        DiscoverRatingFilter,
        DiscoverTvContentRatingFilter,
        DiscoverTvStatusFilter,
    )

/**
 * A way to filter discover
 *
 * Gets and sets values within a [DiscoverFilter]
 */
sealed interface DiscoverFilterBy<T> : FilterBy<DiscoverFilter, T>

data object DiscoverMovieGenreFilter : DiscoverFilterBy<List<Int>> {
    override val stringRes: Int = R.string.genres

    override val supportMultiple: Boolean = true

    override fun get(filter: DiscoverFilter): List<Int>? = filter.genreIds

    override fun set(
        value: List<Int>?,
        filter: DiscoverFilter,
    ): DiscoverFilter = filter.copy(genreIds = value)
}

data object DiscoverTvGenreFilter : DiscoverFilterBy<List<Int>> {
    override val stringRes: Int = R.string.genres

    override val supportMultiple: Boolean = true

    override fun get(filter: DiscoverFilter): List<Int>? = filter.genreIds

    override fun set(
        value: List<Int>?,
        filter: DiscoverFilter,
    ): DiscoverFilter = filter.copy(genreIds = value)
}

data object DiscoverMovieStudiosFilter : DiscoverFilterBy<List<Int>> {
    override val stringRes: Int = R.string.studios

    override val supportMultiple: Boolean = true

    override fun get(filter: DiscoverFilter): List<Int>? = filter.networkIds

    override fun set(
        value: List<Int>?,
        filter: DiscoverFilter,
    ): DiscoverFilter = filter.copy(networkIds = value)
}

data object DiscoverTvStudiosFilter : DiscoverFilterBy<List<Int>> {
    override val stringRes: Int = R.string.studios

    override val supportMultiple: Boolean = true

    override fun get(filter: DiscoverFilter): List<Int>? = filter.networkIds

    override fun set(
        value: List<Int>?,
        filter: DiscoverFilter,
    ): DiscoverFilter = filter.copy(networkIds = value)
}

data object DiscoverTvStatusFilter : DiscoverFilterBy<List<TvDetails.Status>> {
    override val stringRes: Int = R.string.production_status

    override val supportMultiple: Boolean = true

    override fun get(filter: DiscoverFilter): List<TvDetails.Status>? = filter.status

    override fun set(
        value: List<TvDetails.Status>?,
        filter: DiscoverFilter,
    ): DiscoverFilter = filter.copy(status = value)
}

data object DiscoverMovieContentRatingFilter : DiscoverFilterBy<List<String>> {
    override val stringRes: Int = R.string.official_rating

    override val supportMultiple: Boolean = true

    override fun get(filter: DiscoverFilter): List<String>? = filter.certification

    override fun set(
        value: List<String>?,
        filter: DiscoverFilter,
    ): DiscoverFilter = filter.copy(certification = value)
}

data object DiscoverTvContentRatingFilter : DiscoverFilterBy<List<String>> {
    override val stringRes: Int = R.string.official_rating

    override val supportMultiple: Boolean = true

    override fun get(filter: DiscoverFilter): List<String>? = filter.certification

    override fun set(
        value: List<String>?,
        filter: DiscoverFilter,
    ): DiscoverFilter = filter.copy(certification = value)
}

data object DiscoverRatingFilter : DiscoverFilterBy<Int> {
    override val stringRes: Int = R.string.community_rating

    override val supportMultiple: Boolean = true

    override fun get(filter: DiscoverFilter): Int? = filter.voteAverageGte

    override fun set(
        value: Int?,
        filter: DiscoverFilter,
    ): DiscoverFilter = filter.copy(voteAverageGte = value)
}
