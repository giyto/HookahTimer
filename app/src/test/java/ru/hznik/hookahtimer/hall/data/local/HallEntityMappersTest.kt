package ru.hznik.hookahtimer.hall.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.CanvasPosition
import ru.hznik.hookahtimer.hall.model.TablePassage
import ru.hznik.hookahtimer.hall.model.TableShape
import ru.hznik.hookahtimer.hall.model.TableTimerState

class HallEntityMappersTest {
    @Test
    fun everyTimerStateAndTableSettingRoundTrips() {
        val base = HallTable(
            id = "table",
            name = "VIP",
            shape = TableShape.PILL,
            position = CanvasPosition(280f, 520f),
            passages = listOf(
                TablePassage("first", 15),
                TablePassage("second", 45),
            ),
        )
        val states = listOf(
            TableTimerState.Idle,
            TableTimerState.Running("second", 123_456L),
            TableTimerState.Completed,
        )

        states.forEach { timerState ->
            val table = base.copy(timerState = timerState)
            val persisted = table.toPersisted(sortOrder = 7)
            val restored = TableWithPassages(persisted.table, persisted.passages.reversed()).toDomain()

            assertEquals(table, restored)
            assertEquals(7, persisted.table.sortOrder)
        }
    }

    @Test
    fun missingRunningPassageIsMappedToIdleAndMarkedForRepair() {
        val row = TableWithPassages(
            table = HallTableEntity(
                id = "table",
                name = "Стол",
                shape = TableShape.CIRCLE.name,
                positionX = 0.5f,
                positionY = 0.5f,
                canvasX = 544f,
                canvasY = 344f,
                sortOrder = 0,
                timerStatus = TimerStatus.RUNNING.name,
                currentPassageId = "missing",
                endsAtEpochMillis = 10_000L,
            ),
            passages = listOf(
                TablePassageEntity("existing", "table", 30, 0),
            ),
        )

        assertEquals(TableTimerState.Idle, row.toDomain().timerState)
        assertTrue(row.requiresTimerRepair())
    }
}
