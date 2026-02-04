package com.github.damontecres.wholphin.ui.preferences.subtitle

import android.content.pm.ActivityInfo
import android.os.Build
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.SubtitlePreferences
import com.github.damontecres.wholphin.preferences.resetSubtitles
import com.github.damontecres.wholphin.preferences.updateInterfacePreferences
import com.github.damontecres.wholphin.ui.findActivity
import com.github.damontecres.wholphin.ui.preferences.PreferencesViewModel
import com.github.damontecres.wholphin.ui.preferences.subtitle.SubtitleSettings.calculateEdgeSize
import com.github.damontecres.wholphin.ui.preferences.subtitle.SubtitleSettings.toSubtitleStyle
import com.github.damontecres.wholphin.util.Media3SubtitleOverride
import timber.log.Timber

@OptIn(UnstableApi::class)
@Composable
fun SubtitleStylePage(
    initialPreferences: AppPreferences,
    hdrSettings: Boolean,
    modifier: Modifier = Modifier,
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var preferences by remember { mutableStateOf(initialPreferences) }
    LaunchedEffect(Unit) {
        viewModel.preferenceDataStore.data.collect {
            preferences = it
        }
    }
    val display = LocalView.current.display

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        DisposableEffect(context) {
            if (hdrSettings) {
                Timber.v("Switching color mode to HDR")
                context.findActivity()?.window?.colorMode = ActivityInfo.COLOR_MODE_HDR
            }
            onDispose {
                context.findActivity()?.window?.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
            }
        }
    }
    val prefs =
        if (hdrSettings) {
            preferences.interfacePreferences.hdrSubtitlesPreferences
        } else {
            preferences.interfacePreferences.subtitlesPreferences
        }
    var focusedOnMargin by remember { mutableStateOf(false) }
    var focusedOnImageOpacity by remember { mutableStateOf(false) }

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
            if (!focusedOnMargin && !focusedOnImageOpacity) {
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
                                Media3SubtitleOverride(prefs.calculateEdgeSize(density)).apply(it)
                            },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(bottom = padding),
                        )
                    }
                }
            } else if (focusedOnMargin) {
                // Margin
                AndroidView(
                    factory = { context ->
                        SubtitleView(context)
                    },
                    update = {
                        it.setStyle(prefs.toSubtitleStyle())
                        it.setFixedTextSize(Dimension.SP, prefs.fontSize.toFloat())
                        it.setCues(
                            listOf(
                                Cue.Builder().setText("Subtitles margin below here").build(),
                            ),
                        )
                        it.setBottomPaddingFraction(prefs.margin.toFloat() / 100f)
                    },
                    modifier =
                        Modifier
                            .fillMaxSize(),
                )
            } else if (focusedOnImageOpacity) {
                AndroidView(
                    factory = { context ->
                        SubtitleView(context)
                    },
                    update = {
                        it.setStyle(
                            SubtitlePreferences
                                .newBuilder()
                                .apply {
                                    resetSubtitles()
                                }.build()
                                .toSubtitleStyle(),
                        )
                        it.setCues(
                            listOf(
                                Cue
                                    .Builder()
                                    .setText("ExoPlayer only:\nImage based subtitles can be dimmed.")
                                    .build(),
                            ),
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .alpha(prefs.imageSubtitleOpacity / 100f),
                )
            }
        }
        val display = LocalView.current.display
        val prefList =
            remember(hdrSettings, display) {
                if (!hdrSettings && SubtitleSettings.shouldShowHdr(display)) {
                    // If not on HDR page and display is HDR capable, then show the HDR button
                    SubtitleSettings.preferences + SubtitleSettings.hdrPreferenceGroup
                } else {
                    SubtitleSettings.preferences
                }
            }
        SubtitlePreferencesContent(
            title =
                if (hdrSettings) {
                    stringResource(R.string.hdr_subtitle_style)
                } else {
                    stringResource(R.string.subtitle_style)
                },
            preferences =
                if (hdrSettings) {
                    preferences.interfacePreferences.hdrSubtitlesPreferences
                } else {
                    preferences.interfacePreferences.subtitlesPreferences
                },
            prefList = prefList,
            onPreferenceChange = { newSubtitlePrefs ->
                viewModel.preferenceDataStore.updateData {
                    it.updateInterfacePreferences {
                        if (hdrSettings) {
                            hdrSubtitlesPreferences = newSubtitlePrefs
                        } else {
                            subtitlesPreferences = newSubtitlePrefs
                        }
                    }
                }
            },
            onFocus = { groupIndex, prefIndex ->
                val focusedPref =
                    SubtitleSettings.preferences
                        .getOrNull(groupIndex)
                        ?.preferences
                        ?.getOrNull(prefIndex)
                focusedOnMargin = focusedPref == SubtitleSettings.Margin
                focusedOnImageOpacity = focusedPref == SubtitleSettings.ImageOpacity
            },
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(.25f),
        )
    }
}
