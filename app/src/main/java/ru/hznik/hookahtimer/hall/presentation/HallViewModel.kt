package ru.hznik.hookahtimer.hall.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.hznik.hookahtimer.hall.data.HallRepository
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
            pendingTimerConfirmation = editor.pendingTimerConfirmation,
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
            HallAction.AddTable -> addTable()
            is HallAction.MoveTable -> moveTable(action)
            is HallAction.RequestDelete -> requestDelete(action.tableId)
            is HallAction.UpdateTableSettings -> updateTableSettings(action)
            is HallAction.AdvanceTimer -> advanceTimer(action.tableId)
            HallAction.ConfirmTimerTransition -> confirmTimerTransition()
            HallAction.CancelTimerTransition -> cancelTimerTransition()
            HallAction.ConfirmDelete -> confirmDelete()
            HallAction.CancelDelete -> cancelDelete()
        }
    }

    private fun toggleEditMode() {
        editorState.update { current ->
            current.copy(
                isEditMode = !current.isEditMode,
                pendingDeleteTableId = null,
                pendingTimerConfirmation = null,
            )
        }
    }

    private fun toggleFullscreen() {
        editorState.update { current ->
            current.copy(isFullscreenEnabled = !current.isFullscreenEnabled)
        }
    }

    private fun addTable() {
        if (!editorState.value.isEditMode) return
        val tableId = idFactory()
        val passageIds = List(TablePassage.DEFAULT_PASSAGE_COUNT) { passageIdFactory() }
        launchCommand {
            repository.addTable(tableId, passageIds)
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
        val table = state.value.tables.firstOrNull { it.id == tableId }
        if (editor.isEditMode || editor.pendingTimerConfirmation != null || table == null) return

        val nowEpochMillis = timeProvider.nowEpochMillis()
        when (val timerState = table.timerState) {
            TableTimerState.Idle -> advanceTimerImmediately(tableId, nowEpochMillis)
            is TableTimerState.Running -> {
                if (nowEpochMillis >= timerState.endsAtEpochMillis) {
                    advanceTimerImmediately(tableId, nowEpochMillis)
                } else {
                    requestTimerConfirmation(
                        tableId = tableId,
                        type = TimerConfirmationType.ADVANCE_EARLY,
                    )
                }
            }

            TableTimerState.Completed -> requestTimerConfirmation(
                tableId = tableId,
                type = TimerConfirmationType.RESET_COMPLETED,
            )
        }
    }

    private fun advanceTimerImmediately(tableId: String, nowEpochMillis: Long) {
        launchTableCommand(tableId) {
            repository.advanceTimer(tableId, nowEpochMillis)
        }
    }

    private fun requestTimerConfirmation(
        tableId: String,
        type: TimerConfirmationType,
    ) {
        editorState.update { current ->
            if (current.pendingTimerConfirmation != null) {
                current
            } else {
                current.copy(
                    pendingTimerConfirmation = PendingTimerConfirmation(tableId, type),
                )
            }
        }
    }

    private fun confirmTimerTransition() {
        val pending = editorState.value.pendingTimerConfirmation ?: return
        editorState.update { current -> current.copy(pendingTimerConfirmation = null) }
        launchTableCommand(pending.tableId) {
            val table = repository.tables.first().firstOrNull { it.id == pending.tableId }
                ?: return@launchTableCommand
            val isStillApplicable = when (pending.type) {
                TimerConfirmationType.ADVANCE_EARLY ->
                    table.timerState is TableTimerState.Running

                TimerConfirmationType.RESET_COMPLETED ->
                    table.timerState == TableTimerState.Completed
            }
            if (isStillApplicable) {
                repository.advanceTimer(
                    tableId = pending.tableId,
                    nowEpochMillis = timeProvider.nowEpochMillis(),
                )
            }
        }
    }

    private fun cancelTimerTransition() {
        editorState.update { current ->
            if (current.pendingTimerConfirmation == null) {
                current
            } else {
                current.copy(pendingTimerConfirmation = null)
            }
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
        val pendingTimerConfirmation: PendingTimerConfirmation? = null,
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
