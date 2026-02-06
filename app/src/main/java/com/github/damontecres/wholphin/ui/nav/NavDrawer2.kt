package com.github.damontecres.wholphin.ui.nav

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawerItemBorder
import androidx.tv.material3.NavigationDrawerItemColors
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerItemGlow
import androidx.tv.material3.NavigationDrawerItemScale
import androidx.tv.material3.NavigationDrawerItemShape
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.rememberDrawerState

@Composable
fun ModalNavigationDrawer(
    drawerContent: @Composable NavigationDrawerScope.(DrawerValue) -> Unit,
    modifier: Modifier = Modifier,
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed),
    scrimBrush: Brush = SolidColor(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
    content: @Composable () -> Unit,
) {
    val localDensity = LocalDensity.current
    val closedDrawerWidth: MutableState<Dp?> = remember { mutableStateOf(null) }
    val internalDrawerModifier =
        Modifier.zIndex(Float.MAX_VALUE).onSizeChanged {
            if (closedDrawerWidth.value == null && drawerState.currentValue == DrawerValue.Closed) {
                with(localDensity) { closedDrawerWidth.value = it.width.toDp() }
            }
        }

    Box(modifier = modifier) {
        DrawerSheet(
            modifier = internalDrawerModifier.align(Alignment.CenterStart),
            drawerState = drawerState,
            sizeAnimationFinishedListener = { _, targetSize ->
                if (drawerState.currentValue == DrawerValue.Closed) {
                    with(localDensity) { closedDrawerWidth.value = targetSize.width.toDp() }
                }
            },
            content = drawerContent,
        )

        content()

        if (drawerState.currentValue == DrawerValue.Open) {
            // Scrim
            Canvas(Modifier.fillMaxSize()) { drawRect(scrimBrush) }
        }
    }
}

@Composable
private fun DrawerSheet(
    modifier: Modifier = Modifier,
    drawerState: DrawerState = remember { DrawerState() },
    sizeAnimationFinishedListener: ((initialValue: IntSize, targetValue: IntSize) -> Unit)? = null,
    content: @Composable NavigationDrawerScope.(DrawerValue) -> Unit,
) {
    // indicates that the drawer has been set to its initial state and has grabbed focus if
    // necessary. Controls whether focus is used to decide the state of the drawer going forward.
    var initializationComplete: Boolean by remember { mutableStateOf(false) }
    var focusState by remember { mutableStateOf<FocusState?>(null) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(key1 = drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open && focusState?.hasFocus == false) {
            // used to grab focus if the drawer state is set to Open on start.
            focusRequester.requestFocus()
        }
        initializationComplete = true
    }

    val internalModifier =
        Modifier
            .focusRequester(focusRequester)
            .animateContentSize(
//                finishedListener = sizeAnimationFinishedListener,
                animationSpec =
                    spring(
                        stiffness = Spring.StiffnessMedium,
                        visibilityThreshold = IntSize.VisibilityThreshold,
                    ),
            ).fillMaxHeight()
            // adding passed-in modifier here to ensure animateContentSize is called before other
            // size based modifiers.
            .then(modifier)
            .onFocusChanged {
                focusState = it

                if (initializationComplete) {
                    drawerState.setValue(if (it.hasFocus) DrawerValue.Open else DrawerValue.Closed)
                }
            }.focusGroup()

    Box(modifier = internalModifier) {
        NavigationDrawerScopeImpl(drawerState.currentValue == DrawerValue.Open).apply {
            content(drawerState.currentValue)
        }
    }
}

internal class NavigationDrawerScopeImpl(
    override val hasFocus: Boolean,
) : NavigationDrawerScope

val CollapsedDrawerItemWidth = 64.dp
val ExpandedDrawerItemWidth = 256.dp
val DrawerIconSize = 32.dp

