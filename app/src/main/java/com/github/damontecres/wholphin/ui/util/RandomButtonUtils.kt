package com.github.damontecres.wholphin.ui.util

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.BlockingList
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import kotlin.random.Random

/**
 * Navigate to a random item in the given list
 */
suspend fun goToRandom(
    navigateTo: (Destination) -> Unit,
    parentId: String,
    filter: GetItemsFilter,
    sortAndDirection: SortAndDirection,
    recursive: Boolean,
    list: BlockingList<BaseItem?>,
) {
    val randomIndex = Random.nextInt(list.size)
    val random = list.getBlocking(randomIndex)

    Timber.d("Got random item #%s: %s", randomIndex, random?.id)
    if (random != null) {
        val destination =
            if (random.type == BaseItemKind.PHOTO) {
                Destination.Slideshow(
                    parentId = parentId.toUUIDOrNull(),
                    index = randomIndex,
                    filter =
                        CollectionFolderFilter(
                            filter = filter,
                        ),
                    sortAndDirection = sortAndDirection,
                    recursive = recursive,
                    startSlideshow = false,
                )
            } else {
                random.destination()
            }
        navigateTo(destination)
    }
}
