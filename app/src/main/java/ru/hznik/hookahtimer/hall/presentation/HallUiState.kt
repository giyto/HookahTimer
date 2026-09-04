package ru.hznik.hookahtimer.hall.presentation

import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.CanvasPosition
import ru.hznik.hookahtimer.hall.model.TablePassage
import ru.hznik.hookahtimer.hall.model.TableShape

data class HallUiState(
    val tables: List<HallTable> = emptyList(),
    val isEditMode: Boolean = false,
    val isFullscreenEnabled: Boolean = false,
    val pendingDeleteTableId: String? = null,
    val selectedHookahTableId: String? = null,
    val hasCommandError: Boolean = false,
)

sealed interface HallAction {
    data object ToggleEditMode : HallAction
    data object ToggleFullscreen : HallAction
    data class AddTable(
        val position: CanvasPosition = CanvasPosition.Default,
    ) : HallAction
    data class MoveTable(
        val tableId: String,
        val position: CanvasPosition,
    ) : HallAction
    data class RequestDelete(val tableId: String) : HallAction
    data class UpdateTableSettings(
        val tableId: String,
        val name: String,
        val shape: TableShape,
        val passages: List<TablePassage>,
    ) : HallAction
    data class AdvanceTimer(val tableId: String) : HallAction
    data class AddHookah(val tableId: String) : HallAction
    data class AdvanceHookah(val tableId: String, val hookahId: String) : HallAction
    data object CloseHookahs : HallAction
    data object DismissCommandError : HallAction
    data object ConfirmDelete : HallAction
    data object CancelDelete : HallAction
}
