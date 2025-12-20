package com.github.damontecres.wholphin.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import com.github.damontecres.wholphin.R

private val MicIcon: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "Mic",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 14f)
                curveToRelative(1.66f, 0f, 2.99f, -1.34f, 2.99f, -3f)
                lineTo(15f, 5f)
                curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f)
                reflectiveCurveTo(9f, 3.34f, 9f, 5f)
                verticalLineToRelative(6f)
                curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
                close()
                moveTo(17.3f, 11f)
                curveToRelative(0f, 3f, -2.54f, 5.1f, -5.3f, 5.1f)
                reflectiveCurveTo(6.7f, 14f, 6.7f, 11f)
                lineTo(5f, 11f)
                curveToRelative(0f, 3.41f, 2.72f, 6.23f, 6f, 6.72f)
                lineTo(11f, 21f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-3.28f)
                curveToRelative(3.28f, -0.48f, 6f, -3.3f, 6f, -6.72f)
                horizontalLineToRelative(-1.7f)
                close()
            }
        }.build()
}

@Composable
fun VoiceSearchButton(
    onSpeechResult: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val isAvailable =
        remember {
            SpeechRecognizer.isRecognitionAvailable(context)
        }

    val speechLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val spokenText =
                    result.data
                        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                        ?.firstOrNull()
                if (!spokenText.isNullOrBlank()) {
                    onSpeechResult(spokenText)
                }
            }
        }

    if (isAvailable) {
        Button(
            onClick = {
                val intent =
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                        )
                        putExtra(
                            RecognizerIntent.EXTRA_PROMPT,
                            context.getString(R.string.voice_search_prompt),
                        )
                    }
                speechLauncher.launch(intent)
            },
            modifier =
                modifier.requiredSizeIn(
                    minWidth = MinButtonSize,
                    minHeight = MinButtonSize,
                    maxHeight = MinButtonSize,
                ),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                imageVector = MicIcon,
                contentDescription = stringResource(R.string.voice_search),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
