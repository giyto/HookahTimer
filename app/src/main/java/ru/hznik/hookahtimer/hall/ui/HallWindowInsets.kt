package ru.hznik.hookahtimer.hall.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable

/** Keep immersive canvas geometry independent of status/navigation bar animations. */
@Composable
internal fun hallContentWindowInsets(
    isFullscreen: Boolean,
    systemBars: WindowInsets = WindowInsets.systemBars,
    displayCutout: WindowInsets = WindowInsets.displayCutout,
    captionBar: WindowInsets = WindowInsets.captionBar,
    ime: WindowInsets = WindowInsets.ime,
): WindowInsets {
    // Transient bars overlay immersive content. Their last animated inset must not
    // resize the canvas or clamp its saved viewport after Activity recreation.
    // Cutouts, a desktop window's caption and the keyboard still occupy real space.
    val bars = if (isFullscreen) captionBar else systemBars
    return bars.union(displayCutout).union(ime)
}
