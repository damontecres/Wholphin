package com.github.damontecres.wholphin.ui.playback

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/**
 * Returns the badge background color based on the official rating.
 * Muted/pastel tones — readable but not harsh, still clearly
 * color-coded to warn the user about content intensity.
 *
 *  Red   → TV-MA, R          (most intense)
 *  Amber → TV-14, PG-13      (moderate)
 *  Green → TV-PG, PG         (mild)
 *  Blue  → TV-G, G, TV-Y     (family/kids)
 */
private fun ratingColor(rating: String): Color =
    when (rating.uppercase().trim()) {
        "TV-MA", "R", "NC-17"         -> Color(0xFFC0392B)
        "TV-14", "PG-13"              -> Color(0xFFD4690A)
        "TV-PG", "PG"                 -> Color(0xFF27AE60)
        "TV-G", "G", "TV-Y", "TV-Y7" -> Color(0xFF2980B9)
        else                          -> Color(0xFF555555)
    }

/**
 * Netflix-inspired content warning pill shown top-left during playback.
 *
 * Animation sequence:
 *  1. Tiny pill fades in
 *  2. Badge expands to show rating (spring bounce)
 *  3. Descriptor box unrolls to the right (spring)
 *  4. Holds for [displayDurationMs]
 *  5. Descriptor rolls back
 *  6. Badge shrinks back
 *  7. Entire pill fades out smoothly
 */
@Composable
fun ContentWarningPill(
    rating: String,
    warnings: List<String>,
    displayDurationMs: Long = 7_000L,
    modifier: Modifier = Modifier,
) {
    if (warnings.isEmpty() && rating.isBlank()) return

    var wrapAlpha     by remember { mutableStateOf(0f) }
    var badgeExpanded by remember { mutableStateOf(false) }
    var descExpanded  by remember { mutableStateOf(false) }
    var badgeVisible  by remember { mutableStateOf(false) }

    val pillAlpha by animateFloatAsState(
        targetValue   = wrapAlpha,
        animationSpec = tween(durationMillis = if (wrapAlpha > 0f) 300 else 600),
        label         = "pillAlpha",
    )
    val badgeWidth: Dp by animateDpAsState(
        targetValue   = if (badgeExpanded) 58.dp else 10.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium,
        ),
        label = "badgeWidth",
    )
    val descWidth: Dp by animateDpAsState(
        targetValue   = if (descExpanded) 220.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness    = Spring.StiffnessMediumLow,
        ),
        label = "descWidth",
    )
    val badgeTextAlpha by animateFloatAsState(
        targetValue   = if (badgeExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 180, delayMillis = if (badgeExpanded) 200 else 0),
        label         = "badgeTextAlpha",
    )
    val descTextAlpha by animateFloatAsState(
        targetValue   = if (descExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 180, delayMillis = if (descExpanded) 260 else 0),
        label         = "descTextAlpha",
    )

    LaunchedEffect(Unit) {
        delay(400L)
        badgeVisible = true
        wrapAlpha    = 1f
        delay(320L)
        badgeExpanded = true
        delay(380L)
        descExpanded  = true
        delay(displayDurationMs)
        descExpanded  = false
        delay(500L)
        badgeExpanded = false
        delay(450L)
        wrapAlpha = 0f
    }

    val badgeColor = if (badgeVisible) ratingColor(rating) else Color.Transparent
    val shape      = RoundedCornerShape(5.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = modifier.alpha(pillAlpha),
    ) {
        // ── Badge ─────────────────────────────────────────────────────────────
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(badgeWidth)
                .height(34.dp)
                .clip(shape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            badgeColor.copy(alpha = 0.95f),
                            badgeColor.copy(alpha = 0.75f),
                        ),
                    ),
                ),
        ) {
            // Glossy sheen
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.25f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Text(
                text          = rating,
                fontSize      = 10.sp,
                fontWeight    = FontWeight.ExtraBold,
                color         = Color.White,
                letterSpacing = 0.04.sp,
                maxLines      = 1,
                modifier      = Modifier.alpha(badgeTextAlpha),
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // ── Descriptor ────────────────────────────────────────────────────────
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .width(descWidth)
                .height(34.dp)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.08f))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.06f),
                            Color.Transparent,
                        ),
                    ),
                ),
        ) {
            Text(
                text          = warnings.joinToString(" · "),
                fontSize      = 11.sp,
                fontWeight    = FontWeight.Medium,
                color         = Color.White.copy(alpha = 0.88f),
                letterSpacing = 0.02.sp,
                maxLines      = 1,
                modifier      = Modifier
                    .wrapContentWidth()
                    .padding(horizontal = 13.dp)
                    .alpha(descTextAlpha),
            )
        }
    }
}