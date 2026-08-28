package ru.hznik.hookahtimer.hall.model

data class PixelPosition(
    val x: Float,
    val y: Float,
)

data class PixelSize(
    val width: Float,
    val height: Float,
)

fun NormalizedPosition.toPixelPosition(
    fieldSize: PixelSize,
    tableSize: PixelSize,
): PixelPosition {
    val availableWidth = availableDistance(fieldSize.width, tableSize.width)
    val availableHeight = availableDistance(fieldSize.height, tableSize.height)
    return PixelPosition(
        x = x * availableWidth,
        y = y * availableHeight,
    )
}

fun PixelPosition.toNormalizedPosition(
    fieldSize: PixelSize,
    tableSize: PixelSize,
): NormalizedPosition {
    val clamped = clampWithin(fieldSize = fieldSize, tableSize = tableSize)
    val availableWidth = availableDistance(fieldSize.width, tableSize.width)
    val availableHeight = availableDistance(fieldSize.height, tableSize.height)
    return NormalizedPosition.of(
        x = if (availableWidth == 0f) 0f else clamped.x / availableWidth,
        y = if (availableHeight == 0f) 0f else clamped.y / availableHeight,
    )
}

fun PixelPosition.clampWithin(
    fieldSize: PixelSize,
    tableSize: PixelSize,
): PixelPosition = PixelPosition(
    x = finiteOrZero(x).coerceIn(0f, availableDistance(fieldSize.width, tableSize.width)),
    y = finiteOrZero(y).coerceIn(0f, availableDistance(fieldSize.height, tableSize.height)),
)

private fun availableDistance(field: Float, item: Float): Float =
    (finiteOrZero(field) - finiteOrZero(item)).coerceAtLeast(0f)

private fun finiteOrZero(value: Float): Float = if (value.isFinite()) value else 0f
