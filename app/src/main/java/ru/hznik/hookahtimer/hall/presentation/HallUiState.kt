package ru.hznik.hookahtimer.hall.presentation

import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.NormalizedPosition

data class HallUiState(
    val tables: List<HallTable> = emptyList(),
    val isEditMode: Boolean = false,
    val pendingDeleteTableId: String? = null,
    val nextTableNumber: Int = 1,
)

sealed interface HallAction {
    data object ToggleEditMode : HallAction
    data object AddTable : HallAction
    data class MoveTable(
        val tableId: String,
        val position: NormalizedPosition,
    ) : HallAction
    data class RequestDelete(val tableId: String) : HallAction
    data object ConfirmDelete : HallAction
    data object CancelDelete : HallAction
}
