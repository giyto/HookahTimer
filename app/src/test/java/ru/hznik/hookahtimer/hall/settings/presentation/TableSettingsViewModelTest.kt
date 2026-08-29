package ru.hznik.hookahtimer.hall.settings.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.TablePassage
import ru.hznik.hookahtimer.hall.model.TableShape

class TableSettingsViewModelTest {
    @Test
    fun initialStateCopiesEveryTableSettingIntoDraft() {
        val table = configuredTable()

        val state = TableSettingsViewModel(table).state.value

        assertEquals(table.id, state.tableId)
        assertEquals(table.name, state.tableLabel)
        assertEquals(table.name, state.nameInput)
        assertEquals(table.shape, state.shape)
        assertEquals(table.passages.map { it.id }, state.passages.map { it.id })
        assertEquals(listOf("20", "40"), state.passages.map { it.durationInput })
    }

    @Test
    fun editingDraftDoesNotMutateOriginalTable() {
        val table = configuredTable()
        val viewModel = TableSettingsViewModel(table)

        viewModel.onAction(TableSettingsAction.ChangeName("Новое имя"))
        viewModel.onAction(TableSettingsAction.ChangeShape(TableShape.CIRCLE))
        viewModel.onAction(
            TableSettingsAction.ChangePassageDuration(
                passageId = "passage-1",
                value = "55",
            ),
        )

        assertEquals("VIP", table.name)
        assertEquals(TableShape.PILL, table.shape)
        assertEquals(20, table.passages.first().durationMinutes)
        assertNull(viewModel.state.value.saveResult)
    }

    @Test
    fun passagesCanBeAddedAndRemovedButLastOneRemains() {
        val viewModel = TableSettingsViewModel(
            initialTable = configuredTable(),
            passageIdFactory = { "passage-3" },
        )

        viewModel.onAction(TableSettingsAction.AddPassage)
        assertEquals(
            listOf("passage-1", "passage-2", "passage-3"),
            viewModel.state.value.passages.map { it.id },
        )
        assertEquals("30", viewModel.state.value.passages.last().durationInput)

        viewModel.onAction(TableSettingsAction.RemovePassage("passage-1"))
        viewModel.onAction(TableSettingsAction.RemovePassage("passage-2"))
        viewModel.onAction(TableSettingsAction.RemovePassage("passage-3"))

        assertEquals(listOf("passage-3"), viewModel.state.value.passages.map { it.id })
    }

    @Test
    fun invalidNameAndDurationsProduceErrorsWithoutResult() {
        val viewModel = TableSettingsViewModel(configuredTable())
        viewModel.onAction(TableSettingsAction.ChangeName("   "))
        viewModel.onAction(
            TableSettingsAction.ChangePassageDuration("passage-1", "0"),
        )
        viewModel.onAction(
            TableSettingsAction.ChangePassageDuration("passage-2", "999999999999999999"),
        )

        viewModel.onAction(TableSettingsAction.Save)

        val state = viewModel.state.value
        assertTrue(state.nameHasError)
        assertTrue(state.passages.all { it.durationHasError })
        assertNull(state.saveResult)
    }

    @Test
    fun successfulSaveTrimsNameAndBuildsAtomicResult() {
        val viewModel = TableSettingsViewModel(configuredTable())
        viewModel.onAction(TableSettingsAction.ChangeName("  Терраса  "))
        viewModel.onAction(TableSettingsAction.ChangeShape(TableShape.CIRCLE))
        viewModel.onAction(
            TableSettingsAction.ChangePassageDuration("passage-1", "15"),
        )
        viewModel.onAction(
            TableSettingsAction.ChangePassageDuration("passage-2", "45"),
        )

        viewModel.onAction(TableSettingsAction.Save)

        val state = viewModel.state.value
        val result = requireNotNull(state.saveResult)
        assertEquals("Терраса", result.name)
        assertEquals(TableShape.CIRCLE, result.shape)
        assertEquals(listOf("passage-1", "passage-2"), result.passages.map { it.id })
        assertEquals(listOf(15, 45), result.passages.map { it.durationMinutes })
        assertFalse(state.nameHasError)
        assertTrue(state.passages.none { it.durationHasError })
    }

    @Test
    fun correctingFieldClearsItsValidationError() {
        val viewModel = TableSettingsViewModel(configuredTable())
        viewModel.onAction(TableSettingsAction.ChangeName(""))
        viewModel.onAction(
            TableSettingsAction.ChangePassageDuration("passage-1", ""),
        )
        viewModel.onAction(TableSettingsAction.Save)

        viewModel.onAction(TableSettingsAction.ChangeName("Исправлено"))
        viewModel.onAction(
            TableSettingsAction.ChangePassageDuration("passage-1", "30"),
        )

        assertFalse(viewModel.state.value.nameHasError)
        assertFalse(viewModel.state.value.passages.first().durationHasError)
    }

    private fun configuredTable(): HallTable = HallTable(
        id = "table-1",
        name = "VIP",
        shape = TableShape.PILL,
        passages = listOf(
            TablePassage(id = "passage-1", durationMinutes = 20),
            TablePassage(id = "passage-2", durationMinutes = 40),
        ),
    )
}
