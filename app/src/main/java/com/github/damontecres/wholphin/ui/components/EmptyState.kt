package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.FontAwesome

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier = modifier.fillMaxSize().padding(32.dp),
    ) {
        Text(
            text = stringResource(R.string.fa_film),
            fontFamily = FontAwesome,
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = stringResource(R.string.no_media_found),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
