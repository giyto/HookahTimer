package ru.hznik.hookahtimer.hall.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.hznik.hookahtimer.hall.model.CanvasPosition
import ru.hznik.hookahtimer.hall.model.TableTimerState

class RoomHallRepositoryTest {
    private lateinit var database: HookahTimerDatabase
    private lateinit var repository: RoomHallRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            HookahTimerDatabase::class.java,
        ).build()
        repository = RoomHallRepository(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun tableNumbersAndFullConfigurationSurviveRepositoryOperations() = runTest {
        repository.addTable("first", listOf("first-1", "first-2"))
        repository.deleteTable("first")
        repository.addTable("second", listOf("second-1", "second-2"))

        val table = repository.tables.first { it.size == 1 }.single()

        assertEquals("Стол 2", table.name)
        assertEquals(listOf("second-1", "second-2"), table.passages.map { it.id })
    }

    @Test
    fun viewportPositionIsPersistedWhenTableIsAdded() = runTest {
        val position = CanvasPosition(840f, 460f)

        repository.addTable("centered", listOf("centered-1", "centered-2"), position)

        assertEquals(position, repository.tables.first { it.isNotEmpty() }.single().position)
    }

    @Test
    fun timerTransitionsAreAtomicAndIndependent() = runTest {
        repository.addTable("first", listOf("first-1", "first-2"))
        repository.addTable("second", listOf("second-1", "second-2"))

        repository.advanceTimer("first", nowEpochMillis = 1_000L)
        repository.advanceTimer("second", nowEpochMillis = 5_000L)
        repository.advanceTimer("first", nowEpochMillis = 10_000L)

        val tables = repository.tables.first { rows ->
            rows.size == 2 && rows.all { it.timerState is TableTimerState.Running }
        }
        assertEquals(
            TableTimerState.Running("first-2", 1_810_000L),
            tables.single { it.id == "first" }.timerState,
        )
        assertEquals(
            TableTimerState.Running("second-1", 1_805_000L),
            tables.single { it.id == "second" }.timerState,
        )
    }

    @Test
    fun invalidPassageReferenceRepairsOnlyAffectedTimer() = runTest {
        repository.addTable("broken", listOf("broken-1", "broken-2"))
        repository.addTable("healthy", listOf("healthy-1", "healthy-2"))
        repository.advanceTimer("healthy", nowEpochMillis = 1_000L)
        database.hallDao().updateTimer(
            tableId = "broken",
            status = TimerStatus.RUNNING.name,
            passageId = "missing",
            endsAtEpochMillis = 10_000L,
        )

        val tables = repository.tables.first { it.size == 2 }

        assertEquals(TableTimerState.Idle, tables.single { it.id == "broken" }.timerState)
        assertTrue(tables.single { it.id == "healthy" }.timerState is TableTimerState.Running)
        assertEquals(
            TimerStatus.IDLE.name,
            database.hallDao().getTable("broken")?.table?.timerStatus,
        )
    }
}
