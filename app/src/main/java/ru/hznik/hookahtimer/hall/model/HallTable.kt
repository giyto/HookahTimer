package ru.hznik.hookahtimer.hall.model

data class HallTable(
    val id: String,
    val name: String,
    val shape: TableShape = TableShape.CIRCLE,
    val position: CanvasPosition = CanvasPosition.Default,
    val passages: List<TablePassage> = TablePassage.defaultList(),
    val timerState: TableTimerState = TableTimerState.Idle,
) {
    init {
        require(name.isNotBlank()) { "Table name must not be blank" }
        require(passages.isNotEmpty()) { "Table must contain at least one passage" }
        require(passages.distinctBy { it.id }.size == passages.size) {
            "Passage ids must be unique within a table"
        }
        if (timerState is TableTimerState.Running) {
            require(passages.any { it.id == timerState.passageId }) {
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
