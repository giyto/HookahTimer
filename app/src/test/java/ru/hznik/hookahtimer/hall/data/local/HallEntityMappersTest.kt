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
    fun mixedHookahsRoundTripInNumberOrderAndOnlyCorruptHookahIsRepaired() {
        val source = HallTable(
            id = "table", name = "VIP",
            passages = listOf(TablePassage("p1", 30)),
            hookahs = listOf(
                ru.hznik.hookahtimer.hall.model.TableHookah("h1", 1, TableTimerState.Running("p1", 5_000L)),
                ru.hznik.hookahtimer.hall.model.TableHookah("h2", 2, TableTimerState.Completed),
                ru.hznik.hookahtimer.hall.model.TableHookah("h3", 3, TableTimerState.Running("p1", 9_000L)),
            ),
        )
        val persisted = source.toPersisted(4)
        val row = TableWithPassages(persisted.table, persisted.passages, persisted.hookahs.reversed())
        assertEquals(source, row.toDomain())
        val corrupt = row.copy(hookahs = row.hookahs.map {
            if (it.id == "h1") it.copy(currentPassageId = "missing") else it
        })
        val repaired = corrupt.toDomain()
        assertTrue(corrupt.requiresTimerRepair())
        assertEquals(source.hookahs[0].copy(timerState = TableTimerState.Idle), repaired.hookahs[0])
        assertEquals(source.hookahs.drop(1), repaired.hookahs.drop(1))
    }

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
            val restored = TableWithPassages(
                persisted.table, persisted.passages.reversed(), persisted.hookahs,
            ).toDomain()

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
            ),
            passages = listOf(
                TablePassageEntity("existing", "table", 30, 0),
            ),
            hookahs = listOf(
                TableHookahEntity("h1", "table", 1, TimerStatus.RUNNING.name, "missing", 10_000L),
            ),
        )

        assertEquals(TableTimerState.Idle, row.toDomain().timerState)
        assertTrue(row.requiresTimerRepair())
    }
}
