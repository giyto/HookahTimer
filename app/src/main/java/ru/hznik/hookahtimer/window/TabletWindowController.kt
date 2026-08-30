package ru.hznik.hookahtimer.window

interface TabletWindowController {
    fun showHall(isFullscreen: Boolean)

    fun leaveHall()
}

internal interface TabletWindowOperations {
    fun setKeepScreenOn(enabled: Boolean)

    fun setFullscreen(enabled: Boolean)
}

internal class StateAwareTabletWindowController(
    private val operations: TabletWindowOperations,
) : TabletWindowController {
    private var keepScreenOn: Boolean? = null
    private var fullscreen: Boolean? = null

    override fun showHall(isFullscreen: Boolean) {
        applyKeepScreenOn(true)
        applyFullscreen(isFullscreen)
    }

    override fun leaveHall() {
        applyKeepScreenOn(false)
        applyFullscreen(false)
    }

    private fun applyKeepScreenOn(enabled: Boolean) {
        if (keepScreenOn == enabled) return
        keepScreenOn = enabled
        operations.setKeepScreenOn(enabled)
    }

    private fun applyFullscreen(enabled: Boolean) {
        if (fullscreen == enabled) return
        fullscreen = enabled
        operations.setFullscreen(enabled)
    }
}

object NoOpTabletWindowController : TabletWindowController {
    override fun showHall(isFullscreen: Boolean) = Unit

    override fun leaveHall() = Unit
}
