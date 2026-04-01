package com.github.damontecres.wholphin.ui.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

// ── Spring specs — top-level so they are never re-allocated on recomposition ──

/** Snappy entrance — approximates cubic-bezier(0.22, 1, 0.36, 1) from the prototype. */
private val popSpec = spring<Float>(
    dampingRatio = 0.72f,
    stiffness    = Spring.StiffnessMedium,
)

/** Critically-damped exit — clean, no bounce. */
private val collapseSpec = spring<Float>(
    dampingRatio = 1f,
    stiffness    = Spring.StiffnessMediumLow,
)

// ── Rating helpers ─────────────────────────────────────────────────────────────

fun normalizeToAgeRating(raw: String): String =
    when (raw.uppercase().trim()) {
        "TV-Y", "G", "U", "0+", "ALL"                              -> "All"
        "TV-Y7", "TV-Y7-FV", "PG", "GB-PG"                        -> "7+"
        "TV-PG", "TV-14", "PG-13", "12", "12A", "GB-12", "GB-12A" -> "13+"
        "15", "GB-15"                                               -> "16+"
        "TV-MA", "NC-17", "R", "18", "GB-18"                      -> "18+"
        else                                                        -> raw
    }

/** Thin left-border accent color, keyed to the normalized rating. */
private fun ratingAccentColor(normalized: String): Color =
    when (normalized) {
        "All" -> Color(0xFF3D6B7A)
        "7+"  -> Color(0xFF4A6B55)
        "13+" -> Color(0xFF8A6B35)
        "16+" -> Color(0xFF8A5238)
        "18+" -> Color(0xFF7A2020) // matches prototype exactly
        else  -> Color(0xFF555760)
    }

/** Badge fill color. */
private fun ratingBadgeColor(normalized: String): Color =
    when (normalized) {
        "All" -> Color(0xFF5A8B9E)
        "7+"  -> Color(0xFF6B9477)
        "13+" -> Color(0xFFC29B62)
        "16+" -> Color(0xFFC27A59)
        "18+" -> Color(0xFF923030) // matches prototype exactly
        else  -> Color(0xFF7A7D84)
    }

// ── Composable ─────────────────────────────────────────────────────────────────

@Composable
fun ContentWarningPill(
    rating: String,
    warnings: List<String>,
    isPlaying: Boolean,
    displayDurationMs: Long = 7_000L,
    modifier: Modifier = Modifier,
) {
    if (warnings.isEmpty() && rating.isBlank()) return

    val displayRating = remember(rating)   { normalizeToAgeRating(rating) }
    val warningText   = remember(warnings) { warnings.joinToString(" · ") }

    // Three independent states — one per animation beat,
    // matching the HTML prototype's JS sequence exactly.
    var wrapVisible  by remember { mutableStateOf(false) }
    var badgeVisible by remember { mutableStateOf(false) }
    var descVisible  by remember { mutableStateOf(false) }
    var hasShown     by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying || hasShown) return@LaunchedEffect
        hasShown = true

        delay(3_000L)

        // Entrance — mirrors the JS in the prototype: wrap → badge → desc
        wrapVisible  = true
        delay(150L)
        badgeVisible = true
        delay(350L)
        descVisible  = true

        delay(displayDurationMs)

        // Exit — reverse order, same as prototype
        descVisible  = false
        delay(300L)
        badgeVisible = false
        delay(400L)
        wrapVisible  = false
    }

    // ── Beat 1: the left-border line + container ──────────────────────────────
    // Mirrors .blend-wrap:
    //   opacity:0; transform:translateX(-10px) → .show → opacity:1; translateX(0)
    AnimatedVisibility(
        visible  = wrapVisible,
        enter    = fadeIn(tween(500)) + slideInHorizontally(tween(500)) { -10 },
        exit     = fadeOut(tween(500)) + slideOutHorizontally(tween(500)) { -10 },
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {

            // The left-border accent — identical to `border-left: 2.5px solid #7A2020`
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(34.dp)
                    .background(ratingAccentColor(displayRating)),
            )

            Spacer(modifier = Modifier.width(10.dp))

            // ── Beat 2: badge pops ── mirrors .blend-badge ────────────────────
            // opacity:0; transform:scale(0.75) → opacity:1; scale(1)
            AnimatedVisibility(
                visible = badgeVisible,
                enter   = scaleIn(popSpec, initialScale = 0.75f) + fadeIn(tween(600)),
                exit    = scaleOut(collapseSpec, targetScale = 0.75f) + fadeOut(tween(300)),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(26.dp)
                        .defaultMinSize(minWidth = 44.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(ratingBadgeColor(displayRating))
                        .padding(horizontal = 11.dp),
                ) {
                    Text(
                        text          = displayRating,
                        fontSize      = 11.sp,
                        fontWeight    = FontWeight.Bold,
                        color         = Color.White,
                        letterSpacing = 0.5.sp,
                        maxLines      = 1,
                        softWrap      = false, // prevents reflow → no layout pass during scale
                    )
                }
            }

            // ── Beat 3: descriptor pops ── mirrors .blend-desc ────────────────
            // opacity:0; scale(0.82) translateX(-6px); transform-origin:left center
            //          → opacity:1; scale(1) translateX(0)
            AnimatedVisibility(
                visible = descVisible,
                enter   = scaleIn(
                    animationSpec   = popSpec,
                    initialScale    = 0.82f,
                    transformOrigin = TransformOrigin(0f, 0.5f), // pivot: left-center
                ) + slideInHorizontally(tween(350)) { -6 } + fadeIn(tween(350)),
                exit    = scaleOut(
                    animationSpec   = collapseSpec,
                    targetScale     = 0.82f,
                    transformOrigin = TransformOrigin(0f, 0.5f),
                ) + slideOutHorizontally(tween(250)) { -6 } + fadeOut(tween(250)),
            ) {
                Text(
                    text          = warningText,
                    fontSize      = 12.sp,
                    fontWeight    = FontWeight.Normal,
                    color         = Color.White.copy(alpha = 0.6f),
                    letterSpacing = 0.2.sp,
                    maxLines      = 1,
                    softWrap      = false,
                    modifier      = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}