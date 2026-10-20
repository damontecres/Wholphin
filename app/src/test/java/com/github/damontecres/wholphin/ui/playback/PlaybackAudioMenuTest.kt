package com.github.damontecres.wholphin.ui.playback

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import com.github.damontecres.wholphin.ui.performClickEnter
import com.github.damontecres.wholphin.ui.playback.overlay.PlaybackAction
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PlaybackAudioMenuTest {
    @get:Rule val compose = createComposeRule()
    private var dismissals = 0
    private val toggles = mutableListOf<Boolean>()

    private fun show(supported: Boolean = true) {
        compose.setContent {
            WholphinTheme {
                var type by remember { mutableStateOf<PlaybackDialogType?>(PlaybackDialogType.AUDIO) }
                var settings by remember {
                    mutableStateOf(
                        PlaybackSettings(
                            showDebugInfo = false,
                            audioIndex = 1,
                            audioStreams =
                                listOf(
                                    SimpleMediaStream(1, "English", "AAC stereo"),
                                    SimpleMediaStream(2, "Spanish", "AC3 surround"),
                                ),
                            subtitleIndex = null,
                            subtitleStreams = emptyList(),
                            playbackSpeed = 1f,
                            contentScale = ContentScale.Fit,
                            subtitleDelay = Duration.ZERO,
                            hasSubtitleDownloadPermission = false,
                            playbackSpeedEnabled = true,
                            levelingSupported = supported,
                        ),
                    )
                }
                type?.let { currentType ->
                    PlaybackDialog(
                        enableSubtitleDelay = true,
                        enableVideoScale = true,
                        type = currentType,
                        settings = settings,
                        onDismissRequest = {
                            dismissals++
                            type = if (currentType == PlaybackDialogType.AUDIO_TRACK) PlaybackDialogType.AUDIO else null
                        },
                        onControllerInteraction = {},
                        onClickPlaybackDialogType = { type = it },
                        onPlaybackActionClick = { action ->
                            when (action) {
                                is PlaybackAction.SetReduceLoudSounds -> {
                                    toggles.add(action.value)
                                    settings = settings.copy(reduceLoudSounds = action.value)
                                }

                                is PlaybackAction.ToggleAudio -> {
                                    settings = settings.copy(audioIndex = action.index)
                                }

                                else -> {
                                    error("Unexpected action: $action")
                                }
                            }
                        },
                        onChangeSubtitleDelay = {},
                    )
                }
            }
        }
    }

    @Test
    fun `audio menu has only track submenu and in-place boolean switch`() {
        show()
        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
        compose.onNodeWithText("Audio Track").assertIsDisplayed()
        compose.onNodeWithText("English").assertIsDisplayed()
        compose.onNodeWithText("Spanish").assertDoesNotExist()
        compose
            .onNodeWithText("Reduce Loud Sounds")
            .assertIsOff()
            .requestFocus()
            .assertIsFocused()
            .performClickEnter()
        compose
            .onNodeWithText("Reduce Loud Sounds")
            .assertIsOn()
            .assertIsFocused()
            .performClickEnter()
        compose.onNodeWithText("Reduce Loud Sounds").assertIsOff()
        compose.onNodeWithText("Audio Track").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(true, false), toggles)
            assertEquals(0, dismissals)
        }
    }

    @Test
    fun `audio track opens existing list and returns to parent showing selected track`() {
        show()
        compose
            .onNodeWithText("Audio Track")
            .requestFocus()
            .assertIsFocused()
            .performClickEnter()
        compose.onNodeWithText("Reduce Loud Sounds").assertDoesNotExist()
        compose
            .onNodeWithText("Spanish")
            .assertIsDisplayed()
            .requestFocus()
            .assertIsFocused()
            .performClickEnter()
        compose.onNodeWithText("Audio Track").assertIsDisplayed()
        compose.onNodeWithText("Spanish").assertIsDisplayed()
        compose.onNodeWithText("English").assertDoesNotExist()
        compose.onNodeWithText("Reduce Loud Sounds").assertIsOff()
    }

    @Test
    fun `unsupported backend shows only short ExoPlayer reason`() {
        show(supported = false)
        compose.onNodeWithText("Requires ExoPlayer").assertIsDisplayed()
        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
    }
}
