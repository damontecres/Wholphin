package com.github.damontecres.wholphin.ui.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
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
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            AnimatedContent(
                backdrop,
                label = "backdrop_transition",
                modifier = Modifier,
            ) { backdrop ->
                val gradientColor = MaterialTheme.colorScheme.background
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
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, gradientColor),
                                        startY = size.height * .5f,
                                    ),
                                )
                                drawRect(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, gradientColor),
                                        endX = 0f,
                                        startX = size.width * .75f,
                                    ),
                                )
                            },
                )
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
