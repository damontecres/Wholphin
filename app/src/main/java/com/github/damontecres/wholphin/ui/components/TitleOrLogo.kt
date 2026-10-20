package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.CoilTrimTransformation
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.logCoilError
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
    // The logo keeps its own shape within these two limits: a wide logo is stopped by the
    // width, a tall or square one by the height
    val logoMaxWidth = 260.dp
    val logoMaxHeight = 85.dp
    // Width to height of the logo, known once it has loaded
    var logoAspect by remember(logoImageUrl) { mutableStateOf<Float?>(null) }
    Box(
        modifier = modifier.heightIn(max = logoMaxHeight),
    ) {
        if (showLogo && logoImageUrl != null && !imageError) {
            val context = LocalContext.current
            // Empty see-through space around the logo is cut away first, so the size limits
            // apply to the logo itself
            val logoRequest =
                remember(logoImageUrl) {
                    ImageRequest
                        .Builder(context)
                        .data(logoImageUrl)
                        .transformations(CoilTrimTransformation())
                        .build()
                }
            AsyncImage(
                model = logoRequest,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                onError = {
                    logCoilError(logoImageUrl, it.result)
                    imageError = true
                },
                onSuccess = {
                    val image = it.result.image
                    if (image.width > 0 && image.height > 0) {
                        logoAspect = image.width.toFloat() / image.height
                    }
                },
                modifier =
                    logoAspect.let { aspect ->
                        if (aspect != null) {
                            // As wide as the height limit allows, but never past the width limit
                            val width = minOf(logoMaxWidth, logoMaxHeight * aspect)
                            Modifier.size(width, width / aspect)
                        } else {
                            Modifier
                                .widthIn(max = logoMaxWidth)
                                .heightIn(max = logoMaxHeight)
                        }
                    },
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
        if ((item?.type == BaseItemKind.EPISODE || item?.type == BaseItemKind.SEASON) &&
            item.data.seriesId != null &&
            item.data.parentLogoImageTag != null
        ) {
            imageUrlService.getItemImageUrl(
                itemId = item.data.seriesId!!,
                imageType = ImageType.LOGO,
                tag = item.data.parentLogoImageTag.takeIf { item.data.parentLogoItemId == item.data.seriesId },
            )
        } else if (ImageType.LOGO in item?.data?.imageTags.orEmpty()) {
            imageUrlService.getItemImageUrl(item, ImageType.LOGO)
        } else {
            null
        }
    }
}

@Composable
fun SimpleTitleOrLogo(
    item: BaseItem?,
    showLogo: Boolean,
    modifier: Modifier = Modifier,
) {
    val logoImageUrl = rememberLogoUrl(item)
    var imageError by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        if (showLogo && logoImageUrl != null && !imageError) {
            AsyncImage(
                model = logoImageUrl,
                contentDescription = item?.title,
                contentScale = ContentScale.Fit,
                onError = {
                    logCoilError(logoImageUrl, it.result)
                    imageError = true
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Title(item?.title, Modifier)
        }
    }
}
