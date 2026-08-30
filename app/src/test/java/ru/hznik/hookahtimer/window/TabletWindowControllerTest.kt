package ru.hznik.hookahtimer.window

import org.junit.Assert.assertEquals
import org.junit.Test

class TabletWindowControllerTest {
    @Test
    fun hallFullscreenAndExitApplyOnlyChangedWindowOperations() {
        val operations = RecordingOperations()
        val controller = StateAwareTabletWindowController(operations)

        controller.showHall(isFullscreen = false)
        controller.showHall(isFullscreen = false)
        controller.showHall(isFullscreen = true)
        controller.showHall(isFullscreen = true)
        controller.leaveHall()
        controller.leaveHall()

        assertEquals(
            listOf(
                "keep:true",
                "fullscreen:false",
                "fullscreen:true",
                "keep:false",
                "fullscreen:false",
            ),
            operations.calls,
        )
    }

    private class RecordingOperations : TabletWindowOperations {
        val calls = mutableListOf<String>()

        override fun setKeepScreenOn(enabled: Boolean) {
            calls += "keep:$enabled"
        }

        override fun setFullscreen(enabled: Boolean) {
            calls += "fullscreen:$enabled"
        }
    }
}
