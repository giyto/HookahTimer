package ru.hznik.hookahtimer.hall.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HallWindowInsetsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val fullscreen = mutableStateOf(true)
    private val bars = mutableStateOf<WindowInsets>(WindowInsets(top = 72))
    private val cutout = mutableStateOf<WindowInsets>(WindowInsets(0))
    private val caption = mutableStateOf<WindowInsets>(WindowInsets(0))
    private val keyboard = mutableStateOf<WindowInsets>(WindowInsets(0))

    @Test
    fun fullscreenIgnoresStaleAndChangingStatusNavigationInsets() {
        showContent()
        assertContentInsets()
        val before = contentBounds()

        // The failing AVD run reported a hidden status bar's old top inset,
        // then a different bottom navigation inset after recreation.
        listOf(WindowInsets(bottom = 64), WindowInsets(top = 72, bottom = 64), WindowInsets(0))
            .forEach { reportedInsets ->
                composeRule.runOnIdle { bars.value = reportedInsets }
                assertEquals(before, contentBounds())
            }
    }

    @Test
    fun leavingFullscreenRestoresNormalSystemBarPadding() {
        bars.value = WindowInsets(top = 72, bottom = 64)
        fullscreen.value = false
        showContent()
        assertContentInsets(top = 72f, bottom = 64f)

        composeRule.runOnIdle { fullscreen.value = true }
        assertContentInsets()

        composeRule.runOnIdle { fullscreen.value = false }
        assertContentInsets(top = 72f, bottom = 64f)
        composeRule.runOnIdle { bars.value = WindowInsets(0) }
        assertContentInsets()
    }

    @Test
    fun fullscreenStillProtectsCutoutsCaptionAndKeyboard() {
        cutout.value = WindowInsets(left = 24, top = 40, right = 12)
        caption.value = WindowInsets(top = 50)
        showContent()
        assertContentInsets(left = 24f, top = 50f, right = 12f)

        composeRule.runOnIdle { keyboard.value = WindowInsets(bottom = 200) }
        assertContentInsets(left = 24f, top = 50f, right = 12f, bottom = 200f)
        composeRule.runOnIdle { keyboard.value = WindowInsets(0) }
        assertContentInsets(left = 24f, top = 50f, right = 12f)
    }

    private fun showContent() {
        composeRule.setContent {
            val insets = hallContentWindowInsets(
                isFullscreen = fullscreen.value,
                systemBars = bars.value,
                displayCutout = cutout.value,
                captionBar = caption.value,
                ime = keyboard.value,
            )
            Box(Modifier.fillMaxSize().testTag("insets_host")) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(insets)) {
                    Box(Modifier.fillMaxSize().testTag("insets_content"))
                }
            }
        }
    }

    private fun contentBounds(): Rect =
        composeRule.onNodeWithTag("insets_content").fetchSemanticsNode().boundsInRoot

    private fun assertContentInsets(
        left: Float = 0f,
        top: Float = 0f,
        right: Float = 0f,
        bottom: Float = 0f,
    ) {
        val host = composeRule.onNodeWithTag("insets_host").fetchSemanticsNode().boundsInRoot
        assertEquals(
            Rect(host.left + left, host.top + top, host.right - right, host.bottom - bottom),
            contentBounds(),
        )
    }
}
