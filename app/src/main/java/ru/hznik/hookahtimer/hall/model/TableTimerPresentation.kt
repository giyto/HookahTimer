package ru.hznik.hookahtimer.hall.model

data class TableTimerPresentation(
    val timerText: String? = null,
    val passageNumber: Int? = null,
    val isEndingSoon: Boolean = false,
    val isOverdue: Boolean = false,
    val isCompleted: Boolean = false,
)

fun HallTable.timerPresentation(nowEpochMillis: Long): TableTimerPresentation =
    when (val state = timerState) {
        TableTimerState.Idle -> TableTimerPresentation()
        TableTimerState.Completed -> TableTimerPresentation(isCompleted = true)
        is TableTimerState.Running -> {
            val passageIndex = passages.indexOfFirst { it.id == state.passageId }
            val configuredDurationMillis = passages[passageIndex].durationMinutes.toLong() *
                MILLIS_PER_MINUTE
            val remainingMillis = (state.endsAtEpochMillis - nowEpochMillis)
                .coerceAtMost(configuredDurationMillis)
            if (remainingMillis > 0L) {
                val remainingSeconds = remainingMillis.ceilToSeconds()
                TableTimerPresentation(
                    timerText = formatTimerSeconds(remainingSeconds),
                    passageNumber = passageIndex + 1,
                    isEndingSoon = remainingSeconds <= ENDING_SOON_THRESHOLD_SECONDS,
                )
            } else {
                val overdueSeconds = ((nowEpochMillis - state.endsAtEpochMillis).coerceAtLeast(0L)) /
                    MILLIS_PER_SECOND
                TableTimerPresentation(
                    timerText = if (overdueSeconds == 0L) {
                        formatTimerSeconds(0L)
                    } else {
                        "-${formatTimerSeconds(overdueSeconds)}"
                    },
                    passageNumber = passageIndex + 1,
                    isOverdue = true,
                )
            }
        }
    }

fun formatTimerSeconds(totalSeconds: Long): String {
    require(totalSeconds >= 0L) { "Timer seconds must not be negative" }
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return "%02d:%02d".format(minutes, seconds)
}

private fun Long.ceilToSeconds(): Long =
    this / MILLIS_PER_SECOND + if (this % MILLIS_PER_SECOND == 0L) 0L else 1L

private const val MILLIS_PER_SECOND = 1_000L
private const val MILLIS_PER_MINUTE = 60_000L
private const val SECONDS_PER_MINUTE = 60L
private const val ENDING_SOON_THRESHOLD_SECONDS = 60L
