package ru.hznik.hookahtimer.hall.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hznik.hookahtimer.hall.model.NormalizedPosition
import ru.hznik.hookahtimer.hall.model.TableShape

class HallViewModelTest {
    @Test
    fun addIsAllowedOnlyInEditModeAndCreatesUniqueTables() {
        val ids = ArrayDeque(listOf("id-1", "id-2"))
        val viewModel = HallViewModel(idFactory = { ids.removeFirst() })

        viewModel.onAction(HallAction.AddTable)
        assertTrue(viewModel.state.value.tables.isEmpty())

        viewModel.onAction(HallAction.ToggleEditMode)
        viewModel.onAction(HallAction.AddTable)
        viewModel.onAction(HallAction.AddTable)

        val tables = viewModel.state.value.tables
        assertEquals(listOf("Стол 1", "Стол 2"), tables.map { it.name })
        assertNotEquals(tables[0].id, tables[1].id)
        assertTrue(tables.all { it.shape == TableShape.CIRCLE })
    }

    @Test
    fun tableNumbersAreNotReusedAfterDeletion() {
        val ids = ArrayDeque(listOf("id-1", "id-2"))
        val viewModel = HallViewModel(idFactory = { ids.removeFirst() })
        viewModel.onAction(HallAction.ToggleEditMode)
        viewModel.onAction(HallAction.AddTable)
        viewModel.onAction(HallAction.RequestDelete("id-1"))
        viewModel.onAction(HallAction.ConfirmDelete)
        viewModel.onAction(HallAction.AddTable)

        assertEquals(listOf("Стол 2"), viewModel.state.value.tables.map { it.name })
    }

    @Test
    fun moveWorksInEditModeAndIsIgnoredInWorkingMode() {
        val viewModel = HallViewModel(idFactory = { "id-1" })
        val editPosition = NormalizedPosition.of(x = 0.9f, y = 0.1f)
        val forbiddenPosition = NormalizedPosition.of(x = 0.2f, y = 0.8f)
        viewModel.onAction(HallAction.ToggleEditMode)
        viewModel.onAction(HallAction.AddTable)
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
        val viewModel = HallViewModel(idFactory = { ids.removeFirst() })
        viewModel.onAction(HallAction.ToggleEditMode)
        viewModel.onAction(HallAction.AddTable)
        viewModel.onAction(HallAction.AddTable)
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

    private fun viewModelWithOneTable(): HallViewModel = HallViewModel(idFactory = { "id-1" }).also {
        it.onAction(HallAction.ToggleEditMode)
        it.onAction(HallAction.AddTable)
    }
}
