package ru.hznik.hookahtimer.hall.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TableTimerPresentationTest {
    @Test
    fun remainingTimeRoundsUpAndSupportsMoreThanHour() {
        val table = runningTable(endsAt = 5_400_000L)

        assertEquals("90:00", table.timerPresentation(nowEpochMillis = 0L).timerText)
        assertEquals("89:59", table.timerPresentation(nowEpochMillis = 1_001L).timerText)
        assertFalse(table.timerPresentation(nowEpochMillis = 1_001L).isOverdue)
        assertEquals("30:00", formatTimerSeconds(1_800L))
    }

    @Test
    fun staleUiClockDoesNotShowMoreThanConfiguredDuration() {
        val table = HallTable(
            id = "table",
            name = "Стол",
            passages = listOf(TablePassage("passage", 30)),
            timerState = TableTimerState.Running("passage", 1_801_000L),
        )

        assertEquals("30:00", table.timerPresentation(nowEpochMillis = 0L).timerText)
    }

    @Test
    fun boundaryIsRedZeroAndThenNegative() {
        val table = runningTable(endsAt = 1_800_000L)

        val atEnd = table.timerPresentation(nowEpochMillis = 1_800_000L)
        assertEquals("00:00", atEnd.timerText)
        assertTrue(atEnd.isOverdue)

        val oneSecondLate = table.timerPresentation(nowEpochMillis = 1_801_000L)
        assertEquals("-00:01", oneSecondLate.timerText)
        assertTrue(oneSecondLate.isOverdue)
        assertEquals(1, oneSecondLate.passageNumber)
    }

    @Test
    fun idleAndCompletedDoNotExposeTimer() {
        val idle = HallTable(id = "idle", name = "Idle")
        val completed = idle.copy(timerState = TableTimerState.Completed)

        assertEquals(null, idle.timerPresentation(0L).timerText)
        assertTrue(completed.timerPresentation(0L).isCompleted)
        assertEquals(null, completed.timerPresentation(0L).timerText)
    }

    private fun runningTable(endsAt: Long): HallTable = HallTable(
        id = "table",
        name = "Стол",
        passages = listOf(TablePassage("passage", 90)),
        timerState = TableTimerState.Running("passage", endsAt),
    )
}
