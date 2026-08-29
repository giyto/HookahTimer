package ru.hznik.hookahtimer.hall.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TableTimerReducerTest {
    @Test
    fun idleStartsFirstPassageFromCurrentTime() {
        val next = advanceTableTimer(table(), nowEpochMillis = 1_000L)

        assertEquals(
            TableTimerState.Running("first", 1_801_000L),
            next,
        )
    }

    @Test
    fun activeOrOverduePassageStartsNextWithFullDuration() {
        val activeTable = table().copy(
            timerState = TableTimerState.Running("first", 100_000L),
        )
        val overdueTable = activeTable.copy(
            timerState = TableTimerState.Running("first", 1_000L),
        )

        assertEquals(
            TableTimerState.Running("second", 2_702_000L),
            advanceTableTimer(activeTable, nowEpochMillis = 2_000L),
        )
        assertEquals(
            TableTimerState.Running("second", 2_705_000L),
            advanceTableTimer(overdueTable, nowEpochMillis = 5_000L),
        )
    }

    @Test
    fun lastPassageCompletesAndCompletedTableResets() {
        val onLastPassage = table().copy(
            timerState = TableTimerState.Running("second", 100_000L),
        )

        assertEquals(
            TableTimerState.Completed,
            advanceTableTimer(onLastPassage, nowEpochMillis = 2_000L),
        )
        assertEquals(
            TableTimerState.Idle,
            advanceTableTimer(
                onLastPassage.copy(timerState = TableTimerState.Completed),
                nowEpochMillis = 3_000L,
            ),
        )
    }

    private fun table(): HallTable = HallTable(
        id = "table",
        name = "Стол",
        passages = listOf(
            TablePassage("first", 30),
            TablePassage("second", 45),
        ),
    )
}
