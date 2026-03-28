package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

@Composable
fun TitleOrLogo(
    title: String?,
    logoImageUrl: String?,
    showLogo: Boolean,
    modifier: Modifier = Modifier,
) {
    var imageError by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.heightIn(max = HeaderUtils.logoHeight),
    ) {
        if (showLogo && logoImageUrl != null && !imageError) {
            AsyncImage(
                model = logoImageUrl,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .height(HeaderUtils.logoHeight)
                        .widthIn(max = 320.dp),
            )
        } else {
            Title(title, Modifier)
        }
    }
}

@Composable
private fun Title(
    title: String?,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title ?: "",
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun TitleOrLogo(
    item: BaseItem?,
    showLogo: Boolean,
    modifier: Modifier = Modifier,
) {
    val logoImageUrl = rememberLogoUrl(item)
    TitleOrLogo(
        title = item?.title,
        logoImageUrl = logoImageUrl,
        showLogo = showLogo,
        modifier = modifier,
    )
}

@Composable
fun rememberLogoUrl(item: BaseItem?): String? {
    val imageUrlService = LocalImageUrlService.current
    return remember(item?.id) {
        if (item?.type == BaseItemKind.EPISODE && item.data.seriesId != null && item.data.parentLogoImageTag != null) {
            imageUrlService.getItemImageUrl(item.data.seriesId!!, ImageType.LOGO)
        } else if (ImageType.LOGO in item?.data?.imageTags.orEmpty()) {
            imageUrlService.getItemImageUrl(item, ImageType.LOGO)
        } else {
            null
        }
    }
}
