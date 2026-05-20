package com.github.damontecres.wholphin.ui.detail.livetv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.useExistingImageAsPlaceholder
import coil3.request.ImageRequest
import coil3.request.transitionFactory
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.CrossFadeFactory
import com.github.damontecres.wholphin.ui.components.EpisodeName
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.components.StreamLabel
import com.github.damontecres.wholphin.ui.components.TitleOrLogo
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoilApi::class)
@Composable
fun TvGuideHeader(
    program: TvProgram?,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            TitleOrLogo(
                title = program?.name ?: program?.id.toString(),
                logoImageUrl = null,
                showLogo = false,
                modifier =
                    Modifier
                        .padding(start = HeaderUtils.startPadding)
                        .fillMaxWidth(.75f),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(.6f),
            ) {
                program?.subtitle?.let {
                    EpisodeName(
                        episodeName = program.subtitle,
                        modifier = Modifier.padding(start = HeaderUtils.startPadding),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = HeaderUtils.startPadding),
                ) {
                    program?.quickDetails?.let {
                        QuickDetails(
                            it,
                            null,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    if (program?.isRepeat == true) {
                        StreamLabel(stringResource(R.string.live_tv_repeat))
                    }
                }
                OverviewText(
                    overview = program?.overview ?: "",
                    maxLines = 3,
                    onClick = {},
                    enabled = false,
                )
            }
        }

        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalContext.current)
                    .data(program?.imageUrl)
                    .transitionFactory(CrossFadeFactory(300.milliseconds))
                    .useExistingImageAsPlaceholder(true)
                    .build(),
            contentDescription = null,
        )
    }
}
