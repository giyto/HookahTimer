package ru.hznik.hookahtimer.hall.model

sealed interface TableTimerState {
    data object Idle : TableTimerState

    data class Running(
        val passageId: String,
        val endsAtEpochMillis: Long,
    ) : TableTimerState {
        init {
            require(passageId.isNotBlank()) { "Running passage id must not be blank" }
            require(endsAtEpochMillis > 0L) { "Timer end time must be positive" }
        }
    }

    data object Completed : TableTimerState
}
