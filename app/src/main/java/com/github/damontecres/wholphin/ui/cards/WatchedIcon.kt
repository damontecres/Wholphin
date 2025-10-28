package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.theme.WholphinTheme

@Composable
fun WatchedIcon(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        tint = Color.White,
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.border.copy(alpha = 1f), shape = CircleShape)
                .border(.5.dp, Color.Black, CircleShape)
                .padding(2.dp),
    )
}

@PreviewTvSpec
@Composable
private fun WatchedIconPreview() {
    WholphinTheme {
        WatchedIcon(Modifier.size(64.dp))
    }
}
