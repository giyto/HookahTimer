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
    fun compactBoundsAreSymmetricAroundContent() {
        val bounds = calculateCanvasContentBounds(
            items = listOf(CanvasItemBounds(CanvasPosition(100f, 80f), 112f, 112f)),
            viewportSize = CanvasSize(800f, 600f),
        )
        assertEquals(CanvasRect(-140f, -160f, 452f, 432f), bounds)
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
        assertFalse(gridDetailForScale(0.399f).showMinorLines)
        assertTrue(gridDetailForScale(0.4f).showMinorLines)
        assertTrue(gridDetailForScale(1f).showMinorLines)
        assertEquals(200f, gridDetailForScale(1f).majorStep, 0f)
    }

    @Test
    fun viewportReportsVisibleRectAndWorldCenter() {
        val viewport = CanvasViewport(
            scale = 2f,
            offset = CanvasPosition(100f, 50f),
        )
        val size = CanvasSize(800f, 600f)

        assertEquals(CanvasRect(100f, 50f, 500f, 350f), viewport.visibleRect(size))
        assertEquals(CanvasPosition(300f, 200f), viewport.center(size))
    }

    @Test
    fun freeTablePlacementCentersTableInVisibleBounds() {
        val result = findNearestAvailableTablePosition(
            preferredCenter = CanvasPosition(500f, 400f),
            tableSize = CanvasSize(112f, 112f),
            occupiedItems = emptyList(),
            visibleBounds = CanvasRect(100f, 100f, 900f, 700f),
        )

        assertEquals(CanvasPosition(444f, 344f), result)
    }

    @Test
    fun occupiedCenterUsesNearestVisibleFreeGridPosition() {
        val occupied = CanvasItemBounds(CanvasPosition(444f, 344f), 112f, 112f)

        val result = findNearestAvailableTablePosition(
            preferredCenter = CanvasPosition(500f, 400f),
            tableSize = CanvasSize(112f, 112f),
            occupiedItems = listOf(occupied),
            visibleBounds = CanvasRect(100f, 100f, 900f, 700f),
        )

        assertTrue(result != occupied.position)
        assertTrue(result.x >= 100f && result.x + 112f <= 900f)
        assertTrue(result.y >= 100f && result.y + 112f <= 700f)
    }

    @Test
    fun fullViewportFallsBackToClampedCenter() {
        val result = findNearestAvailableTablePosition(
            preferredCenter = CanvasPosition(100f, 100f),
            tableSize = CanvasSize(112f, 112f),
            occupiedItems = listOf(CanvasItemBounds(CanvasPosition(0f, 0f), 200f, 200f)),
            visibleBounds = CanvasRect(0f, 0f, 200f, 200f),
        )

        assertEquals(CanvasPosition(44f, 44f), result)
    }

    @Test
    fun compactContentCannotBePannedIntoEmptySpace() {
        val bounds = CanvasRect(-140f, -160f, 452f, 432f)
        val size = CanvasSize(800f, 600f)

        val first = CanvasViewport(offset = CanvasPosition(-10_000f, -10_000f))
            .clampTo(bounds, size)
        val second = CanvasViewport(offset = CanvasPosition(10_000f, 10_000f))
            .clampTo(bounds, size)

        assertEquals(first, second)
        assertEquals(CanvasPosition(-244f, -164f), first.offset)
    }

    @Test
    fun largeContentCanReachBothExtremeEdges() {
        val bounds = CanvasRect(-500f, -300f, 1_500f, 1_300f)
        val size = CanvasSize(800f, 600f)

        val topLeft = CanvasViewport(offset = CanvasPosition(-50_000f, -50_000f))
            .clampTo(bounds, size)
        val bottomRight = CanvasViewport(offset = CanvasPosition(50_000f, 50_000f))
            .clampTo(bounds, size)

        assertEquals(CanvasPosition(-500f, -300f), topLeft.offset)
        assertEquals(CanvasPosition(700f, 700f), bottomRight.offset)
    }
}
