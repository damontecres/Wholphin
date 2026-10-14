package com.github.damontecres.wholphin.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.ui.nav.CollapsedDrawerItemWidth

/**
 * Side margins that adapt to the shape of the screen.
 *
 * They are taken from the width a screen has beyond a 16:9 shape. A 16:9 screen has none, so it
 * gets no margins and looks as it always did; a wider screen shares its extra width out between
 * the left margin, the right margin and the content in between.
 *
 * Headlines (page titles and descriptions, and the titles of card rows) start at the left margin.
 * The backdrop image ends at the right margin. Card rows ignore both and scroll across the full
 * width of the screen.
 */
object FluidLayout {
    // Width beyond a 16:9 shape; vw is the screen width and vh the screen height
    @Composable
    private fun extraWidth(): Dp {
        val configuration = LocalConfiguration.current
        val vw = configuration.screenWidthDp.dp
        val vh = configuration.screenHeightDp.dp
        return (vw - vh * (16f / 9f)).coerceAtLeast(0.dp)
    }

    /**
     * Gap between the left edge of the screen and the headlines: clamp(min, target, max), where
     * the target is a share of the extra width and min/max are shares of the screen width
     */
    @Composable
    fun leftMargin(): Dp {
        val vw = LocalConfiguration.current.screenWidthDp.dp
        val leftShare = 0.5f
        val leftMin = vw * 0f
        val leftMax = vw * 0.2f
        return (extraWidth() * leftShare).coerceIn(leftMin, leftMax)
    }

    /**
     * Gap between the backdrop image and the right edge of the screen, worked out the same way
     */
    @Composable
    fun rightMargin(): Dp {
        val vw = LocalConfiguration.current.screenWidthDp.dp
        val rightShare = 0.1f
        val rightMin = vw * -0.3f
        val rightMax = vw * 0.23f
        return (extraWidth() * rightShare).coerceIn(rightMin, rightMax)
    }

    /**
     * How far headlines are indented within a page. Pages already start just clear of the nav
     * drawer, so this is the left margin less that, and never less than the app's usual small
     * indent
     */
    @Composable
    fun headlineInset(): Dp = (leftMargin() - (CollapsedDrawerItemWidth + 8.dp)).coerceAtLeast(8.dp)

    /**
     * Where the first card of a row starts: level with the headlines, or at the row's own usual
     * indent if that is further in. Only the starting point moves; rows still scroll across the
     * full width
     */
    @Composable
    fun rowStart(usual: Dp): Dp = maxOf(usual, headlineInset())

    /**
     * Width of the sharp backdrop image: clamp(min, target, max), with the target tied to the
     * screen's height and min/max shares of the screen width
     */
    @Composable
    fun backdropImageWidth(): Dp {
        val configuration = LocalConfiguration.current
        val vw = configuration.screenWidthDp.dp
        val vh = configuration.screenHeightDp.dp
        val widthMin = vw * 0.3f
        val widthTarget = vh * 1.5f
        val widthMax = vw * 0.63f
        return widthTarget.coerceIn(widthMin, widthMax)
    }

    /**
     * Width of the headline block (title, details and description on the home screen). It runs
     * from the left margin to where the backdrop image starts, reaching a little way into the
     * image because the image's left side is faded out. Kept within a min and max so the text
     * never gets too narrow to read or too long per line
     */
    @Composable
    fun headlineWidth(): Dp {
        val vw = LocalConfiguration.current.screenWidthDp.dp
        // How far into the image the text may run, as a share of the image's width
        val reachIntoImage = 0.15f
        val widthMin = vw * 0.3f
        val widthMax = vw * 0.45f
        val imageWidth = backdropImageWidth()
        val imageStart = vw - rightMargin() - imageWidth
        val target = imageStart + imageWidth * reachIntoImage - leftMargin()
        return target.coerceIn(widthMin, widthMax)
    }
}
