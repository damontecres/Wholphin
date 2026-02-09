package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.contentColorFor
import com.github.damontecres.wholphin.preferences.AppSliderPreference
import com.github.damontecres.wholphin.ui.components.SliderBar

@Composable
fun SliderPreference(
    preference: AppSliderPreference<*>,
    title: String,
    summary: String?,
    value: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    summaryBelow: Boolean = false,
    heightAdjustment: Dp = 0.dp,
    additionalSummary: @Composable (ColumnScope.() -> Unit)? = null,
) {
    val focused = interactionSource.collectIsFocusedAsState().value
    val background =
        if (focused) {
            MaterialTheme.colorScheme.inverseSurface
        } else {
            Color.Unspecified
        }
    val contentColor = contentColorFor(background)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
//                .height(80.dp) // not dense
                .height(72.dp + heightAdjustment) // dense
                .fillMaxWidth()
                .background(background, shape = RoundedCornerShape(8.dp))
                .padding(PaddingValues(horizontal = 12.dp, vertical = 10.dp)), // dense,
    ) {
        PreferenceTitle(title, color = contentColor)

        ProvideTextStyle(PreferenceSummaryStyle.copy(color = contentColor)) {
            additionalSummary?.invoke(this)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxSize(),
        ) {
            SliderBar(
                value = value,
                min = preference.min,
                max = preference.max,
                interval = preference.interval,
                onChange = onChange,
                enableWrapAround = false,
                interactionSource = interactionSource,
                modifier = Modifier.weight(1f),
            )

            if (!summaryBelow) {
                PreferenceSummary(summary, color = contentColor)
            }
        }
        if (summaryBelow) {
            PreferenceSummary(summary, color = contentColor)
        }
    }
}
