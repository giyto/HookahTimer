package ru.hznik.hookahtimer.hall.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.hznik.hookahtimer.hall.data.HallRepository
import ru.hznik.hookahtimer.hall.data.InMemoryHallRepository
import ru.hznik.hookahtimer.hall.model.*

class MultipleHookahsRepositoryTest {
    @Test fun memorySatisfiesContract() = runTest { contract(InMemoryHallRepository()) }

    @Test fun roomSatisfiesSameContract() = runTest {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), HookahTimerDatabase::class.java,
        ).build()
        try { contract(RoomHallRepository(db)) } finally { db.close() }
    }

    private suspend fun contract(repository: HallRepository) {
        repository.addTable("t", listOf("p1", "p2"), CanvasPosition(450f, 250f))
        repository.updateTableSettings("t", "VIP", TableShape.PILL, listOf(TablePassage("p1", 2), TablePassage("p2", 3)))
        repository.advanceTimer("t", 1_000L)
        repository.addHookah("t", "h2", 2_000L)
        repository.addHookah("t", "h2", 3_000L)
        var table = repository.tables.first().single()
        assertEquals(listOf(1, 2), table.hookahs.map { it.number })
        val firstId = table.hookahs[0].id
        val firstState = table.hookahs[0].timerState
        val secondState = table.hookahs[1].timerState
        coroutineScope {
            launch { repository.advanceHookah("t", firstId, 4_000L, firstState) }
            launch { repository.advanceHookah("t", "h2", 5_000L, secondState) }
            launch { repository.addHookah("t", "h3", 6_000L) }
        }
        table = repository.tables.first().single()
        assertEquals(TableTimerState.Running("p2", 184_000L), table.hookahs[0].timerState)
        assertEquals(TableTimerState.Running("p2", 185_000L), table.hookahs[1].timerState)
        assertEquals(TableTimerState.Running("p1", 126_000L), table.hookahs[2].timerState)
        repository.advanceHookah("t", firstId, 7_000L, firstState) // stale event
        repository.resetTable("t", "too-early")
        repository.updateTableSettings("t", "Wrong", TableShape.CIRCLE, listOf(TablePassage("new", 1)))
        assertEquals(table, repository.tables.first().single())
        repository.advanceHookah("t", firstId, 8_000L)
        repository.advanceHookah("t", "h2", 8_000L)
        repository.advanceHookah("t", "h3", 8_000L)
        repository.advanceHookah("t", "h3", 9_000L)
        table = repository.tables.first().single()
        assertTrue(table.isCompleted)
        repository.advanceHookah("t", "h3", 10_000L)
        assertEquals(table, repository.tables.first().single())
        repository.addHookah("t", "h4", 11_000L)
        repository.resetTable("t", "must-not-remove-new-order")
        table = repository.tables.first().single()
        assertEquals(4, table.hookahs.size)
        repository.advanceHookah("t", "h4", 12_000L)
        repository.advanceHookah("t", "h4", 13_000L)
        repository.resetTable("t", "next-session")
        table = repository.tables.first().single()
        assertTrue(table.isIdle)
        assertEquals(listOf(TableHookah("next-session", 1)), table.hookahs)
        assertEquals("VIP", table.name)
        assertEquals(TableShape.PILL, table.shape)
        assertEquals(CanvasPosition(450f, 250f), table.position)
        repository.advanceHookah("t", firstId, 14_000L)
        assertEquals(table, repository.tables.first().single())
        repository.updateTableSettings("t", "New", TableShape.CIRCLE, listOf(TablePassage("new", 1)))
        assertEquals("New", repository.tables.first().single().name)
        repository.deleteTable("t")
        repository.addHookah("t", "missing", 15_000L)
        assertTrue(repository.tables.first().isEmpty())
        repository.addTable("race", listOf("race-p1", "race-p2"))
        repeat(3) { repository.advanceTimer("race", 1_000L) }
        coroutineScope {
            launch { repository.resetTable("race", "race-reset") }
            launch { repository.addHookah("race", "race-new-order", 20_000L) }
        }
        val raced = repository.tables.first().single()
        assertEquals(
            TableTimerState.Running("race-p1", 1_820_000L),
            raced.hookahs.single { it.id == "race-new-order" }.timerState,
        )
        assertFalse(raced.isCompleted)
        repository.deleteTable("race")
    }

    @Test fun corruptHookahIsRepairedWithoutTouchingHealthyNeighbors() = runTest {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), HookahTimerDatabase::class.java,
        ).build()
        try {
            val repository = RoomHallRepository(db)
            repository.addTable("t", listOf("p1", "p2"))
            repository.advanceTimer("t", 1_000L)
            repository.addHookah("t", "h2", 2_000L)
            val healthy = repository.tables.first().single().hookahs[1]
            db.hallDao().updateTimer("t", "initial:t", "RUNNING", "missing", 10_000L)
            val table = repository.tables.first().single()
            assertEquals(TableTimerState.Idle, table.hookahs[0].timerState)
            assertEquals(healthy, table.hookahs[1])
            assertFalse(table.isIdle)
            repository.advanceHookah("t", "initial:t", 3_000L)
            assertEquals("p1", (repository.tables.first().single().hookahs[0].timerState as TableTimerState.Running).passageId)
        } finally { db.close() }
    }

    @Test fun failedInsertRollsBackWithoutPartialComposition() = runTest {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), HookahTimerDatabase::class.java,
        ).build()
        try {
            val repository = RoomHallRepository(db)
            repository.addTable("t", listOf("p1", "p2"))
            repository.advanceTimer("t", 1_000L)
            val before = repository.tables.first()
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_hookah BEFORE INSERT ON table_hookahs " +
                    "BEGIN SELECT RAISE(ABORT, 'test failure'); END",
            )
            var failed = false
            try { repository.addHookah("t", "h2", 2_000L) } catch (_: Exception) { failed = true }
            assertTrue(failed)
            assertEquals(before, repository.tables.first())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_hookah")
            repository.addHookah("t", "h2", 2_000L)
            assertEquals(2, repository.tables.first().single().hookahs.size)
        } finally { db.close() }
    }
}
