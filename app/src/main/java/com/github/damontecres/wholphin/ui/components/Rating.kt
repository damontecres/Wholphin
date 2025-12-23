package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.playOnClickSound
import com.github.damontecres.wholphin.ui.playSoundOnFocus
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import java.util.Locale

enum class StarRatingPrecision {
    FULL,
    HALF,
    QUARTER,
    ;

    companion object {
        fun fromFloat(value: Float): StarRatingPrecision =
            if (value <= .25f) {
                QUARTER
            } else if (value <= .5f) {
                HALF
            } else {
                FULL
            }
    }
}

val FilledStarColor = Color(0xFFFFC700)
val EmptyStarColor = Color(0xFF5D4A0A)

val ratingBarHeight: Dp = 32.dp

@Composable
fun SimpleStarRating(
    communityRating: Float?,
    modifier: Modifier = Modifier,
) = CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    SimpleStarRating(
        text = communityRating?.let { String.format(Locale.getDefault(), "%.1f", it) },
        modifier = modifier,
    )
}

@Composable
fun SimpleStarRating(
    text: String?,
    modifier: Modifier = Modifier,
    starColor: Color = FilledStarColor,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        if (text.isNotNullOrBlank()) {
            Text(
                text = text,
                maxLines = 1,
                modifier = Modifier,
            )
            val height = with(LocalDensity.current) { LocalTextStyle.current.fontSize.toDp() }
            Icon(
                imageVector = Icons.Filled.Star,
                tint = starColor,
                contentDescription = null,
                modifier = Modifier.height(height),
            )
        }
    }
}

@Composable
fun TomatoRating(
    rating: Float?,
    modifier: Modifier = Modifier,
    threshold: Float = 60f,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        if (rating != null) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Text(
                    text = rating.toInt().toString() + "%",
                    maxLines = 1,
                    modifier = Modifier,
                )
            }
            val height = with(LocalDensity.current) { LocalTextStyle.current.fontSize.toDp() * .8f }
            Icon(
                painter =
                    painterResource(
                        if (rating >= threshold) {
                            R.drawable.ic_rotten_tomatoes_fresh
                        } else {
                            R.drawable.ic_rotten_tomatoes_rotten
                        },
                    ),
                contentDescription = null,
                modifier = Modifier.height(height),
                tint = Color.Unspecified,
            )
        }
    }
}

/**
 * Shows a rating out of 5 stars
 */
@Composable
fun StarRating(
    rating100: Int,
    onRatingChange: (Int) -> Unit,
    precision: StarRatingPrecision,
    enabled: Boolean,
    playSoundOnFocus: Boolean,
    modifier: Modifier = Modifier,
    bgColor: Color = AppColors.TransparentBlack75, // MaterialTheme.colorScheme.background,
) {
    val context = LocalContext.current
    var tempRating by remember(rating100) { mutableIntStateOf(rating100) }
    val percentage = (if (enabled) tempRating else rating100) / 100f
    val focusRequesters = remember { List(5) { FocusRequester() } }
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(bgColor),
    ) {
        LazyRow(
            modifier =
                Modifier
                    .selectableGroup()
                    .padding(4.dp)
                    .drawWithCache {
                        onDrawWithContent {
                            drawContent()
                            if (percentage in 0f..<1f) {
                                drawRect(
                                    color = bgColor,
                                    topLeft = Offset(size.width * percentage, 0f),
                                    blendMode = BlendMode.SrcAtop,
                                )
                            }
                        }
                    }.focusGroup()
                    .focusProperties {
                        onEnter = {
                            val index =
                                if (rating100 <= 20) {
                                    0
                                } else if (rating100 <= 40) {
                                    1
                                } else if (rating100 <= 60) {
                                    2
                                } else if (rating100 <= 80) {
                                    3
                                } else {
                                    4
                                }
                            focusRequesters[index].tryRequestFocus()
                        }
                    },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            for (i in 1..5) {
                item {
                    val isRated = (if (enabled) tempRating else rating100) >= (i * 20)
                    val icon = Icons.Filled.Star
                    var focused by remember { mutableStateOf(false) }

                    val focusedColor =
                        if (focused) {
                            MaterialTheme.colorScheme.border
                        } else {
                            Color.Unspecified
                        }
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(focusedColor),
                    ) {
                        Icon(
                            imageVector = icon,
                            tint = FilledStarColor,
                            contentDescription = null,
                            modifier =
                                if (enabled) {
                                    Modifier
                                        .onFocusChanged {
                                            focused = it.isFocused
                                            if (it.isFocused) {
                                                tempRating = i * 20
                                            } else {
                                                tempRating = rating100
                                            }
                                        }.playSoundOnFocus(playSoundOnFocus)
                                        .focusRequester(focusRequesters[i - 1])
                                        .focusProperties {
                                            left =
                                                if (i == 1) focusRequesters.last() else FocusRequester.Default
                                            right =
                                                if (i == 5) focusRequesters.first() else FocusRequester.Default
                                        }.selectable(
                                            selected = isRated,
                                            onClick = {
                                                if (playSoundOnFocus) playOnClickSound(context)
                                                val newRating100 =
                                                    when (precision) {
                                                        StarRatingPrecision.FULL -> {
                                                            if (i == 1 && rating100 > 0 && rating100 <= 20) 0 else i * 20
                                                        }

                                                        StarRatingPrecision.HALF -> {
                                                            if (rating100 > i * 20) {
                                                                i * 20
                                                            } else if (rating100 == i * 20) {
                                                                i * 20 - 10
                                                            } else if (rating100 == i * 20 - 10) {
                                                                (i - 1) * 20
                                                            } else if (i == 1 && rating100 == 0) {
                                                                20
                                                            } else if (i == 1 && rating100 > 10) {
                                                                10
                                                            } else if (i == 1 && rating100 <= 10) {
                                                                0
                                                            } else {
                                                                (i) * 20
                                                            }
                                                        }

                                                        StarRatingPrecision.QUARTER -> {
                                                            // TODO
                                                            null
                                                        }
                                                    }
                                                if (newRating100 != null) {
                                                    tempRating = newRating100
                                                    onRatingChange(newRating100)
                                                }
                                            },
                                        )
                                } else {
                                    Modifier
                                }.fillMaxHeight()
                                    .aspectRatio(1f),
                        )
                    }
                }
            }
        }
    }
}

@PreviewTvSpec
@Composable
private fun SimpleStarRatingPreview() {
    WholphinTheme {
        Column {
            SimpleStarRating(7.5f, Modifier.height(32.dp))
            TomatoRating(75f)
            SimpleStarRating(7.5f, Modifier.height(20.dp))
            SimpleStarRating("", Modifier.height(32.dp))
            TomatoRating(75f)
            TomatoRating(40f)
            TomatoRating(3f)
        }
    }
}
