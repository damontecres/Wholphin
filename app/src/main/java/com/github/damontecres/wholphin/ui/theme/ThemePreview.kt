package com.github.damontecres.wholphin.ui.theme

import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.cards.BannerCard
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import com.github.damontecres.wholphin.ui.cards.WatchedIcon
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.nav.NavDrawerItem
import com.github.damontecres.wholphin.ui.nav.NavItem
import com.github.damontecres.wholphin.ui.playback.PlaybackButton
import com.github.damontecres.wholphin.ui.preferences.SliderPreference
import com.github.damontecres.wholphin.ui.preferences.SwitchPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Preview(
    device = "spec:width=1200dp,height=2000dp",
    backgroundColor = 0xFF383535,
    uiMode = UI_MODE_TYPE_TELEVISION,
)
@Composable
private fun ThemePreview() {
    Column {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(AppThemeColors.entries.filterNot { it == AppThemeColors.UNRECOGNIZED }) {
                ThemeExample(it)
            }
        }
    }
}

@Composable
private fun ThemeExample(theme: AppThemeColors) {
    val source = remember { PreviewInteractionSource() }
    WholphinTheme(appThemeColors = theme) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                Text(
                    text = theme.toString(),
                    color = Color.White,
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        WatchedIcon()
                        BannerCard(
                            name = "Card",
                            imageUrl = null,
                            onClick = { },
                            onLongClick = {},
                            playPercent = .5,
                            cardHeight = 64.dp,
                        )
                        BannerCard(
                            name = "Card",
                            imageUrl = null,
                            onClick = { },
                            onLongClick = {},
                            playPercent = .5,
                            cardHeight = 64.dp,
                            interactionSource = source,
                        )
                        SeasonCard(
                            title = "Card",
                            subtitle = "2025",
                            name = "N/A",
                            imageUrl = "abc",
                            isFavorite = true,
                            isPlayed = true,
                            unplayedItemCount = 2,
                            playedPercentage = 50.0,
                            onClick = { },
                            onLongClick = {},
                            imageHeight = 120.dp,
                            interactionSource = source,
                            showImageOverlay = true,
                            aspectRatio = AspectRatios.TALL,
                        )
                        SeasonCard(
                            title = "Watched",
                            subtitle = "2025",
                            name = "N/A",
                            imageUrl = "abc",
                            isFavorite = false,
                            isPlayed = true,
                            unplayedItemCount = 2,
                            playedPercentage = 0.0,
                            onClick = { },
                            onLongClick = {},
                            imageHeight = 120.dp,
//                        interactionSource = source,
                            showImageOverlay = true,
                            aspectRatio = AspectRatios.SQUARE,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SliderPreference(
                            preference = AppPreference.AutoPlayNextDelay,
                            title = "Slider",
                            summary = "5 seconds",
                            value = 30,
                            onChange = {},
                            modifier = Modifier.weight(1f),
                        )
                        SliderPreference(
                            preference = AppPreference.AutoPlayNextDelay,
                            title = "Slider",
                            summary = "5 seconds",
                            value = 30,
                            onChange = {},
                            modifier = Modifier.weight(1f),
                            interactionSource = source,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SwitchPreference(
                            title = "Switch",
                            value = false,
                            onClick = {},
                            summaryOn = "Enabled",
                            summaryOff = "Disabled",
                            modifier = Modifier.weight(1f),
                        )
                        SwitchPreference(
                            title = "Switch",
                            value = false,
                            onClick = {},
                            summaryOn = "Enabled",
                            summaryOff = "Disabled",
                            modifier = Modifier.weight(1f),
                            interactionSource = source,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SwitchPreference(
                            title = "Switch",
                            value = true,
                            onClick = {},
                            summaryOn = "Enabled",
                            summaryOff = "Disabled",
                            modifier = Modifier.weight(1f),
                        )
                        SwitchPreference(
                            title = "Switch",
                            value = true,
                            onClick = {},
                            summaryOn = "Enabled",
                            summaryOff = "Disabled",
                            modifier = Modifier.weight(1f),
                            interactionSource = source,
                        )
                    }
                    val navScope = NavScope(true)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
                    ) {
                        navScope.apply {
                            NavItem(
                                library = NavDrawerItem.Favorites,
                                onClick = { },
                                selected = false,
                                moreExpanded = false,
                                drawerOpen = true,
                                modifier = Modifier,
                            )
                            NavItem(
                                library = NavDrawerItem.Favorites,
                                onClick = { },
                                selected = false,
                                moreExpanded = false,
                                drawerOpen = true,
                                modifier = Modifier,
                                interactionSource = source,
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
                    ) {
                        navScope.apply {
                            NavItem(
                                library = NavDrawerItem.Favorites,
                                onClick = { },
                                selected = true,
                                moreExpanded = false,
                                drawerOpen = true,
                                modifier = Modifier,
                            )
                            NavItem(
                                library = NavDrawerItem.Favorites,
                                onClick = { },
                                selected = true,
                                moreExpanded = false,
                                drawerOpen = true,
                                modifier = Modifier,
                                interactionSource = source,
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        PlaybackButton(
                            modifier = Modifier,
                            iconRes = R.drawable.baseline_play_arrow_24,
                            onClick = {},
                            onControllerInteraction = {},
                        )
                        PlaybackButton(
                            modifier = Modifier,
                            iconRes = R.drawable.baseline_play_arrow_24,
                            onClick = {},
                            onControllerInteraction = {},
                            interactionSource = source,
                        )
                        PlaybackButton(
                            modifier = Modifier,
                            iconRes = R.drawable.baseline_pause_24,
                            onClick = {},
                            onControllerInteraction = {},
                        )
                        PlaybackButton(
                            modifier = Modifier,
                            iconRes = R.drawable.baseline_pause_24,
                            onClick = {},
                            onControllerInteraction = {},
                            interactionSource = source,
                        )
                    }
                }
            }
        }
    }
}

class PreviewInteractionSource : MutableInteractionSource {
    override val interactions: Flow<Interaction>
        get() = flowOf(FocusInteraction.Focus())

    override suspend fun emit(interaction: Interaction) {
    }

    override fun tryEmit(interaction: Interaction): Boolean = false
}

class NavScope(
    override val hasFocus: Boolean,
) : NavigationDrawerScope
