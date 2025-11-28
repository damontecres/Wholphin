package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.TimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalTime

@Composable
fun BoxScope.TimeDisplay(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            now = LocalTime.now()
            delay(1000L)
        }
    }
    Text(
        text = TimeFormatter.format(now),
        fontSize = 18.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            modifier
                .align(Alignment.TopEnd)
                .padding(vertical = 16.dp, horizontal = 24.dp),
    )
}
