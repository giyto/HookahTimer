package ru.hznik.hookahtimer.hall.model

import org.junit.Assert.*
import org.junit.Test

class MultipleHookahsTest {
    private fun table() = HallTable(
        id = "table",
        name = "VIP",
        shape = TableShape.PILL,
        position = CanvasPosition(320f, 180f),
        passages = listOf(TablePassage("p1", 2), TablePassage("p2", 3)),
    )

    @Test
    fun newTableHasOneIdleHookahAndRejectsEmptyOrDuplicateComposition() {
        val table = table()
        assertTrue(table.isIdle)
        assertFalse(table.isCompleted)
        assertEquals(1, table.hookahs.single().number)
        assertThrows(IllegalArgumentException::class.java) { table.copy(hookahs = emptyList()) }
        val first = table.hookahs.single()
        assertThrows(IllegalArgumentException::class.java) {
            table.copy(hookahs = listOf(first, first.copy(number = 2)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            table.copy(hookahs = listOf(first, first.copy(id = "other")))
        }
    }

    @Test
    fun longPressOnFreeTableStartsOnlyNumberOneAndAdditionIsIdempotent() {
        val started = table().withAddedHookah("order-1", 1_000L)
        assertEquals(1, started.hookahs.size)
        assertEquals(TableTimerState.Running("p1", 121_000L), started.timerState)
        assertEquals(started, started.withAddedHookah("order-1", 10_000L))
        val two = started.withAddedHookah("order-2", 4_000L)
        assertEquals(listOf(1, 2), two.hookahs.map { it.number })
        assertEquals(started.hookahs.single(), two.hookahs.first())
        assertEquals(TableTimerState.Running("p1", 124_000L), two.hookahs.last().timerState)
        assertEquals(two, two.withAddedHookah("order-2", 10_000L))
    }

    @Test
    fun configuredFifteenAndFortyFiveMinutePassagesAreIndependentForEveryOrder() {
        val configured = table().copy(passages = listOf(
            TablePassage("short", 15), TablePassage("long", 45),
        ))
        var current = configured.withAddedHookah("h1", 10_000L).withAddedHookah("h2", 40_000L)
        assertEquals(TableTimerState.Running("short", 910_000L), current.hookahs[0].timerState)
        assertEquals(TableTimerState.Running("short", 940_000L), current.hookahs[1].timerState)
        current = current.withAdvancedHookah("h1", 70_000L)
        assertEquals(TableTimerState.Running("long", 2_770_000L), current.hookahs[0].timerState)
        assertEquals(TableTimerState.Running("short", 940_000L), current.hookahs[1].timerState)
        current = current.withAdvancedHookah("h2", 90_000L)
        assertEquals(TableTimerState.Running("long", 2_770_000L), current.hookahs[0].timerState)
        assertEquals(TableTimerState.Running("long", 2_790_000L), current.hookahs[1].timerState)
        current = current.withAdvancedHookah("h1", 100_000L).withAdvancedHookah("h2", 110_000L)
        val completed = current.hookahs
        current = current.withAddedHookah("h3", 120_000L)
        assertEquals(completed, current.hookahs.take(2))
        assertEquals(3, current.hookahs.last().number)
        assertEquals(TableTimerState.Running("short", 1_020_000L), current.hookahs.last().timerState)
    }

    @Test
    fun independentPassagesCompletionNewOrderAndWholeTableReset() {
        val original = table()
        var current = original.withAddedHookah("h1", 1_000L).withAddedHookah("h2", 2_000L)
        val second = current.hookahs[1]
        current = current.withAdvancedHookah("h1", 10_000L)
        assertEquals(second, current.hookahs[1])
        assertEquals(TableTimerState.Running("p2", 190_000L), current.hookahs[0].timerState)
        current = current.withAdvancedHookah("h1", 20_000L)
        assertFalse(current.isCompleted)
        assertEquals(current, current.withAdvancedHookah("h1", 25_000L))
        assertEquals(current, current.withResetHookahs("reset-too-early"))
        current = current.withAdvancedHookah("h2", 30_000L).withAdvancedHookah("h2", 40_000L)
        assertTrue(current.isCompleted)
        current = current.withAddedHookah("h3", 50_000L)
        assertEquals(listOf(1, 2, 3), current.hookahs.map { it.number })
        assertFalse(current.isCompleted)
        current = current.withAdvancedHookah("h3", 60_000L).withAdvancedHookah("h3", 70_000L)
        val reset = current.withResetHookahs("new-session")
        assertTrue(reset.isIdle)
        assertEquals(listOf(TableHookah("new-session", 1)), reset.hookahs)
        assertEquals(original.name, reset.name)
        assertEquals(original.position, reset.position)
        assertEquals(original.shape, reset.shape)
        assertEquals(original.passages, reset.passages)
        assertEquals(reset, reset.withAdvancedHookah("h1", 80_000L))
    }

    @Test
    fun mostOverdueWinsTiesUseNumberAndCompletedHookahsAreExcluded() {
        val current = table().copy(hookahs = listOf(
            TableHookah("h1", 1, TableTimerState.Completed),
            TableHookah("h2", 2, TableTimerState.Running("p1", 300_000L)),
            TableHookah("h3", 3, TableTimerState.Running("p2", 540_000L)),
            TableHookah("h4", 4, TableTimerState.Running("p1", 300_000L)),
        ))
        assertEquals("h2", current.mostUrgentHookah?.id)
        assertEquals("-05:00", current.timerPresentation(600_000L).timerText)
        val advanced = current.withAdvancedHookah("h2", 600_000L)
        assertEquals("h4", advanced.mostUrgentHookah?.id)
        assertFalse(advanced.isCompleted)
    }

    @Test
    fun minuteBoundariesUseSamePresentationOnTableAndHookahWithoutChangingState() {
        val current = table().withAddedHookah("h1", 0L).withAddedHookah("h2", 10_000L)
        val cases = listOf(
            Triple(59_000L, "01:01", 0),
            Triple(60_000L, "01:00", 1),
            Triple(119_000L, "00:01", 1),
            Triple(120_000L, "00:00", 2),
            Triple(121_000L, "-00:01", 2),
        )
        cases.forEach { (now, text, visual) ->
            val presentation = current.timerPresentation(now)
            assertEquals(text, presentation.timerText)
            assertEquals(visual == 1, presentation.isEndingSoon)
            assertEquals(visual == 2, presentation.isOverdue)
            assertEquals(current.hookahs[0].timerPresentation(current.passages, now), presentation)
            assertFalse(current.isCompleted)
        }
    }

    @Test
    fun recoveredIdleHookahsHaveNeutralSummaryAndCanBeStartedIndividually() {
        val current = table().copy(hookahs = listOf(TableHookah("h1", 1), TableHookah("h2", 2)))
        assertFalse(current.isIdle)
        assertFalse(current.isCompleted)
        assertNull(current.timerPresentation(100L).timerText)
        val started = current.withAdvancedHookah("h2", 100L)
        assertEquals(TableTimerState.Idle, started.hookahs[0].timerState)
        assertEquals("h2", started.mostUrgentHookah?.id)
    }
}
