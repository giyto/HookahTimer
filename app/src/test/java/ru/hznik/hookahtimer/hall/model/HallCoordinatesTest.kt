package ru.hznik.hookahtimer.hall.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HallCoordinatesTest {
    private val field = PixelSize(width = 500f, height = 400f)
    private val table = PixelSize(width = 100f, height = 80f)

    @Test
    fun normalizedPositionConvertsToAvailablePixelArea() {
        val result = NormalizedPosition.of(x = 0.5f, y = 0.25f)
            .toPixelPosition(fieldSize = field, tableSize = table)

        assertEquals(200f, result.x, DELTA)
        assertEquals(80f, result.y, DELTA)
    }

    @Test
    fun pixelPositionInsideBoundsIsPreserved() {
        val result = PixelPosition(x = 120f, y = 160f)
            .clampWithin(fieldSize = field, tableSize = table)

        assertEquals(120f, result.x, DELTA)
        assertEquals(160f, result.y, DELTA)
    }

    @Test
    fun pixelPositionIsClampedAtEveryEdge() {
        val topLeft = PixelPosition(x = -10f, y = -20f)
            .clampWithin(fieldSize = field, tableSize = table)
        val bottomRight = PixelPosition(x = 900f, y = 800f)
            .clampWithin(fieldSize = field, tableSize = table)

        assertEquals(0f, topLeft.x, DELTA)
        assertEquals(0f, topLeft.y, DELTA)
        assertEquals(400f, bottomRight.x, DELTA)
        assertEquals(320f, bottomRight.y, DELTA)
    }

    @Test
    fun outOfBoundsPixelsNormalizeToNearestEdge() {
        val result = PixelPosition(x = -20f, y = 500f)
            .toNormalizedPosition(fieldSize = field, tableSize = table)

        assertEquals(0f, result.x, DELTA)
        assertEquals(1f, result.y, DELTA)
    }

    @Test
    fun fieldSmallerThanTableUsesOrigin() {
        val smallField = PixelSize(width = 50f, height = 40f)
        val pixel = NormalizedPosition.Center.toPixelPosition(
            fieldSize = smallField,
            tableSize = table,
        )
        val normalized = PixelPosition(x = 100f, y = 100f).toNormalizedPosition(
            fieldSize = smallField,
            tableSize = table,
        )

        assertEquals(0f, pixel.x, DELTA)
        assertEquals(0f, pixel.y, DELTA)
        assertEquals(0f, normalized.x, DELTA)
        assertEquals(0f, normalized.y, DELTA)
    }

    private companion object {
        const val DELTA = 0.0001f
    }
}
