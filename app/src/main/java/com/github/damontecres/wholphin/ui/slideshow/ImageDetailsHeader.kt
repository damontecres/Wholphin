package com.github.damontecres.wholphin.ui.slideshow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem

@Composable
fun ImageDetailsHeader(
    player: Player,
    image: BaseItem,
    position: Int,
    count: Int,
    moreOnClick: () -> Unit,
    onZoom: (Float) -> Unit,
    onRotate: (Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .height(440.dp)
                .bringIntoViewRequester(bringIntoViewRequester),
    ) {
        // Title
        Text(
            text = image.title ?: "",
//                        color = MaterialTheme.colorScheme.onBackground,
            color = Color.LightGray,
            style =
                MaterialTheme.typography.displayMedium.copy(
                    shadow =
                        Shadow(
                            color = Color.DarkGray,
                            offset = Offset(5f, 2f),
                            blurRadius = 2f,
                        ),
                ),
        )
        val playPauseState = rememberPlayPauseButtonState(player)
        ImageControlsOverlay(
            isImageClip = image.isImageClip,
            bringIntoViewRequester = bringIntoViewRequester,
            onZoom = onZoom,
            onRotate = onRotate,
            onReset = onReset,
            moreOnClick = moreOnClick,
            playPauseOnClick = playPauseState::onClick,
            isPlaying = playPauseState.showPlay,
            modifier =
                Modifier
                    .fillMaxWidth(),
        )
    }
}
