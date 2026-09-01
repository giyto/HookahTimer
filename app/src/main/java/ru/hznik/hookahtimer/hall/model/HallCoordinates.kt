package ru.hznik.hookahtimer.hall.model

data class ScreenPosition(
    val x: Float,
    val y: Float,
) {
    init {
        require(x.isFinite()) { "x must be finite" }
        require(y.isFinite()) { "y must be finite" }
    }

    companion object {
        fun of(x: Float, y: Float): ScreenPosition = ScreenPosition(
            x = x.finiteOrZero(),
            y = y.finiteOrZero(),
        )
    }
}

data class CanvasSize(
    val width: Float,
    val height: Float,
) {
    init {
        require(width.isFinite() && width >= 0f) { "width must be finite and non-negative" }
        require(height.isFinite() && height >= 0f) { "height must be finite and non-negative" }
    }

    companion object {
        val Zero = CanvasSize(0f, 0f)

        fun of(width: Float, height: Float): CanvasSize = CanvasSize(
            width = width.finiteOrZero().coerceAtLeast(0f),
            height = height.finiteOrZero().coerceAtLeast(0f),
        )
    }
}

data class CanvasTransform(
    val scale: Float,
    val offset: CanvasPosition,
) {
    init {
        require(scale.isFinite() && scale > 0f) { "scale must be finite and positive" }
    }
}

fun CanvasPosition.toScreenPosition(transform: CanvasTransform): ScreenPosition =
    ScreenPosition(
        x = (x - transform.offset.x) * transform.scale,
        y = (y - transform.offset.y) * transform.scale,
    )

fun ScreenPosition.toCanvasPosition(transform: CanvasTransform): CanvasPosition =
    CanvasPosition(
        x = x / transform.scale + transform.offset.x,
        y = y / transform.scale + transform.offset.y,
    )

fun ScreenPosition.screenDeltaToCanvas(transform: CanvasTransform): CanvasPosition =
    CanvasPosition(
        x = x / transform.scale,
        y = y / transform.scale,
    )

private fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f
