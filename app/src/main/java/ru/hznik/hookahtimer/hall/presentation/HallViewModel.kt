package ru.hznik.hookahtimer.hall.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
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
    private val hookahIdFactory: () -> String = { UUID.randomUUID().toString() },
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
            selectedHookahTableId = editor.selectedHookahTableId?.takeIf { id ->
                !editor.isEditMode && tables.any { it.id == id && it.hasMultipleHookahs }
            },
            hasCommandError = editor.hasCommandError,
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
            is HallAction.AddHookah -> addHookah(action.tableId)
            is HallAction.AdvanceHookah -> advanceHookah(action.tableId, action.hookahId)
            HallAction.CloseHookahs -> editorState.update { it.copy(selectedHookahTableId = null) }
            HallAction.DismissCommandError -> editorState.update { it.copy(hasCommandError = false) }
            HallAction.ConfirmDelete -> confirmDelete()
            HallAction.CancelDelete -> cancelDelete()
        }
    }

    private fun toggleEditMode() {
        editorState.update { current ->
            current.copy(
                isEditMode = !current.isEditMode,
                pendingDeleteTableId = null,
                selectedHookahTableId = null,
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
            selectedTable?.isIdle != true ||
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
        if (editorState.value.isEditMode) return
        val table = state.value.tables.find { it.id == tableId } ?: return
        when {
            table.isCompleted -> launchTableCommand("reset:$tableId") {
                repository.resetTable(tableId, hookahIdFactory())
                editorState.update { it.copy(selectedHookahTableId = null) }
            }
            table.hasMultipleHookahs ->
                editorState.update { it.copy(selectedHookahTableId = tableId) }
            else -> advanceHookah(tableId, table.hookahs.single().id)
        }
    }

    private fun addHookah(tableId: String) {
        if (editorState.value.isEditMode) return
        val table = state.value.tables.find { it.id == tableId } ?: return
        if (table.isIdle) {
            advanceHookah(tableId, table.hookahs.single().id)
            return
        }
        val now = timeProvider.nowEpochMillis()
        val hookahId = hookahIdFactory()
        launchTableCommand("add:$tableId") {
            repository.addHookah(tableId, hookahId, now)
        }
    }

    private fun advanceHookah(tableId: String, hookahId: String) {
        if (editorState.value.isEditMode) return
        val table = state.value.tables.find { it.id == tableId } ?: return
        val hookah = table.hookahs.find { it.id == hookahId } ?: return
        if (hookah.timerState == TableTimerState.Completed) return
        val now = timeProvider.nowEpochMillis()
        // Separate keys keep concurrent taps on different cards; the repository
        // re-reads under its transaction and rejects stale taps on the same timer.
        launchTableCommand("hookah:$tableId:$hookahId") {
            repository.advanceHookah(tableId, hookahId, now, hookah.timerState)
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
            runCommand(block)
        }
    }

    private fun launchTableCommand(tableId: String, block: suspend () -> Unit) {
        synchronized(processingTableIds) {
            if (!processingTableIds.add(tableId)) return
        }
        viewModelScope.launch {
            try {
                runCommand(block)
            } finally {
                synchronized(processingTableIds) {
                    processingTableIds.remove(tableId)
                }
            }
        }
    }

    private suspend fun runCommand(block: suspend () -> Unit) {
        try {
            commandMutex.withLock { block() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            editorState.update { it.copy(hasCommandError = true) }
        }
    }

    private data class EditorState(
        val isEditMode: Boolean = false,
        val isFullscreenEnabled: Boolean = false,
        val pendingDeleteTableId: String? = null,
        val selectedHookahTableId: String? = null,
        val hasCommandError: Boolean = false,
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
