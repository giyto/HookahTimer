package ru.hznik.hookahtimer.hall.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.hznik.hookahtimer.MainDispatcherRule
import ru.hznik.hookahtimer.hall.data.HallRepository
import ru.hznik.hookahtimer.hall.data.InMemoryHallRepository
import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.CanvasPosition
import ru.hznik.hookahtimer.hall.model.TablePassage
import ru.hznik.hookahtimer.hall.model.TableShape
import ru.hznik.hookahtimer.hall.model.TableTimerState
import ru.hznik.hookahtimer.hall.model.TimeProvider

class HallViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun addIsAllowedOnlyInEditModeAndCreatesUniqueTables() {
        val ids = ArrayDeque(listOf("id-1", "id-2"))
        val viewModel = viewModel(idFactory = { ids.removeFirst() })

        viewModel.onAction(HallAction.AddTable())
        assertTrue(viewModel.state.value.tables.isEmpty())

        viewModel.onAction(HallAction.ToggleEditMode)
        viewModel.onAction(HallAction.AddTable())
        viewModel.onAction(HallAction.AddTable())

        val tables = viewModel.state.value.tables
        assertEquals(listOf("Стол 1", "Стол 2"), tables.map { it.name })
        assertNotEquals(tables[0].id, tables[1].id)
        assertTrue(tables.all { it.shape == TableShape.CIRCLE })
        assertTrue(tables.all { table -> table.passages.map { it.durationMinutes } == listOf(30, 30) })
    }

    @Test
    fun addUsesPositionProvidedByCurrentCanvasViewport() {
        val viewModel = viewModel(idFactory = { "id-1" })
        val viewportPosition = CanvasPosition(840f, 460f)

        viewModel.onAction(HallAction.ToggleEditMode)
        viewModel.onAction(HallAction.AddTable(viewportPosition))

        assertEquals(viewportPosition, viewModel.state.value.tables.single().position)
    }

    @Test
    fun everyNewTableGetsIndependentPassageIds() {
        val tableIds = ArrayDeque(listOf("id-1", "id-2"))
        val passageIds = ArrayDeque(listOf("p-1", "p-2", "p-3", "p-4"))
        val viewModel = viewModel(
            idFactory = { tableIds.removeFirst() },
            passageIdFactory = { passageIds.removeFirst() },
        )
        viewModel.onAction(HallAction.ToggleEditMode)
        viewModel.onAction(HallAction.AddTable())
        viewModel.onAction(HallAction.AddTable())

        val ids = viewModel.state.value.tables.flatMap { table -> table.passages.map { it.id } }
        assertEquals(listOf("p-1", "p-2", "p-3", "p-4"), ids)
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun tableNumbersAreNotReusedAfterDeletion() {
        val ids = ArrayDeque(listOf("id-1", "id-2"))
        val viewModel = viewModel(idFactory = { ids.removeFirst() })
        viewModel.onAction(HallAction.ToggleEditMode)
        viewModel.onAction(HallAction.AddTable())
        viewModel.onAction(HallAction.RequestDelete("id-1"))
        viewModel.onAction(HallAction.ConfirmDelete)
        viewModel.onAction(HallAction.AddTable())

        assertEquals(listOf("Стол 2"), viewModel.state.value.tables.map { it.name })
    }

    @Test
    fun moveWorksInEditModeAndIsIgnoredInWorkingMode() {
        val viewModel = viewModel(idFactory = { "id-1" })
        val editPosition = CanvasPosition(x = 900f, y = 100f)
        val forbiddenPosition = CanvasPosition(x = 200f, y = 800f)
        viewModel.onAction(HallAction.ToggleEditMode)
        viewModel.onAction(HallAction.AddTable())
        viewModel.onAction(HallAction.MoveTable("id-1", editPosition))

        assertEquals(editPosition, viewModel.state.value.tables.single().position)

        viewModel.onAction(HallAction.ToggleEditMode)
        viewModel.onAction(HallAction.MoveTable("id-1", forbiddenPosition))

        assertEquals(editPosition, viewModel.state.value.tables.single().position)
        assertFalse(viewModel.state.value.isEditMode)
    }

    @Test
    fun deletionCanBeCancelledWithoutChangingTables() {
        val viewModel = viewModelWithOneTable()
        viewModel.onAction(HallAction.RequestDelete("id-1"))
        assertEquals("id-1", viewModel.state.value.pendingDeleteTableId)

        viewModel.onAction(HallAction.CancelDelete)

        assertNull(viewModel.state.value.pendingDeleteTableId)
        assertEquals(listOf("id-1"), viewModel.state.value.tables.map { it.id })
    }

    @Test
    fun confirmationDeletesOnlyRequestedTable() {
        val ids = ArrayDeque(listOf("id-1", "id-2"))
        val viewModel = viewModel(idFactory = { ids.removeFirst() })
        viewModel.onAction(HallAction.ToggleEditMode)
        viewModel.onAction(HallAction.AddTable())
        viewModel.onAction(HallAction.AddTable())
        viewModel.onAction(HallAction.RequestDelete("id-1"))
        viewModel.onAction(HallAction.ConfirmDelete)

        assertEquals(listOf("id-2"), viewModel.state.value.tables.map { it.id })
        assertNull(viewModel.state.value.pendingDeleteTableId)
    }

    @Test
    fun deleteRequestIsIgnoredOutsideEditMode() {
        val viewModel = viewModelWithOneTable()
        viewModel.onAction(HallAction.ToggleEditMode)

        viewModel.onAction(HallAction.RequestDelete("id-1"))
        viewModel.onAction(HallAction.ConfirmDelete)

        assertEquals(listOf("id-1"), viewModel.state.value.tables.map { it.id })
        assertNull(viewModel.state.value.pendingDeleteTableId)
    }

    @Test
    fun leavingEditModeClearsPendingDeleteRequest() {
        val viewModel = viewModelWithOneTable()
        viewModel.onAction(HallAction.RequestDelete("id-1"))
        viewModel.onAction(HallAction.ToggleEditMode)

        assertNull(viewModel.state.value.pendingDeleteTableId)
        assertFalse(viewModel.state.value.isEditMode)
    }

    @Test
    fun settingsUpdateOnlySelectedIdleTableAndKeepItsPosition() {
        val tableIds = ArrayDeque(listOf("id-1", "id-2"))
        val viewModel = viewModel(idFactory = { tableIds.removeFirst() })
        viewModel.onAction(HallAction.ToggleEditMode)
        viewModel.onAction(HallAction.AddTable())
        viewModel.onAction(HallAction.AddTable())
        val position = viewModel.state.value.tables.first().position
        val passages = listOf(TablePassage(id = "custom", durationMinutes = 45))

        viewModel.onAction(
            HallAction.UpdateTableSettings(
                tableId = "id-1",
                name = "  VIP  ",
                shape = TableShape.PILL,
                passages = passages,
            ),
        )

        val tables = viewModel.state.value.tables
        assertEquals("VIP", tables[0].name)
        assertEquals(TableShape.PILL, tables[0].shape)
        assertEquals(passages, tables[0].passages)
        assertEquals(position, tables[0].position)
        assertEquals("Стол 2", tables[1].name)
    }

    @Test
    fun timerProgressesIndependentlyAndUsesInjectedClock() {
        var now = 1_000L
        val timeProvider = TimeProvider { now }
        val tableIds = ArrayDeque(listOf("id-1", "id-2"))
        val viewModel = viewModel(
            timeProvider = timeProvider,
            idFactory = { tableIds.removeFirst() },
        )
        viewModel.onAction(HallAction.ToggleEditMode)
        viewModel.onAction(HallAction.AddTable())
        viewModel.onAction(HallAction.AddTable())
        viewModel.onAction(HallAction.ToggleEditMode)

        viewModel.onAction(HallAction.AdvanceTimer("id-1"))
        now = 5_000L
        viewModel.onAction(HallAction.AdvanceTimer("id-2"))
        now = 10_000L
        viewModel.onAction(HallAction.AdvanceTimer("id-1"))

        val tables = viewModel.state.value.tables
        val first = tables.single { it.id == "id-1" }
        val second = tables.single { it.id == "id-2" }
        assertEquals(
            TableTimerState.Running(first.passages[1].id, 1_810_000L),
            first.timerState,
        )
        assertEquals(
            TableTimerState.Running(second.passages[0].id, 1_805_000L),
            second.timerState,
        )
    }

    @Test
    fun activeTableCannotBeReconfiguredButCanBeDeletedInEditMode() {
        val viewModel = viewModelWithOneTable(timeProvider = TimeProvider { 1_000L })
        viewModel.onAction(HallAction.ToggleEditMode)
        viewModel.onAction(HallAction.AdvanceTimer("id-1"))
        viewModel.onAction(HallAction.ToggleEditMode)
        val running = viewModel.state.value.tables.single()

        viewModel.onAction(
            HallAction.UpdateTableSettings(
                tableId = "id-1",
                name = "Нельзя",
                shape = TableShape.PILL,
                passages = listOf(TablePassage("replacement", 10)),
            ),
        )

        assertEquals(running, viewModel.state.value.tables.single())
        viewModel.onAction(HallAction.RequestDelete("id-1"))
        viewModel.onAction(HallAction.ConfirmDelete)
        assertTrue(viewModel.state.value.tables.isEmpty())
    }

    @Test
    fun activeTableCanMoveWithoutChangingTimer() {
        val viewModel = viewModelWithOneTable(timeProvider = TimeProvider { 1_000L })
        viewModel.onAction(HallAction.ToggleEditMode)
        viewModel.onAction(HallAction.AdvanceTimer("id-1"))
        viewModel.onAction(HallAction.ToggleEditMode)
        val timerBeforeMove = viewModel.state.value.tables.single().timerState
        val newPosition = CanvasPosition(800f, 200f)

        viewModel.onAction(HallAction.MoveTable("id-1", newPosition))

        assertEquals(newPosition, viewModel.state.value.tables.single().position)
        assertEquals(timerBeforeMove, viewModel.state.value.tables.single().timerState)
    }

    @Test
    fun repeatedTapWhileTimerCommandIsRunningIsDropped() {
        val repository = BlockingTimerRepository()
        val viewModel = HallViewModel(
            repository = repository,
            timeProvider = TimeProvider { 1_000L },
        )

        viewModel.onAction(HallAction.AdvanceTimer("table"))
        viewModel.onAction(HallAction.AdvanceTimer("table"))

        assertEquals(1, repository.advanceCalls)
        repository.release.complete(Unit)
    }

    @Test
    fun fullscreenChoiceIsSessionStateAndDoesNotChangeTables() {
        val viewModel = viewModelWithOneTable()
        val tablesBefore = viewModel.state.value.tables

        viewModel.onAction(HallAction.ToggleFullscreen)

        assertTrue(viewModel.state.value.isFullscreenEnabled)
        assertEquals(tablesBefore, viewModel.state.value.tables)
        viewModel.onAction(HallAction.ToggleFullscreen)
        assertFalse(viewModel.state.value.isFullscreenEnabled)
    }

    @Test
    fun idleRunningAndOverdueTimersAdvanceImmediately() {
        val now = 1_000L
        val passages = listOf(TablePassage("p1", 1), TablePassage("p2", 1))
        val repository = InMemoryHallRepository(
            initialTables = listOf(
                HallTable(id = "idle", name = "Свободен", passages = passages),
                HallTable(
                    id = "running",
                    name = "Работает",
                    passages = passages,
                    timerState = TableTimerState.Running("p1", 2_000L),
                ),
                HallTable(
                    id = "overdue",
                    name = "Просрочен",
                    passages = passages,
                    timerState = TableTimerState.Running("p1", 1_000L),
                ),
            ),
        )
        val viewModel = HallViewModel(repository, TimeProvider { now })

        viewModel.onAction(HallAction.AdvanceTimer("idle"))
        assertTrue(table(viewModel, "idle").timerState is TableTimerState.Running)

        viewModel.onAction(HallAction.AdvanceTimer("running"))
        assertEquals(
            TableTimerState.Running("p2", 61_000L),
            table(viewModel, "running").timerState,
        )

        viewModel.onAction(HallAction.AdvanceTimer("overdue"))
        assertEquals(
            TableTimerState.Running("p2", 61_000L),
            table(viewModel, "overdue").timerState,
        )
    }

    @Test
    fun runningTransitionUsesTimeOfAcceptedTap() {
        var now = 1_000L
        val passages = listOf(TablePassage("p1", 1), TablePassage("p2", 30))
        val initialTimer = TableTimerState.Running("p1", 60_000L)
        val repository = InMemoryHallRepository(
            initialTables = listOf(
                HallTable(
                    id = "table",
                    name = "Стол",
                    passages = passages,
                    timerState = initialTimer,
                ),
            ),
        )
        val viewModel = HallViewModel(repository, TimeProvider { now })

        now = 5_000L
        viewModel.onAction(HallAction.AdvanceTimer("table"))

        assertEquals(
            TableTimerState.Running("p2", 1_805_000L),
            table(viewModel, "table").timerState,
        )
    }

    @Test
    fun completedTableResetsImmediatelyAndLeavesOthersUntouched() {
        val other = HallTable(id = "other", name = "Другой")
        val repository = InMemoryHallRepository(
            initialTables = listOf(
                HallTable(
                    id = "completed",
                    name = "Готов",
                    timerState = TableTimerState.Completed,
                ),
                other,
            ),
        )
        val viewModel = HallViewModel(repository, TimeProvider { 1_000L })

        viewModel.onAction(HallAction.AdvanceTimer("completed"))

        assertEquals(TableTimerState.Idle, table(viewModel, "completed").timerState)
        assertEquals(other, table(viewModel, "other"))
    }

    @Test
    fun tappingRunningLastPassageCompletesTable() {
        val passages = listOf(TablePassage("p1", 1), TablePassage("p2", 1))
        val viewModel = HallViewModel(
            repository = InMemoryHallRepository(
                initialTables = listOf(
                    HallTable(
                        id = "table",
                        name = "Стол",
                        passages = passages,
                        timerState = TableTimerState.Running("p2", 10_000L),
                    ),
                ),
            ),
            timeProvider = TimeProvider { 1_000L },
        )

        viewModel.onAction(HallAction.AdvanceTimer("table"))

        assertEquals(TableTimerState.Completed, table(viewModel, "table").timerState)
    }

    @Test
    fun directTransitionsRemainIndependentAcrossTables() {
        val passages = listOf(TablePassage("p1", 1), TablePassage("p2", 1))
        val repository = InMemoryHallRepository(
            initialTables = listOf(
                HallTable(
                    id = "first",
                    name = "Первый",
                    passages = passages,
                    timerState = TableTimerState.Running("p1", 10_000L),
                ),
                HallTable(id = "second", name = "Второй", passages = passages),
            ),
        )
        val viewModel = HallViewModel(repository, TimeProvider { 1_000L })

        viewModel.onAction(HallAction.AdvanceTimer("first"))
        viewModel.onAction(HallAction.AdvanceTimer("second"))

        assertEquals(
            TableTimerState.Running("p2", 61_000L),
            table(viewModel, "first").timerState,
        )
        assertEquals(
            TableTimerState.Running("p1", 61_000L),
            table(viewModel, "second").timerState,
        )
    }

    @Test
    fun tappingMissingTableIsSafe() {
        val repository = InMemoryHallRepository()
        val viewModel = HallViewModel(repository, TimeProvider { 1_000L })

        viewModel.onAction(HallAction.AdvanceTimer("missing"))

        assertTrue(viewModel.state.value.tables.isEmpty())
    }

    private fun table(viewModel: HallViewModel, id: String): HallTable =
        viewModel.state.value.tables.single { it.id == id }

    private fun viewModelWithOneTable(
        timeProvider: TimeProvider = TimeProvider { 1_000L },
    ): HallViewModel = viewModel(
        timeProvider = timeProvider,
        idFactory = { "id-1" },
    ).also {
        it.onAction(HallAction.ToggleEditMode)
        it.onAction(HallAction.AddTable())
    }

    private fun viewModel(
        timeProvider: TimeProvider = TimeProvider { 1_000L },
        idFactory: () -> String = { "id-1" },
        passageIdFactory: () -> String = { java.util.UUID.randomUUID().toString() },
    ): HallViewModel = HallViewModel(
        repository = InMemoryHallRepository(),
        timeProvider = timeProvider,
        idFactory = idFactory,
        passageIdFactory = passageIdFactory,
    )

    private class BlockingTimerRepository : HallRepository {
        override val tables: Flow<List<HallTable>> = MutableStateFlow(
            listOf(HallTable(id = "table", name = "Стол")),
        )
        val release = CompletableDeferred<Unit>()
        var advanceCalls = 0

        override suspend fun addTable(
            tableId: String,
            passageIds: List<String>,
            position: CanvasPosition?,
        ) = Unit
        override suspend fun moveTable(tableId: String, position: CanvasPosition) = Unit
        override suspend fun updateTableSettings(
            tableId: String,
            name: String,
            shape: TableShape,
            passages: List<TablePassage>,
        ) = Unit
        override suspend fun deleteTable(tableId: String) = Unit
        override suspend fun addHookah(tableId: String, hookahId: String, nowEpochMillis: Long) = Unit
        override suspend fun resetTable(tableId: String, initialHookahId: String) = Unit
        override suspend fun advanceHookah(
            tableId: String,
            hookahId: String,
            nowEpochMillis: Long,
            expectedState: TableTimerState?,
        ) = advanceTimer(tableId, nowEpochMillis)

        override suspend fun advanceTimer(tableId: String, nowEpochMillis: Long) {
            advanceCalls += 1
            release.await()
        }
    }
}
