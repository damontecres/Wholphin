package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.preferences.PrefContentScale
import com.github.damontecres.wholphin.services.HomeSettingsService
import com.github.damontecres.wholphin.ui.AspectRatio
import com.github.damontecres.wholphin.ui.components.ViewOptionImageType
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.junit.Assert
import org.junit.Test
import kotlin.reflect.KClass

class TestHomeRowSamples {
    companion object {
        val SAMPLES =
            listOf(
                HomeRowConfig.RecentlyAdded(
                    parentId = UUID.randomUUID(),
                    viewOptions =
                        HomeRowViewOptions(
                            heightDp = 100,
                            spacing = 8,
                            contentScale = PrefContentScale.CROP,
                            aspectRatio = AspectRatio.FOUR_THREE,
                            imageType = ViewOptionImageType.THUMB,
                            showTitles = false,
                            useSeries = false,
                        ),
                ),
                HomeRowConfig.RecentlyReleased(
                    parentId = UUID.randomUUID(),
                    viewOptions = HomeRowViewOptions(),
                ),
                HomeRowConfig.Genres(
                    parentId = UUID.randomUUID(),
                    viewOptions = HomeRowViewOptions(),
                ),
                HomeRowConfig.ContinueWatching(
                    viewOptions = HomeRowViewOptions(),
                ),
                HomeRowConfig.NextUp(
                    viewOptions = HomeRowViewOptions(),
                ),
                HomeRowConfig.ContinueWatchingCombined(
                    viewOptions = HomeRowViewOptions(),
                ),
                HomeRowConfig.ByParent(
                    parentId = UUID.randomUUID(),
                    recursive = true,
                    sort = SortAndDirection(ItemSortBy.CRITIC_RATING, SortOrder.ASCENDING),
                    viewOptions = HomeRowViewOptions(),
                ),
                HomeRowConfig.GetItems(
                    name = "Episodes by date created",
                    getItems =
                        GetItemsRequest(
                            parentId = UUID.randomUUID(),
                            recursive = true,
                            isFavorite = true,
                            includeItemTypes = listOf(BaseItemKind.EPISODE),
                            sortBy = listOf(ItemSortBy.DATE_CREATED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                        ),
                    viewOptions = HomeRowViewOptions(),
                ),
                HomeRowConfig.Favorite(kind = BaseItemKind.SERIES),
                HomeRowConfig.Recordings(),
                HomeRowConfig.TvPrograms(),
                HomeRowConfig.Suggestions(parentId = UUID.randomUUID()),
            )
    }

    @Test
    fun `Check all types have a sample`() {
        // This ensures there is a sample for each possible HomeRowConfig type
        val foundTypes = mutableSetOf<KClass<out HomeRowConfig>>()
        SAMPLES.forEach {
            when (it) {
                is HomeRowConfig.ContinueWatching -> foundTypes.add(it::class)
                is HomeRowConfig.ContinueWatchingCombined -> foundTypes.add(it::class)
                is HomeRowConfig.Genres -> foundTypes.add(it::class)
                is HomeRowConfig.NextUp -> foundTypes.add(it::class)
                is HomeRowConfig.RecentlyAdded -> foundTypes.add(it::class)
                is HomeRowConfig.RecentlyReleased -> foundTypes.add(it::class)
                is HomeRowConfig.ByParent -> foundTypes.add(it::class)
                is HomeRowConfig.GetItems -> foundTypes.add(it::class)
                is HomeRowConfig.Favorite -> foundTypes.add(it::class)
                is HomeRowConfig.Recordings -> foundTypes.add(it::class)
                is HomeRowConfig.TvPrograms -> foundTypes.add(it::class)
                is HomeRowConfig.Suggestions -> foundTypes.add(it::class)
            }
        }
        Assert.assertEquals(HomeRowConfig::class.sealedSubclasses.size, foundTypes.size)
    }

    @Test
    fun `Print sample JSON`() {
        // This just prints out the JSON of the samples so developers can review
        val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            }
        val string = json.encodeToString(SAMPLES)
        println(string)
        json.decodeFromString<List<HomeRowConfig>>(string)
    }

    @Test
    fun `Parse list of rows with an unknown type`() {
        val service =
            HomeSettingsService(
                context = mockk(),
                api = mockk(),
                userPreferencesService = mockk(),
                navDrawerItemRepository = mockk(),
                latestNextUpService = mockk(),
                imageUrlService = mockk(),
                suggestionService = mockk(),
            )

        val str = """{
            "type": "HomePageSettings",
            "version": 1,
            "rows": [
                {
                    "type": "RecentlyAdded",
                    "parentId": "1dd1c2fd-2e1b-48e4-ba94-17a2350fe9cf"
                },
                {
                    "type": "Does not exist",
                    "viewOptions": {}
                }
            ]
        }"""

        val jsonElement = service.jsonParser.parseToJsonElement(str)
        val settings = service.decode(jsonElement)
        Assert.assertEquals(1, settings.rows.size)
    }
}
