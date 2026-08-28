package ru.hznik.hookahtimer.hall.model

data class HallTable(
    val id: String,
    val name: String,
    val shape: TableShape = TableShape.CIRCLE,
    val position: NormalizedPosition = NormalizedPosition.Center,
)

enum class TableShape {
    CIRCLE,
    PILL,
}

data class NormalizedPosition(
    val x: Float,
    val y: Float,
) {
    init {
        require(x.isFinite() && x in 0f..1f) { "x must be finite and between 0 and 1" }
        require(y.isFinite() && y in 0f..1f) { "y must be finite and between 0 and 1" }
    }

    companion object {
        val Center = NormalizedPosition(x = 0.5f, y = 0.5f)

        fun of(x: Float, y: Float): NormalizedPosition = NormalizedPosition(
            x = x.normalizedCoordinate(),
            y = y.normalizedCoordinate(),
        )
    }
}

private fun Float.normalizedCoordinate(): Float =
    if (isFinite()) coerceIn(0f, 1f) else 0f
