package ru.hznik.hookahtimer.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorTest {
    @Test
    fun primaryActionColorsHaveReadableContrast() {
        assertReadable(PastelGreen, PastelGreenOnPrimary)
        assertReadable(PastelGreenContainer, PastelGreenOnContainer)
    }

    @Test
    fun graphiteSurfaceColorsHaveReadableContrast() {
        assertReadable(GraphiteBackground, GraphiteOnSurface)
        assertReadable(GraphiteSurface, GraphiteOnSurface)
        assertReadable(GraphiteSurfaceVariant, GraphiteOnSurfaceVariant)
    }

    private fun assertReadable(background: Color, foreground: Color) {
        assertTrue(
            "Expected WCAG AA contrast, actual=${contrastRatio(background, foreground)}",
            contrastRatio(background, foreground) >= MINIMUM_TEXT_CONTRAST,
        )
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = first.relativeLuminance()
        val secondLuminance = second.relativeLuminance()
        return (max(firstLuminance, secondLuminance) + 0.05) /
            (min(firstLuminance, secondLuminance) + 0.05)
    }

    private fun Color.relativeLuminance(): Double =
        0.2126 * red.linearized() +
            0.7152 * green.linearized() +
            0.0722 * blue.linearized()

    private fun Float.linearized(): Double {
        val component = toDouble()
        return if (component <= 0.04045) {
            component / 12.92
        } else {
            ((component + 0.055) / 1.055).pow(2.4)
        }
    }

    private companion object {
        const val MINIMUM_TEXT_CONTRAST = 4.5
    }
}
