package ru.hznik.hookahtimer.hall.presentation

import androidx.lifecycle.ViewModel
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.NormalizedPosition

class HallViewModel(
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(HallUiState())
    val state: StateFlow<HallUiState> = mutableState.asStateFlow()

    fun onAction(action: HallAction) {
        when (action) {
            HallAction.ToggleEditMode -> toggleEditMode()
            HallAction.AddTable -> addTable()
            is HallAction.MoveTable -> moveTable(action)
            is HallAction.RequestDelete -> requestDelete(action.tableId)
            HallAction.ConfirmDelete -> confirmDelete()
            HallAction.CancelDelete -> cancelDelete()
        }
    }

    private fun toggleEditMode() {
        mutableState.update { current ->
            current.copy(
                isEditMode = !current.isEditMode,
                pendingDeleteTableId = null,
            )
        }
    }

    private fun addTable() {
        mutableState.update { current ->
            if (!current.isEditMode) return@update current

            val number = current.nextTableNumber
            current.copy(
                tables = current.tables + HallTable(
                    id = idFactory(),
                    name = "Стол $number",
                    position = initialPosition(number),
                ),
                nextTableNumber = number + 1,
            )
        }
    }

    private fun moveTable(action: HallAction.MoveTable) {
        mutableState.update { current ->
            if (!current.isEditMode) return@update current

            current.copy(
                tables = current.tables.map { table ->
                    if (table.id == action.tableId) {
                        table.copy(position = action.position)
                    } else {
                        table
                    }
                },
            )
        }
    }

    private fun requestDelete(tableId: String) {
        mutableState.update { current ->
            if (!current.isEditMode || current.tables.none { it.id == tableId }) {
                current
            } else {
                current.copy(pendingDeleteTableId = tableId)
            }
        }
    }

    private fun confirmDelete() {
        mutableState.update { current ->
            val tableId = current.pendingDeleteTableId ?: return@update current
            current.copy(
                tables = current.tables.filterNot { it.id == tableId },
                pendingDeleteTableId = null,
            )
        }
    }

    private fun cancelDelete() {
        mutableState.update { current ->
            if (current.pendingDeleteTableId == null) current else current.copy(pendingDeleteTableId = null)
        }
    }

    private fun initialPosition(number: Int): NormalizedPosition {
        val index = number - 1
        val column = index % INITIAL_COLUMNS
        val row = (index / INITIAL_COLUMNS) % INITIAL_ROWS
        return NormalizedPosition.of(
            x = (column + 1f) / (INITIAL_COLUMNS + 1f),
            y = (row + 1f) / (INITIAL_ROWS + 1f),
        )
    }

    private companion object {
        const val INITIAL_COLUMNS = 4
        const val INITIAL_ROWS = 3
    }
}