@Composable
fun NavigationDrawerScope.NavigationDrawerItem(
    selected: Boolean,
    onClick: () -> Unit,
    leadingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    tonalElevation: Dp = NavigationDrawerItemDefaults.NavigationDrawerItemElevation,
    shape: NavigationDrawerItemShape =
        NavigationDrawerItemDefaults.shape(
            RoundedCornerShape(8.dp),
        ),
    colors: NavigationDrawerItemColors = NavigationDrawerItemDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val animatedWidth by
        animateDpAsState(
            targetValue =
                if (hasFocus) {
                    ExpandedDrawerItemWidth
                } else {
                    CollapsedDrawerItemWidth
                },
            label = "NavigationDrawerItem width open/closed state of the drawer item",
        )
    val navDrawerItemHeight =
        if (supportingContent == null) {
            NavigationDrawerItemDefaults.ContainerHeightOneLine
        } else {
            NavigationDrawerItemDefaults.ContainerHeightTwoLine
        }
    ListItem(
        selected = selected,
        onClick = onClick,
        headlineContent = {
            content()
//            AnimatedVisibility(
//                visible = hasFocus,
//                enter = NavigationDrawerItemDefaults.ContentAnimationEnter,
//                exit = NavigationDrawerItemDefaults.ContentAnimationExit,
//            ) {
//                content()
//            }
        },
        leadingContent = {
//            Box(Modifier.size(NavigationDrawerItemDefaults.IconSize)) { leadingContent() }
            Box(Modifier.size(ListItemDefaults.IconSize)) { leadingContent() }
        },
        trailingContent =
            trailingContent?.let {
                {
                    it()
//                    AnimatedVisibility(
//                        visible = hasFocus,
//                        enter = NavigationDrawerItemDefaults.ContentAnimationEnter,
//                        exit = NavigationDrawerItemDefaults.ContentAnimationExit,
//                    ) {
//
//                    }
                }
            },
        supportingContent =
            supportingContent?.let {
                {
                    it()
//                    AnimatedVisibility(
//                        visible = hasFocus,
//                        enter = NavigationDrawerItemDefaults.ContentAnimationEnter,
//                        exit = NavigationDrawerItemDefaults.ContentAnimationExit,
//                    ) {
//
//                    }
                }
            },
        modifier =
            modifier
                .layout { measurable, constraints ->
                    val width = animatedWidth.roundToPx()
                    val height = navDrawerItemHeight.roundToPx()
                    val placeable =
                        measurable.measure(
                            constraints.copy(
                                minWidth = width,
                                maxWidth = width,
                                minHeight = height,
                                maxHeight = height,
                            ),
                        )
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                },
        enabled = enabled,
        onLongClick = onLongClick,
        tonalElevation = tonalElevation,
//        shape = shape.toToggleableListItemShape(),
        colors = colors.toToggleableListItemColors(hasFocus),
        scale = ListItemDefaults.scale(1f, 1f),
//        scale = scale.toToggleableListItemScale(),
        interactionSource = interactionSource,
    )
}

@Composable
private fun NavigationDrawerItemShape.toToggleableListItemShape() =
    ListItemDefaults.shape(
        shape = shape,
        focusedShape = focusedShape,
        pressedShape = pressedShape,
        selectedShape = selectedShape,
        disabledShape = disabledShape,
        focusedSelectedShape = focusedSelectedShape,
        focusedDisabledShape = focusedDisabledShape,
        pressedSelectedShape = pressedSelectedShape,
    )

@Composable
private fun NavigationDrawerItemColors.toToggleableListItemColors(doesNavigationDrawerHaveFocus: Boolean) =
    ListItemDefaults.colors(
        containerColor = containerColor,
        contentColor = if (doesNavigationDrawerHaveFocus) contentColor else inactiveContentColor,
        focusedContainerColor = focusedContainerColor,
        focusedContentColor = focusedContentColor,
        pressedContainerColor = pressedContainerColor,
        pressedContentColor = pressedContentColor,
        selectedContainerColor = selectedContainerColor,
        selectedContentColor = selectedContentColor,
        disabledContainerColor = disabledContainerColor,
        disabledContentColor =
            if (doesNavigationDrawerHaveFocus) {
                disabledContentColor
            } else {
                disabledInactiveContentColor
            },
        focusedSelectedContainerColor = focusedSelectedContainerColor,
        focusedSelectedContentColor = focusedSelectedContentColor,
        pressedSelectedContainerColor = pressedSelectedContainerColor,
        pressedSelectedContentColor = pressedSelectedContentColor,
    )

@Composable
private fun NavigationDrawerItemScale.toToggleableListItemScale() =
    ListItemDefaults.scale(
        scale = scale,
        focusedScale = focusedScale,
        pressedScale = pressedScale,
        selectedScale = selectedScale,
        disabledScale = disabledScale,
        focusedSelectedScale = focusedSelectedScale,
        focusedDisabledScale = focusedDisabledScale,
        pressedSelectedScale = pressedSelectedScale,
    )

@Composable
private fun NavigationDrawerItemBorder.toToggleableListItemBorder() =
    ListItemDefaults.border(
        border = border,
        focusedBorder = focusedBorder,
        pressedBorder = pressedBorder,
        selectedBorder = selectedBorder,
        disabledBorder = disabledBorder,
        focusedSelectedBorder = focusedSelectedBorder,
        focusedDisabledBorder = focusedDisabledBorder,
        pressedSelectedBorder = pressedSelectedBorder,
    )

@Composable
private fun NavigationDrawerItemGlow.toToggleableListItemGlow() =
    ListItemDefaults.glow(
        glow = glow,
        focusedGlow = focusedGlow,
        pressedGlow = pressedGlow,
        selectedGlow = selectedGlow,
        focusedSelectedGlow = focusedSelectedGlow,
        pressedSelectedGlow = pressedSelectedGlow,
    )
