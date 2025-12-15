package com.github.damontecres.wholphin.ui.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transitionFactory
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropResult
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.CrossFadeFactory
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

/**
 * This is generally the root composable of the of the app
 *
 * Here the navigation backstack is used and pages are rendered in the nav drawer or full screen
 */
@Composable
fun ApplicationContent(
    server: JellyfinServer?,
    user: JellyfinUser?,
    navigationManager: NavigationManager,
    preferences: UserPreferences,
    backdrop: BackdropResult,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
    ) {
        SharedTransitionLayout(
            modifier = Modifier.fillMaxSize(),
        ) {
            AnimatedContent(
                backdrop,
                label = "backdrop_transition",
                modifier = Modifier.fillMaxSize(),
            ) { backdrop ->
                Timber.v("backdrop=$backdrop")
                val baseBackgroundColor = MaterialTheme.colorScheme.background
                if (backdrop.hasColors) {
                    Timber.v("All colors")
                    val animPrimary by animateColorAsState(
                        backdrop.dynamicColorPrimary,
                        animationSpec = tween(1250),
                        label = "primary",
                    )
                    val animSecondary by animateColorAsState(
                        backdrop.dynamicColorSecondary,
                        animationSpec = tween(1250),
                        label = "secondary",
                    )
                    val animTertiary by animateColorAsState(
                        backdrop.dynamicColorTertiary,
                        animationSpec = tween(1250),
                        label = "tertiary",
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    drawRect(color = baseBackgroundColor)
                                    // Top Left (Vibrant/Muted)
                                    drawRect(
                                        brush =
                                            Brush.radialGradient(
                                                colors = listOf(animSecondary, Color.Transparent),
                                                center = Offset(0f, 0f),
                                                radius = size.width * 0.8f,
                                            ),
                                    )
                                    // Bottom Right (DarkVibrant/DarkMuted)
                                    drawRect(
                                        brush =
                                            Brush.radialGradient(
                                                colors = listOf(animPrimary, Color.Transparent),
                                                center = Offset(size.width, size.height),
                                                radius = size.width * 0.8f,
                                            ),
                                    )
                                    // Bottom Left (Dark / Bridge)
                                    drawRect(
                                        brush =
                                            Brush.radialGradient(
                                                colors =
                                                    listOf(
                                                        baseBackgroundColor,
                                                        Color.Transparent,
                                                    ),
                                                center = Offset(0f, size.height),
                                                radius = size.width * 0.8f,
                                            ),
                                    )
                                    // Top Right (Under Image - Vibrant/Bright)
                                    drawRect(
                                        brush =
                                            Brush.radialGradient(
                                                colors = listOf(animTertiary, Color.Transparent),
                                                center = Offset(size.width, 0f),
                                                radius = size.width * 0.8f,
                                            ),
                                    )
                                },
                    )
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalContext.current)
                                .data(backdrop.imageUrl)
                                .transitionFactory(CrossFadeFactory(800.milliseconds))
                                .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.TopEnd,
                        modifier =
                            Modifier
                                .sharedElement(
                                    rememberSharedContentState(key = backdrop.imageUrl ?: ""),
                                    animatedVisibilityScope = this@AnimatedContent,
                                ).align(Alignment.TopEnd)
                                .fillMaxHeight(backdrop.fillHeight)
                                .fillMaxWidth(backdrop.fillWidth)
                                .alpha(.75f)
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush =
                                            Brush.horizontalGradient(
                                                colors = listOf(Color.Transparent, Color.Black),
                                                startX = 0f,
                                                endX = size.width * 0.6f,
                                            ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                    drawRect(
                                        brush =
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Black, Color.Transparent),
                                                startY = 0f,
                                                endY = size.height,
                                            ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                },
                    )
                }
            }
        }
        NavDisplay(
            backStack = navigationManager.backStack,
            onBack = { navigationManager.goBack() },
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider = { key ->
                key as Destination
                val contentKey = "${key}_${server?.id}_${user?.id}"
                NavEntry(key, contentKey = contentKey) {
                    if (key.fullScreen) {
                        DestinationContent(
                            destination = key,
                            preferences = preferences,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (user != null && server != null) {
                        NavDrawer(
                            destination = key,
                            preferences = preferences,
                            user = user,
                            server = server,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        ErrorMessage("Trying to go to $key without a user logged in", null)
                    }
                }
            },
        )
    }
}
