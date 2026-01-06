package com.github.damontecres.wholphin.ui.slideshow

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.VideoFilter

data class SlideshowState(
    val index: Int,
    val totalCount: Int,
    val currentImage: BaseItem,
    val currentFilter: VideoFilter,
)
