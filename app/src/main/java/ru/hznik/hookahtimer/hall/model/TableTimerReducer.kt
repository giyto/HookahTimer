package ru.hznik.hookahtimer.hall.model

fun advanceTableTimer(
    table: HallTable,
    nowEpochMillis: Long,
): TableTimerState {
    require(nowEpochMillis >= 0L) { "Current epoch time must not be negative" }

    return when (val state = table.timerState) {
        TableTimerState.Idle -> table.passages.first().runningFrom(nowEpochMillis)
        is TableTimerState.Running -> {
            val currentIndex = table.passages.indexOfFirst { it.id == state.passageId }
            val nextPassage = table.passages.getOrNull(currentIndex + 1)
            nextPassage?.runningFrom(nowEpochMillis) ?: TableTimerState.Completed
        }
        TableTimerState.Completed -> TableTimerState.Idle
    }
}

private fun TablePassage.runningFrom(nowEpochMillis: Long): TableTimerState.Running {
    val durationMillis = durationMinutes.toLong() * MILLIS_PER_MINUTE
    return TableTimerState.Running(
        passageId = id,
        endsAtEpochMillis = Math.addExact(nowEpochMillis, durationMillis),
    )
}

private const val MILLIS_PER_MINUTE = 60_000L
