package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.TitleLogoDisplay
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.logCoilError
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

fun TitleLogoDisplay.orDefault(): TitleLogoDisplay =
    if (this == TitleLogoDisplay.UNRECOGNIZED) TitleLogoDisplay.PREFER_LOGO_ONLY else this

data class TitleLogoVisibility(
    val showLogo: Boolean,
    val showTitle: Boolean,
) {
    val showBoth: Boolean get() = showLogo && showTitle
}

fun TitleLogoDisplay.visibility(logoAvailable: Boolean): TitleLogoVisibility {
    val display = orDefault()
    val showLogo = display != TitleLogoDisplay.TITLE_ONLY && logoAvailable
    val showTitle = display != TitleLogoDisplay.PREFER_LOGO_ONLY || !showLogo
    return TitleLogoVisibility(showLogo, showTitle)
}

@Composable
fun TitleOrLogo(
    title: String?,
    logoImageUrl: String?,
    titleLogoDisplay: TitleLogoDisplay,
    modifier: Modifier = Modifier,
    bothModifier: Modifier = modifier,
) {
    var imageError by remember { mutableStateOf(false) }
    val visibility = titleLogoDisplay.visibility(logoImageUrl != null && !imageError)

    Box(
        modifier =
            (if (visibility.showBoth) bothModifier else modifier)
                .heightIn(max = HeaderUtils.logoHeight),
    ) {
        if (visibility.showLogo) {
            AsyncImage(
                model = logoImageUrl,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                onError = {
                    logCoilError(logoImageUrl, it.result)
                    imageError = true
                },
                modifier =
                    Modifier
                        .align(if (visibility.showBoth) Alignment.CenterEnd else Alignment.CenterStart)
                        .height(HeaderUtils.logoHeight)
                        .widthIn(max = 320.dp),
            )
        }
        if (visibility.showTitle) {
            Title(
                title,
                Modifier
                    .align(Alignment.CenterStart)
                    .then(if (visibility.showBoth) Modifier.fillMaxWidth(.6f) else Modifier),
            )
        }
    }
}

@Composable
internal fun Title(
    title: String?,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
) {
    Text(
        text = title ?: "",
        color = MaterialTheme.colorScheme.onSurface,
        style = style,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier,
    )
}

@Composable
fun TitleOrLogo(
    item: BaseItem?,
    titleLogoDisplay: TitleLogoDisplay,
    modifier: Modifier = Modifier,
    bothModifier: Modifier = modifier,
) {
    val logoImageUrl = rememberLogoUrl(item)
    TitleOrLogo(
        title = item?.title,
        logoImageUrl = logoImageUrl,
        titleLogoDisplay = titleLogoDisplay,
        modifier = modifier,
        bothModifier = bothModifier,
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
