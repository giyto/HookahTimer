package ru.hznik.hookahtimer.hall.presentation

import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.NormalizedPosition
import ru.hznik.hookahtimer.hall.model.TablePassage
import ru.hznik.hookahtimer.hall.model.TableShape

data class HallUiState(
    val tables: List<HallTable> = emptyList(),
    val isEditMode: Boolean = false,
    val isFullscreenEnabled: Boolean = false,
    val pendingDeleteTableId: String? = null,
    val pendingTimerConfirmation: PendingTimerConfirmation? = null,
)

data class PendingTimerConfirmation(
    val tableId: String,
    val type: TimerConfirmationType,
) {
    init {
        require(tableId.isNotBlank())
    }
}

enum class TimerConfirmationType {
    ADVANCE_EARLY,
    RESET_COMPLETED,
}

sealed interface HallAction {
    data object ToggleEditMode : HallAction
    data object ToggleFullscreen : HallAction
    data object AddTable : HallAction
    data class MoveTable(
        val tableId: String,
        val position: NormalizedPosition,
    ) : HallAction
    data class RequestDelete(val tableId: String) : HallAction
    data class UpdateTableSettings(
        val tableId: String,
        val name: String,
        val shape: TableShape,
        val passages: List<TablePassage>,
    ) : HallAction
    data class AdvanceTimer(val tableId: String) : HallAction
    data object ConfirmTimerTransition : HallAction
    data object CancelTimerTransition : HallAction
    data object ConfirmDelete : HallAction
    data object CancelDelete : HallAction
}
