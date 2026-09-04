package ru.hznik.hookahtimer.hall.presentation

import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.hznik.hookahtimer.MainDispatcherRule
import ru.hznik.hookahtimer.hall.data.HallRepository
import ru.hznik.hookahtimer.hall.data.InMemoryHallRepository
import ru.hznik.hookahtimer.hall.model.*

class MultipleHookahsViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private fun table() = HallTable("table", "VIP", passages = listOf(TablePassage("p1", 2), TablePassage("p2", 3)))
    private fun active() = table().withAddedHookah("h1", 100L).withAddedHookah("h2", 200L)

    @Test
    fun singleTapAdvancesMultiTapOnlyOpensAndHoldStartsWithoutOpening() {
        val vm = HallViewModel(InMemoryHallRepository(listOf(table())), TimeProvider { 1_000L },
            hookahIdFactory = { "h2" })
        vm.onAction(HallAction.AddHookah("table"))
        assertEquals(1, vm.state.value.tables.single().hookahs.size)
        assertTrue(vm.state.value.tables.single().timerState is TableTimerState.Running)
        assertNull(vm.state.value.selectedHookahTableId)
        vm.onAction(HallAction.AddHookah("table"))
        val before = vm.state.value.tables.single()
        vm.onAction(HallAction.AdvanceTimer("table"))
        assertEquals("table", vm.state.value.selectedHookahTableId)
        assertEquals(before, vm.state.value.tables.single())
        vm.onAction(HallAction.CloseHookahs)
        assertNull(vm.state.value.selectedHookahTableId)
        assertEquals(before, vm.state.value.tables.single())
    }

    @Test
    fun completingCardsKeepsWindowAndOnlyTableTapResets() {
        val completed = active().copy(hookahs = active().hookahs.map {
            it.copy(timerState = TableTimerState.Running("p2", 100_000L))
        })
        val vm = HallViewModel(InMemoryHallRepository(listOf(completed)), TimeProvider { 1_000L })
        vm.onAction(HallAction.AdvanceTimer("table"))
        vm.onAction(HallAction.AdvanceHookah("table", "h1"))
        assertFalse(vm.state.value.tables.single().isCompleted)
        vm.onAction(HallAction.AdvanceHookah("table", "h2"))
        assertTrue(vm.state.value.tables.single().isCompleted)
        assertEquals("table", vm.state.value.selectedHookahTableId)
        vm.onAction(HallAction.AdvanceHookah("table", "h2"))
        assertTrue(vm.state.value.tables.single().isCompleted)
        vm.onAction(HallAction.CloseHookahs)
        vm.onAction(HallAction.AdvanceTimer("table"))
        assertTrue(vm.state.value.tables.single().isIdle)
        assertNull(vm.state.value.selectedHookahTableId)
    }

    @Test
    fun differentCardsQueueWhileDuplicatePendingCardIsIgnoredAndTimeIsCapturedOnce() {
        val memory = InMemoryHallRepository(listOf(active()))
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val repository = object : HallRepository by memory {
            override suspend fun advanceHookah(
                tableId: String, hookahId: String, nowEpochMillis: Long, expectedState: TableTimerState?,
            ) {
                calls++
                release.await()
                memory.advanceHookah(tableId, hookahId, nowEpochMillis, expectedState)
            }
        }
        var now = 1_000L
        val vm = HallViewModel(repository, TimeProvider { now })
        vm.onAction(HallAction.AdvanceHookah("table", "h1"))
        vm.onAction(HallAction.AdvanceHookah("table", "h1"))
        now = 2_000L
        vm.onAction(HallAction.AdvanceHookah("table", "h2"))
        now = 90_000L
        release.complete(Unit)
        assertEquals(2, calls)
        val result = vm.state.value.tables.single()
        assertEquals(TableTimerState.Running("p2", 181_000L), result.hookahs[0].timerState)
        assertEquals(TableTimerState.Running("p2", 182_000L), result.hookahs[1].timerState)
    }

    @Test
    fun failedCommandShowsErrorAndReleasesGuardForRetry() {
        val memory = InMemoryHallRepository(listOf(active()))
        var fail = true
        val repository = object : HallRepository by memory {
            override suspend fun advanceHookah(
                tableId: String, hookahId: String, nowEpochMillis: Long, expectedState: TableTimerState?,
            ) {
                if (fail) error("Simulated write failure")
                memory.advanceHookah(tableId, hookahId, nowEpochMillis, expectedState)
            }
        }
        val vm = HallViewModel(repository, TimeProvider { 1_000L })
        vm.onAction(HallAction.AdvanceHookah("table", "h1"))
        assertTrue(vm.state.value.hasCommandError)
        assertEquals(active(), vm.state.value.tables.single())
        fail = false
        vm.onAction(HallAction.DismissCommandError)
        vm.onAction(HallAction.AdvanceHookah("table", "h1"))
        assertFalse(vm.state.value.hasCommandError)
        assertEquals(TableTimerState.Running("p2", 181_000L), vm.state.value.tables.single().hookahs[0].timerState)
    }

    @Test
    fun editModeClosesWindowAndRejectsWorkingActionsAndActiveSettings() {
        val vm = HallViewModel(InMemoryHallRepository(listOf(active())), TimeProvider { 1_000L })
        vm.onAction(HallAction.AdvanceTimer("table"))
        vm.onAction(HallAction.ToggleEditMode)
        vm.onAction(HallAction.AddHookah("table"))
        vm.onAction(HallAction.AdvanceHookah("table", "h1"))
        vm.onAction(HallAction.UpdateTableSettings("table", "Changed", TableShape.CIRCLE, listOf(TablePassage("new", 5))))
        assertNull(vm.state.value.selectedHookahTableId)
        assertEquals(active(), vm.state.value.tables.single())
    }
}
