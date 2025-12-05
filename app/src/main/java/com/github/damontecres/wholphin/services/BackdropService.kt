package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import org.jellyfin.sdk.model.api.ImageType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
@OptIn(FlowPreview::class)
class BackdropService
    @Inject
    constructor(
        private val imageUrlService: ImageUrlService,
    ) {
        private val channel = Channel<BackdropRequest>(CONFLATED)

        val backdropFlow =
            channel
                .consumeAsFlow()
                .debounce {
                    if (it.delay) {
                        150.milliseconds
                    } else {
                        0.milliseconds
                    }
                }.map {
                    BackdropResult(
                        imageUrl = imageUrlService.getItemImageUrl(it.item, ImageType.BACKDROP),
                        fillWidth = if (it.small) .7f else .85f,
                        fillHeight = if (it.small) .7f else 1f,
                    )
                }

        suspend fun submit(item: BaseItem) {
            channel.send(BackdropRequest(item, false, false))
        }

        suspend fun submit(request: BackdropRequest) {
            channel.send(request)
        }
    }

data class BackdropRequest(
    val item: BaseItem,
    val delay: Boolean,
    val small: Boolean,
)

data class BackdropResult(
    val imageUrl: String?,
    val fillWidth: Float,
    val fillHeight: Float,
)
