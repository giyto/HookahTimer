package ru.hznik.hookahtimer.hall.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HallCoordinatesTest {
    @Test
    fun worldPositionConvertsThroughViewportScaleAndOffset() {
        val transform = CanvasTransform(
            scale = 2f,
            offset = CanvasPosition(100f, 50f),
        )

        val result = CanvasPosition(250f, 150f).toScreenPosition(transform)

        assertEquals(300f, result.x, DELTA)
        assertEquals(200f, result.y, DELTA)
    }

    @Test
    fun screenAndWorldConversionsRoundTripAtDifferentScales() {
        listOf(0.35f, 1f, 2.5f).forEach { scale ->
            val transform = CanvasTransform(
                scale = scale,
                offset = CanvasPosition(-120f, 80f),
            )
            val world = CanvasPosition(540f, -40f)

            val restored = world.toScreenPosition(transform).toCanvasPosition(transform)

            assertEquals(world.x, restored.x, DELTA)
            assertEquals(world.y, restored.y, DELTA)
        }
    }

    @Test
    fun screenDeltaIsConvertedWithoutViewportOffset() {
        val transform = CanvasTransform(
            scale = 2f,
            offset = CanvasPosition(1_000f, 1_000f),
        )

        val result = ScreenPosition(80f, -40f).screenDeltaToCanvas(transform)

        assertEquals(40f, result.x, DELTA)
        assertEquals(-20f, result.y, DELTA)
    }

    @Test
    fun factoriesSanitizeNonFiniteInput() {
        assertEquals(CanvasPosition.Origin, CanvasPosition.of(Float.NaN, Float.POSITIVE_INFINITY))
        assertEquals(CanvasSize.Zero, CanvasSize.of(Float.NEGATIVE_INFINITY, Float.NaN))
        assertEquals(ScreenPosition(0f, 0f), ScreenPosition.of(Float.NaN, Float.POSITIVE_INFINITY))
    }

    @Test
    fun constructorsRejectInvalidScaleAndSize() {
        assertThrows(IllegalArgumentException::class.java) {
            CanvasTransform(scale = 0f, offset = CanvasPosition.Origin)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CanvasSize(width = -1f, height = 10f)
        }
    }

    private companion object {
        const val DELTA = 0.0001f
    }
}
