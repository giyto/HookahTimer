package ru.hznik.hookahtimer.window

import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class AndroidTabletWindowController(
    window: Window,
) : TabletWindowController by StateAwareTabletWindowController(
    operations = AndroidTabletWindowOperations(window),
)

private class AndroidTabletWindowOperations(
    private val window: Window,
) : TabletWindowOperations {
    private val insetsController: WindowInsetsControllerCompat
        get() = WindowCompat.getInsetsController(window, window.decorView)

    override fun setKeepScreenOn(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun setFullscreen(enabled: Boolean) {
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (enabled) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
