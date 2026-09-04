package ru.hznik.hookahtimer.hall.model

data class HallTable(
    val id: String,
    val name: String,
    val shape: TableShape = TableShape.CIRCLE,
    val position: CanvasPosition = CanvasPosition.Default,
    val passages: List<TablePassage> = TablePassage.defaultList(),
    val hookahs: List<TableHookah> = listOf(TableHookah.initial(id)),
) {
    // Совместимый вход для одиночного таймера: состояние хранится только в кальяне.
    constructor(
        id: String,
        name: String,
        shape: TableShape = TableShape.CIRCLE,
        position: CanvasPosition = CanvasPosition.Default,
        passages: List<TablePassage> = TablePassage.defaultList(),
        timerState: TableTimerState,
    ) : this(id, name, shape, position, passages, listOf(TableHookah.initial(id).copy(timerState = timerState)))

    val isIdle: Boolean get() = hookahs.size == 1 && hookahs.single().number == 1 &&
        hookahs.single().timerState == TableTimerState.Idle
    val isCompleted: Boolean get() = hookahs.all { it.timerState == TableTimerState.Completed }
    val hasMultipleHookahs: Boolean get() = hookahs.size > 1
    val mostUrgentHookah: TableHookah?
        get() = hookahs.filter { it.timerState is TableTimerState.Running }
            .minWithOrNull(compareBy<TableHookah> {
                (it.timerState as TableTimerState.Running).endsAtEpochMillis
            }.thenBy { it.number })

    val timerState: TableTimerState
        get() = mostUrgentHookah?.timerState ?: if (isCompleted) TableTimerState.Completed else TableTimerState.Idle

    fun copy(timerState: TableTimerState): HallTable {
        require(!hasMultipleHookahs) { "Use an addressed hookah transition for multiple hookahs" }
        return copy(hookahs = listOf(hookahs.single().copy(timerState = timerState)))
    }

    init {
        require(name.isNotBlank()) { "Table name must not be blank" }
        require(passages.isNotEmpty()) { "Table must contain at least one passage" }
        require(passages.distinctBy { it.id }.size == passages.size) {
            "Passage ids must be unique within a table"
        }
        require(hookahs.isNotEmpty()) { "Table must contain at least one hookah" }
        require(hookahs.distinctBy { it.id }.size == hookahs.size) { "Hookah ids must be unique" }
        require(hookahs.distinctBy { it.number }.size == hookahs.size) { "Hookah numbers must be unique" }
        require(hookahs == hookahs.sortedBy { it.number }) { "Hookahs must be ordered by number" }
        hookahs.forEach { hookah ->
            val running = hookah.timerState as? TableTimerState.Running
            require(running == null || passages.any { it.id == running.passageId }) {
                "Running timer must reference an existing passage"
            }
        }
    }
}

enum class TableShape {
    CIRCLE,
    PILL,
}

data class CanvasPosition(
    val x: Float,
    val y: Float,
) {
    init {
        require(x.isFinite()) { "x must be finite" }
        require(y.isFinite()) { "y must be finite" }
    }

    companion object {
        val Origin = CanvasPosition(x = 0f, y = 0f)
        val Default = CanvasPosition(x = 160f, y = 140f)

        fun of(x: Float, y: Float): CanvasPosition = CanvasPosition(
            x = x.finiteOrZero(),
            y = y.finiteOrZero(),
        )
    }
}

private fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f
