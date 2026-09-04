package ru.hznik.hookahtimer.hall.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * One recognizer owns tap and hold. Movement, consumption by the canvas or a
 * second pointer cancels both. A completed hold consumes release, never taps.
 */
@Composable
internal fun Modifier.hookahTableGestures(
    tableId: String,
    tapLabel: String,
    addLabel: String,
    onTap: () -> Unit,
    onHold: () -> Unit,
    onPressed: (Boolean) -> Unit,
): Modifier {
    val tap = rememberUpdatedState(onTap)
    val hold = rememberUpdatedState(onHold)
    val pressed = rememberUpdatedState(onPressed)
    return this
        .semantics(mergeDescendants = true) {
            role = Role.Button
            onClick(label = tapLabel) { tap.value(); true }
            onLongClick(label = addLabel) { hold.value(); true }
        }
        .onKeyEvent {
            if (it.key == Key.Enter || it.key == Key.NumPadEnter ||
                it.key == Key.DirectionCenter || it.key == Key.Spacebar
            ) {
                if (it.type == KeyEventType.KeyUp) tap.value()
                true
            } else {
                false
            }
        }
        .focusable()
        .pointerInput(tableId) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.isConsumed) return@awaitEachGesture
                var cancelled = false
                var released = false
                try {
                    pressed.value(true)
                    val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.find { it.id == down.id }
                            if (event.changes.size > 1 || pointer == null ||
                                event.changes.any { it.isConsumed } ||
                                (pointer.position - down.position).getDistance() > viewConfiguration.touchSlop
                            ) {
                                cancelled = true
                                break
                            }
                            if (!pointer.pressed) {
                                pointer.consume()
                                released = true
                                break
                            }
                            if (awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed }) {
                                cancelled = true
                                break
                            }
                        }
                        true
                    }
                    if (finished == null && !cancelled) {
                        hold.value()
                        // Keep the gesture until every finger is up; no release click.
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    } else if (released && !cancelled) {
                        tap.value()
                    }
                } finally {
                    pressed.value(false)
                }
            }
        }
}
