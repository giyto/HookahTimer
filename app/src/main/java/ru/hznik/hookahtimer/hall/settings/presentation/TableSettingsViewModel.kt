package ru.hznik.hookahtimer.hall.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.TablePassage

class TableSettingsViewModel(
    initialTable: HallTable,
    private val passageIdFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(initialTable.toSettingsState())
    val state: StateFlow<TableSettingsUiState> = mutableState.asStateFlow()

    fun onAction(action: TableSettingsAction) {
        when (action) {
            is TableSettingsAction.ChangeName -> changeName(action.value)
            is TableSettingsAction.ChangeShape -> mutableState.update {
                it.copy(shape = action.shape, saveResult = null)
            }
            is TableSettingsAction.ChangePassageDuration -> changePassageDuration(action)
            TableSettingsAction.AddPassage -> addPassage()
            is TableSettingsAction.RemovePassage -> removePassage(action.passageId)
            TableSettingsAction.Save -> save()
        }
    }

    private fun changeName(value: String) {
        mutableState.update {
            it.copy(
                nameInput = value,
                nameHasError = false,
                saveResult = null,
            )
        }
    }

    private fun changePassageDuration(action: TableSettingsAction.ChangePassageDuration) {
        mutableState.update { current ->
            current.copy(
                passages = current.passages.map { passage ->
                    if (passage.id == action.passageId) {
                        passage.copy(
                            durationInput = action.value,
                            durationHasError = false,
                        )
                    } else {
                        passage
                    }
                },
                saveResult = null,
            )
        }
    }

    private fun addPassage() {
        mutableState.update { current ->
            current.copy(
                passages = current.passages + PassageDraft(
                    id = passageIdFactory(),
                    durationInput = TablePassage.DEFAULT_DURATION_MINUTES.toString(),
                ),
                saveResult = null,
            )
        }
    }

    private fun removePassage(passageId: String) {
        mutableState.update { current ->
            if (current.passages.size <= 1) {
                current
            } else {
                current.copy(
                    passages = current.passages.filterNot { it.id == passageId },
                    saveResult = null,
                )
            }
        }
    }

    private fun save() {
        mutableState.update { current ->
            val trimmedName = current.nameInput.trim()
            val parsedDurations = current.passages.map { passage ->
                passage.durationInput.toIntOrNull()?.takeIf { it > 0 }
            }
            val validatedPassages = current.passages.mapIndexed { index, passage ->
                passage.copy(durationHasError = parsedDurations[index] == null)
            }
            val nameHasError = trimmedName.isEmpty()

            if (nameHasError || parsedDurations.any { it == null }) {
                current.copy(
                    nameHasError = nameHasError,
                    passages = validatedPassages,
                    saveResult = null,
                )
            } else {
                current.copy(
                    nameInput = trimmedName,
                    nameHasError = false,
                    passages = validatedPassages,
                    saveResult = TableSettingsResult(
                        tableId = current.tableId,
                        name = trimmedName,
                        shape = current.shape,
                        passages = current.passages.mapIndexed { index, passage ->
                            TablePassage(
                                id = passage.id,
                                durationMinutes = checkNotNull(parsedDurations[index]),
                            )
                        },
                    ),
                )
            }
        }
    }

    companion object {
        fun factory(initialTable: HallTable): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(TableSettingsViewModel::class.java))
                    return TableSettingsViewModel(initialTable) as T
                }
            }
    }
}

private fun HallTable.toSettingsState(): TableSettingsUiState = TableSettingsUiState(
    tableId = id,
    tableLabel = name,
    nameInput = name,
    shape = shape,
    passages = passages.map { passage ->
        PassageDraft(
            id = passage.id,
            durationInput = passage.durationMinutes.toString(),
        )
    },
)
