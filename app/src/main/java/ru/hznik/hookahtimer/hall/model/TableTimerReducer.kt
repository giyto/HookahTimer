package ru.hznik.hookahtimer.hall.model

fun advanceTableTimer(
    table: HallTable,
    nowEpochMillis: Long,
): TableTimerState {
    require(nowEpochMillis >= 0L) { "Current epoch time must not be negative" }

    require(!table.hasMultipleHookahs) { "Select a hookah before advancing its timer" }
    return if (table.isCompleted) TableTimerState.Idle else advanceHookahTimer(
        table.hookahs.single(), table.passages, nowEpochMillis,
    )
}

fun advanceHookahTimer(
    hookah: TableHookah,
    passages: List<TablePassage>,
    nowEpochMillis: Long,
): TableTimerState {
    require(nowEpochMillis >= 0L) { "Current epoch time must not be negative" }
    return when (val state = hookah.timerState) {
        TableTimerState.Idle -> passages.first().runningFrom(nowEpochMillis)
        is TableTimerState.Running -> {
            val currentIndex = passages.indexOfFirst { it.id == state.passageId }
            require(currentIndex >= 0) { "Unknown passage" }
            val nextPassage = passages.getOrNull(currentIndex + 1)
            nextPassage?.runningFrom(nowEpochMillis) ?: TableTimerState.Completed
        }
        TableTimerState.Completed -> TableTimerState.Completed
    }
}

fun HallTable.withAddedHookah(hookahId: String, nowEpochMillis: Long): HallTable {
    if (isIdle) return copy(
        hookahs = listOf(TableHookah(hookahId, 1, passages.first().runningFrom(nowEpochMillis))),
    )
    if (hookahs.any { it.id == hookahId }) return this
    val hookah = TableHookah(
        id = hookahId,
        number = Math.addExact(hookahs.maxOf { it.number }, 1),
        timerState = passages.first().runningFrom(nowEpochMillis),
    )
    return copy(hookahs = hookahs + hookah)
}

fun HallTable.withAdvancedHookah(hookahId: String, nowEpochMillis: Long): HallTable = copy(
    hookahs = hookahs.map { hookah ->
        if (hookah.id == hookahId) {
            hookah.copy(timerState = advanceHookahTimer(hookah, passages, nowEpochMillis))
        } else hookah
    },
)

fun HallTable.withResetHookahs(initialHookahId: String): HallTable =
    if (isCompleted) copy(hookahs = listOf(TableHookah(initialHookahId, 1))) else this

private fun TablePassage.runningFrom(nowEpochMillis: Long): TableTimerState.Running {
    require(nowEpochMillis >= 0L) { "Current epoch time must not be negative" }
    val durationMillis = durationMinutes.toLong() * MILLIS_PER_MINUTE
    return TableTimerState.Running(
        passageId = id,
        endsAtEpochMillis = Math.addExact(nowEpochMillis, durationMillis),
    )
}

private const val MILLIS_PER_MINUTE = 60_000L
