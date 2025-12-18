package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.util.LocalClock

@Composable
fun BoxScope.TimeDisplay(modifier: Modifier = Modifier) {
    Text(
        text = LocalClock.current.timeString,
        fontSize = 18.sp,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge,
        modifier =
            modifier
                .align(Alignment.TopEnd)
                .padding(vertical = 16.dp, horizontal = 24.dp),
    )
}
