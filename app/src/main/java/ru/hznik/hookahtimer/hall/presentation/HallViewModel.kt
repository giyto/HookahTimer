package ru.hznik.hookahtimer.hall.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.hznik.hookahtimer.hall.data.HallRepository
import ru.hznik.hookahtimer.hall.model.CanvasPosition
import ru.hznik.hookahtimer.hall.model.SystemTimeProvider
import ru.hznik.hookahtimer.hall.model.TablePassage
import ru.hznik.hookahtimer.hall.model.TableTimerState
import ru.hznik.hookahtimer.hall.model.TimeProvider

class HallViewModel(
    private val repository: HallRepository,
    val timeProvider: TimeProvider = SystemTimeProvider,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val passageIdFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val editorState = MutableStateFlow(EditorState())
    private val commandMutex = Mutex()
    private val processingTableIds = mutableSetOf<String>()

    val state: StateFlow<HallUiState> = combine(
        repository.tables,
        editorState,
    ) { tables, editor ->
        HallUiState(
            tables = tables,
            isEditMode = editor.isEditMode,
            isFullscreenEnabled = editor.isFullscreenEnabled,
            pendingDeleteTableId = editor.pendingDeleteTableId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HallUiState(),
    )

    fun onAction(action: HallAction) {
        when (action) {
            HallAction.ToggleEditMode -> toggleEditMode()
            HallAction.ToggleFullscreen -> toggleFullscreen()
            is HallAction.AddTable -> addTable(action.position)
            is HallAction.MoveTable -> moveTable(action)
            is HallAction.RequestDelete -> requestDelete(action.tableId)
            is HallAction.UpdateTableSettings -> updateTableSettings(action)
            is HallAction.AdvanceTimer -> advanceTimer(action.tableId)
            HallAction.ConfirmDelete -> confirmDelete()
            HallAction.CancelDelete -> cancelDelete()
        }
    }

    private fun toggleEditMode() {
        editorState.update { current ->
            current.copy(
                isEditMode = !current.isEditMode,
                pendingDeleteTableId = null,
            )
        }
    }

    private fun toggleFullscreen() {
        editorState.update { current ->
            current.copy(isFullscreenEnabled = !current.isFullscreenEnabled)
        }
    }

    private fun addTable(position: CanvasPosition) {
        if (!editorState.value.isEditMode) return
        val tableId = idFactory()
        val passageIds = List(TablePassage.DEFAULT_PASSAGE_COUNT) { passageIdFactory() }
        launchCommand {
            repository.addTable(tableId, passageIds, position)
        }
    }

    private fun moveTable(action: HallAction.MoveTable) {
        if (!editorState.value.isEditMode || state.value.tables.none { it.id == action.tableId }) return
        launchTableCommand(action.tableId) {
            repository.moveTable(action.tableId, action.position)
        }
    }

    private fun requestDelete(tableId: String) {
        editorState.update { current ->
            if (!current.isEditMode || state.value.tables.none { it.id == tableId }) {
                current
            } else {
                current.copy(pendingDeleteTableId = tableId)
            }
        }
    }

    private fun updateTableSettings(action: HallAction.UpdateTableSettings) {
        val trimmedName = action.name.trim()
        val selectedTable = state.value.tables.firstOrNull { it.id == action.tableId }
        val hasUniquePassageIds = action.passages.distinctBy { it.id }.size == action.passages.size
        if (
            !editorState.value.isEditMode ||
            selectedTable?.timerState != TableTimerState.Idle ||
            trimmedName.isEmpty() ||
            action.passages.isEmpty() ||
            !hasUniquePassageIds
        ) {
            return
        }
        launchTableCommand(action.tableId) {
            repository.updateTableSettings(
                tableId = action.tableId,
                name = trimmedName,
                shape = action.shape,
                passages = action.passages,
            )
        }
    }

    private fun advanceTimer(tableId: String) {
        val editor = editorState.value
        if (editor.isEditMode || state.value.tables.none { it.id == tableId }) return

        val nowEpochMillis = timeProvider.nowEpochMillis()
        advanceTimerImmediately(tableId, nowEpochMillis)
    }

    private fun advanceTimerImmediately(tableId: String, nowEpochMillis: Long) {
        launchTableCommand(tableId) {
            repository.advanceTimer(tableId, nowEpochMillis)
        }
    }

    private fun confirmDelete() {
        val tableId = editorState.value.pendingDeleteTableId ?: return
        if (!editorState.value.isEditMode) return
        launchTableCommand(tableId) {
            repository.deleteTable(tableId)
            editorState.update { it.copy(pendingDeleteTableId = null) }
        }
    }

    private fun cancelDelete() {
        editorState.update { current ->
            if (current.pendingDeleteTableId == null) current else current.copy(pendingDeleteTableId = null)
        }
    }

    private fun launchCommand(block: suspend () -> Unit) {
        viewModelScope.launch {
            commandMutex.withLock { block() }
        }
    }

    private fun launchTableCommand(tableId: String, block: suspend () -> Unit) {
        synchronized(processingTableIds) {
            if (!processingTableIds.add(tableId)) return
        }
        viewModelScope.launch {
            try {
                commandMutex.withLock { block() }
            } finally {
                synchronized(processingTableIds) {
                    processingTableIds.remove(tableId)
                }
            }
        }
    }

    private data class EditorState(
        val isEditMode: Boolean = false,
        val isFullscreenEnabled: Boolean = false,
        val pendingDeleteTableId: String? = null,
    )

    companion object {
        fun factory(
            repository: HallRepository,
            timeProvider: TimeProvider = SystemTimeProvider,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(HallViewModel::class.java))
                return HallViewModel(
                    repository = repository,
                    timeProvider = timeProvider,
                ) as T
            }
        }
    }
}
