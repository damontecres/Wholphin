package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.HomeRowConfigDisplay
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.preferences.PrefContentScale
import com.github.damontecres.wholphin.ui.AspectRatio
import com.github.damontecres.wholphin.ui.components.ViewOptionImageType
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.UUID
import org.junit.Assert
import org.junit.Test
import kotlin.reflect.KClass

class TestHomeRowSamples {
    companion object {
        val SAMPLES =
            listOf(
                HomeRowConfigDisplay(
                    title = "Recently added",
                    config =
                        HomeRowConfig.RecentlyAdded(
                            id = 0,
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
                ),
                HomeRowConfigDisplay(
                    title = "Recently released",
                    config =
                        HomeRowConfig.RecentlyReleased(
                            id = 0,
                            parentId = UUID.randomUUID(),
                            viewOptions = HomeRowViewOptions(),
                        ),
                ),
                HomeRowConfigDisplay(
                    title = "Genres",
                    config =
                        HomeRowConfig.Genres(
                            id = 0,
                            parentId = UUID.randomUUID(),
                            viewOptions = HomeRowViewOptions(),
                        ),
                ),
                HomeRowConfigDisplay(
                    title = "Continue watching",
                    config =
                        HomeRowConfig.ContinueWatching(
                            id = 0,
                            viewOptions = HomeRowViewOptions(),
                        ),
                ),
                HomeRowConfigDisplay(
                    title = "Next up",
                    config =
                        HomeRowConfig.NextUp(
                            id = 0,
                            viewOptions = HomeRowViewOptions(),
                        ),
                ),
                HomeRowConfigDisplay(
                    title = "Combined",
                    config =
                        HomeRowConfig.ContinueWatchingCombined(
                            id = 0,
                            viewOptions = HomeRowViewOptions(),
                        ),
                ),
            )
    }

    @Test
    fun `Check all types have a sample`() {
        // This ensures there is a sample for each possible HomeRowConfig type
        val foundTypes = mutableSetOf<KClass<out HomeRowConfig>>()
        SAMPLES.forEach {
            when (it.config) {
                is HomeRowConfig.ContinueWatching -> foundTypes.add(HomeRowConfig.ContinueWatching::class)
                is HomeRowConfig.ContinueWatchingCombined -> foundTypes.add(HomeRowConfig.ContinueWatchingCombined::class)
                is HomeRowConfig.Genres -> foundTypes.add(HomeRowConfig.Genres::class)
                is HomeRowConfig.NextUp -> foundTypes.add(HomeRowConfig.NextUp::class)
                is HomeRowConfig.RecentlyAdded -> foundTypes.add(HomeRowConfig.RecentlyAdded::class)
                is HomeRowConfig.RecentlyReleased -> foundTypes.add(HomeRowConfig.RecentlyReleased::class)
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
        json.decodeFromString<List<HomeRowConfigDisplay>>(string)
    }
}
