package ru.hznik.hookahtimer.hall.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HallCanvasGeometryTest {
    @Test
    fun emptyBoundsUseViewport() {
        assertEquals(
            CanvasRect(0f, 0f, 800f, 600f),
            calculateCanvasContentBounds(emptyList(), CanvasSize(800f, 600f)),
        )
    }

    @Test
    fun compactBoundsKeepViewportAsMinimum() {
        val bounds = calculateCanvasContentBounds(
            items = listOf(CanvasItemBounds(CanvasPosition(100f, 80f), 112f, 112f)),
            viewportSize = CanvasSize(800f, 600f),
        )
        assertEquals(CanvasRect(0f, 0f, 800f, 600f), bounds)
    }

    @Test
    fun largeAndNegativeHallExpandsEverySide() {
        val bounds = calculateCanvasContentBounds(
            items = listOf(
                CanvasItemBounds(CanvasPosition(-300f, -100f), 112f, 112f),
                CanvasItemBounds(CanvasPosition(1_400f, 900f), 184f, 96f),
            ),
            viewportSize = CanvasSize(800f, 600f),
        )
        assertEquals(CanvasRect(-540f, -340f, 1_824f, 1_236f), bounds)
    }

    @Test
    fun zoomKeepsFocusAtSameScreenPoint() {
        val bounds = CanvasRect(-1_000f, -1_000f, 3_000f, 3_000f)
        val viewportSize = CanvasSize(800f, 600f)
        val before = CanvasViewport(scale = 1f, offset = CanvasPosition(100f, 50f))
        val focus = CanvasPosition(300f, 250f)
        val screenBefore = focus.toScreenPosition(CanvasTransform(before.scale, before.offset))

        val after = before.zoomBy(2f, focus, bounds, viewportSize)
        val screenAfter = focus.toScreenPosition(CanvasTransform(after.scale, after.offset))

        assertEquals(screenBefore.x, screenAfter.x, 0.001f)
        assertEquals(screenBefore.y, screenAfter.y, 0.001f)
    }

    @Test
    fun panAndScaleAreClamped() {
        val bounds = CanvasRect(0f, 0f, 2_000f, 1_600f)
        val viewportSize = CanvasSize(800f, 600f)
        val zoomed = CanvasViewport().zoomBy(100f, CanvasPosition.Origin, bounds, viewportSize)
        val panned = zoomed.panBy(-50_000f, -50_000f, bounds, viewportSize)

        assertEquals(MAX_CANVAS_SCALE, zoomed.scale, 0f)
        assertEquals(1_680f, panned.offset.x, 0.001f)
        assertEquals(1_360f, panned.offset.y, 0.001f)
    }

    @Test
    fun minorGridDisappearsWhenItsScreenStepIsTooSmall() {
        assertFalse(gridDetailForScale(MIN_CANVAS_SCALE).showMinorLines)
        assertTrue(gridDetailForScale(1f).showMinorLines)
        assertEquals(200f, gridDetailForScale(1f).majorStep, 0f)
    }
}
