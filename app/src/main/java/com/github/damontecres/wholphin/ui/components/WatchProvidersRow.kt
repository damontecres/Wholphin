package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.github.damontecres.wholphin.R

data class WatchProviderDisplay(
    val name: String?,
    val logoUrl: String,
)

@Composable
fun WatchProvidersRow(
    providers: List<WatchProviderDisplay>,
    modifier: Modifier = Modifier,
) {
    if (providers.isEmpty()) return

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.currently_streaming_on),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 16.dp),
        ) {
            items(
                items = providers,
                key = { it.logoUrl },
            ) { provider ->
                WatchProviderLogo(provider)
            }
        }
    }
}

@Composable
private fun WatchProviderLogo(
    provider: WatchProviderDisplay,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model =
            ImageRequest
                .Builder(LocalContext.current)
                .data(provider.logoUrl)
                .build(),
        contentDescription = provider.name,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Preview(showBackground = true, backgroundColor = 11119017)
@Composable
private fun WatchProvidersRowPreview() {
    WatchProvidersRow(
        providers =
            listOf(
                WatchProviderDisplay(
                    name = "Netflix",
                    logoUrl = "https://upload.wikimedia.org/wikipedia/commons/0/08/Netflix_2015_logo.svg",
                ),
                WatchProviderDisplay(
                    name = "HBO Max",
                    logoUrl = "https://upload.wikimedia.org/wikipedia/commons/1/17/HBO_Max_Logo.svg",
                ),
                WatchProviderDisplay(
                    name = "Disney+",
                    logoUrl = "https://upload.wikimedia.org/wikipedia/commons/3/3e/Disney%2B_logo.svg",
                ),
            ),
    )
}
