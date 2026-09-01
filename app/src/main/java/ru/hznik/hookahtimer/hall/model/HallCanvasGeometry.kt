package ru.hznik.hookahtimer.hall.model

import kotlin.math.max

data class CanvasRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite())
        require(right >= left && bottom >= top)
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class CanvasItemBounds(
    val position: CanvasPosition,
    val width: Float,
    val height: Float,
) {
    init {
        require(width.isFinite() && height.isFinite())
        require(width >= 0f && height >= 0f)
    }
}

data class CanvasViewport(
    val scale: Float = 1f,
    val offset: CanvasPosition = CanvasPosition.Origin,
) {
    init {
        require(scale.isFinite() && scale > 0f)
    }

    fun zoomBy(
        zoomFactor: Float,
        focus: CanvasPosition,
        contentBounds: CanvasRect,
        viewportSize: CanvasSize,
    ): CanvasViewport {
        if (!zoomFactor.isFinite() || zoomFactor <= 0f) return this
        val newScale = (scale * zoomFactor).coerceIn(MIN_CANVAS_SCALE, MAX_CANVAS_SCALE)
        val effectiveFactor = newScale / scale
        val newOffset = CanvasPosition.of(
            x = focus.x - (focus.x - offset.x) / effectiveFactor,
            y = focus.y - (focus.y - offset.y) / effectiveFactor,
        )
        return copy(scale = newScale, offset = newOffset)
            .clampTo(contentBounds, viewportSize)
    }

    fun panBy(
        canvasDeltaX: Float,
        canvasDeltaY: Float,
        contentBounds: CanvasRect,
        viewportSize: CanvasSize,
    ): CanvasViewport = copy(
        offset = CanvasPosition.of(
            x = offset.x - canvasDeltaX,
            y = offset.y - canvasDeltaY,
        ),
    ).clampTo(contentBounds, viewportSize)

    fun clampTo(contentBounds: CanvasRect, viewportSize: CanvasSize): CanvasViewport {
        val safeScale = scale.coerceIn(MIN_CANVAS_SCALE, MAX_CANVAS_SCALE)
        val visibleWidth = viewportSize.width / safeScale
        val visibleHeight = viewportSize.height / safeScale
        return copy(
            scale = safeScale,
            offset = CanvasPosition.of(
                x = clampAxis(offset.x, contentBounds.left, contentBounds.right, visibleWidth),
                y = clampAxis(offset.y, contentBounds.top, contentBounds.bottom, visibleHeight),
            ),
        )
    }
}

data class GridDetail(
    val showMinorLines: Boolean,
    val minorStep: Float = MINOR_GRID_STEP,
    val majorStep: Float = MAJOR_GRID_STEP,
)

fun calculateCanvasContentBounds(
    items: List<CanvasItemBounds>,
    viewportSize: CanvasSize,
    margin: Float = CANVAS_CONTENT_MARGIN,
): CanvasRect {
    require(margin.isFinite() && margin >= 0f)
    val fallbackRight = max(viewportSize.width, 1f)
    val fallbackBottom = max(viewportSize.height, 1f)
    if (items.isEmpty()) {
        return CanvasRect(0f, 0f, fallbackRight, fallbackBottom)
    }

    val contentLeft = items.minOf { it.position.x }
    val contentTop = items.minOf { it.position.y }
    val contentRight = items.maxOf { it.position.x + it.width }
    val contentBottom = items.maxOf { it.position.y + it.height }
    return CanvasRect(
        left = if (contentLeft < 0f) contentLeft - margin else 0f,
        top = if (contentTop < 0f) contentTop - margin else 0f,
        right = max(fallbackRight, contentRight + margin),
        bottom = max(fallbackBottom, contentBottom + margin),
    )
}

fun gridDetailForScale(scale: Float): GridDetail {
    val safeScale = scale.takeIf { it.isFinite() && it > 0f } ?: 1f
    return GridDetail(showMinorLines = MINOR_GRID_STEP * safeScale >= MINOR_GRID_VISIBLE_DP)
}

private fun clampAxis(value: Float, start: Float, end: Float, visibleSize: Float): Float {
    if (visibleSize >= end - start) {
        return start - (visibleSize - (end - start)) / 2f
    }
    return value.coerceIn(start, end - visibleSize)
}

const val MIN_CANVAS_SCALE = 0.35f
const val MAX_CANVAS_SCALE = 2.5f
const val CANVAS_CONTENT_MARGIN = 240f
const val MINOR_GRID_STEP = 40f
const val MAJOR_GRID_STEP = 200f
const val MINOR_GRID_VISIBLE_DP = 16f
