package ru.hznik.hookahtimer.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun TabletWindowEffect(
    controller: TabletWindowController,
    isHallVisible: Boolean,
    isFullscreen: Boolean,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentHallVisible by rememberUpdatedState(isHallVisible)
    val currentFullscreen by rememberUpdatedState(isFullscreen)

    fun applyCurrentState() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
            currentHallVisible
        ) {
            controller.showHall(currentFullscreen)
        } else {
            controller.leaveHall()
        }
    }

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME,
                -> applyCurrentState()

                Lifecycle.Event.ON_STOP -> controller.leaveHall()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        applyCurrentState()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.leaveHall()
        }
    }

    LaunchedEffect(isHallVisible, isFullscreen) {
        applyCurrentState()
    }
}
