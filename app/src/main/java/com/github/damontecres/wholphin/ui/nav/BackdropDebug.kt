package com.github.damontecres.wholphin.ui.nav

/**
 * Switches for working on the backdrop. They hide what normally sits on top of the background so
 * it can be seen on its own. Hidden things are only invisible: focus still moves through the card
 * rows, so the backdrop keeps changing as usual.
 *
 * All of them must be false in anything that is not a development build.
 */
object BackdropDebug {
    // Hides the card rows
    val hideRows = false

    // Hides the sharp backdrop image, leaving only the baked background
    val hideImage = false

    // Draws a coloured frame around each blurred copy in the baked background, to check where
    // they sit and how big they are without changing any of their settings.
    // The main blurred copy magenta, tiles in order: magenta, cyan, yellow, green, then repeating
    val outlineCopies = false
}
