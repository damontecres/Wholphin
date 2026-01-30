package com.github.damontecres.wholphin.ui.slideshow

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.components.StreamLabel
import com.github.damontecres.wholphin.ui.components.VideoStreamDetails
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import org.jellyfin.sdk.model.api.MediaType

@OptIn(UnstableApi::class)
@Composable
fun ImageDetailsHeader(
    slideshowEnabled: Boolean,
    slideshowControls: SlideshowControls,
    player: Player,
    image: ImageState,
    position: Int,
    count: Int,
    moreOnClick: () -> Unit,
    onZoom: (Float) -> Unit,
    onRotate: (Int) -> Unit,
    onReset: () -> Unit,
    onShowFilterDialogClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .bringIntoViewRequester(bringIntoViewRequester),
    ) {
        image.image.title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(.75f),
            )
        }
        if (image.image.ui.quickDetails
                .isNotNullOrBlank()
        ) {
            QuickDetails(image.image.ui.quickDetails, null)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StreamLabel("${position + 1} of $count")
            if (image.image.data.mediaType == MediaType.VIDEO) {
                VideoStreamDetails(
                    chosenStreams = image.chosenStreams,
                    numberOfVersions = 0,
                )
            } else {
                image.image.data.let {
                    if (it.width != null && it.height != null) {
                        StreamLabel("${it.width}x${it.height}")
                    }
                }
            }
        }
        OverviewText(
            overview = image.image.data.overview ?: "",
            maxLines = 3,
            onClick = {},
            modifier = Modifier.fillMaxWidth(.75f),
        )
        val playPauseState = rememberPlayPauseButtonState(player)
        ImageControlsOverlay(
            slideshowEnabled = slideshowEnabled,
            slideshowControls = slideshowControls,
            isImageClip = image.image.data.mediaType == MediaType.VIDEO,
            bringIntoViewRequester = bringIntoViewRequester,
            onZoom = onZoom,
            onRotate = onRotate,
            onReset = onReset,
            moreOnClick = moreOnClick,
            playPauseOnClick = playPauseState::onClick,
            isPlaying = playPauseState.showPlay,
            onShowFilterDialogClick = onShowFilterDialogClick,
            onDismiss = {},
            modifier =
                Modifier
                    .fillMaxWidth(),
        )
    }
}
