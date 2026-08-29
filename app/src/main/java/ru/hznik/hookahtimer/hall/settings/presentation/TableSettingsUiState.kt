package ru.hznik.hookahtimer.hall.settings.presentation

import ru.hznik.hookahtimer.hall.model.TablePassage
import ru.hznik.hookahtimer.hall.model.TableShape

data class TableSettingsUiState(
    val tableId: String,
    val tableLabel: String,
    val nameInput: String,
    val shape: TableShape,
    val passages: List<PassageDraft>,
    val nameHasError: Boolean = false,
    val saveResult: TableSettingsResult? = null,
)

data class PassageDraft(
    val id: String,
    val durationInput: String,
    val durationHasError: Boolean = false,
)

data class TableSettingsResult(
    val tableId: String,
    val name: String,
    val shape: TableShape,
    val passages: List<TablePassage>,
)

sealed interface TableSettingsAction {
    data class ChangeName(val value: String) : TableSettingsAction
    data class ChangeShape(val shape: TableShape) : TableSettingsAction
    data class ChangePassageDuration(
        val passageId: String,
        val value: String,
    ) : TableSettingsAction
    data object AddPassage : TableSettingsAction
    data class RemovePassage(val passageId: String) : TableSettingsAction
    data object Save : TableSettingsAction
}
