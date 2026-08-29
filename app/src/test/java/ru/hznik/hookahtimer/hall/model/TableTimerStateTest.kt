package ru.hznik.hookahtimer.hall.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TableTimerStateTest {
    @Test
    fun runningStateRequiresPassageIdAndPositiveEndTime() {
        assertThrows(IllegalArgumentException::class.java) {
            TableTimerState.Running(passageId = " ", endsAtEpochMillis = 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TableTimerState.Running(passageId = "passage", endsAtEpochMillis = 0L)
        }

        assertEquals(
            "passage",
            TableTimerState.Running("passage", 1L).passageId,
        )
    }

    @Test
    fun tableRejectsRunningStateForMissingPassage() {
        assertThrows(IllegalArgumentException::class.java) {
            HallTable(
                id = "table",
                name = "Стол",
                passages = listOf(TablePassage("passage", 30)),
                timerState = TableTimerState.Running("missing", 1L),
            )
        }
    }
}
