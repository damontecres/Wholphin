package com.github.damontecres.wholphin.ui.preferences.subtitle

import androidx.annotation.Dimension
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption
import com.github.damontecres.wholphin.ui.preferences.PreferencesContent
import com.github.damontecres.wholphin.ui.preferences.PreferencesViewModel
import com.github.damontecres.wholphin.ui.preferences.subtitle.SubtitleSettings.toSubtitleStyle
import timber.log.Timber

@OptIn(UnstableApi::class)
@Composable
fun SubtitleStylePage(
    initialPreferences: AppPreferences,
    modifier: Modifier = Modifier,
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    var preferences by remember { mutableStateOf(initialPreferences) }
    LaunchedEffect(Unit) {
        viewModel.preferenceDataStore.data.collect {
            preferences = it
        }
    }
    Timber.v("SubtitleStylePage")
    val prefs = preferences.interfacePreferences.subtitlesPreferences
    Row(
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier =
                Modifier
                    .fillMaxSize()
                    .weight(1f),
        ) {
            Image(
                painter = painterResource(R.mipmap.eclipse),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier =
                    Modifier
                        .fillMaxSize(),
            )
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier =
                    Modifier
                        .padding(16.dp)
                        .fillMaxSize(),
            ) {
                val examples =
                    mapOf(
                        "Subtitles will look like this" to 48.dp,
                        "This is another example" to 24.dp,
                        "Longer multi line subtitles will\nlook like this" to 0.dp,
                    )
                examples.forEach { (text, padding) ->
                    AndroidView(
                        factory = { context ->
                            SubtitleView(context)
                        },
                        update = {
                            it.setStyle(prefs.toSubtitleStyle())
                            it.setFixedTextSize(Dimension.SP, prefs.fontSize.toFloat())
                            it.setCues(
                                listOf(
                                    Cue.Builder().setText(text).build(),
                                ),
                            )
                        },
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(bottom = padding),
                    )
                }
            }
        }
        PreferencesContent(
            initialPreferences = preferences,
            preferenceScreenOption = PreferenceScreenOption.SUBTITLES,
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(.25f),
        )
    }
}
