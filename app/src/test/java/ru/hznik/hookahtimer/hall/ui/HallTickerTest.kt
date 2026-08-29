package ru.hznik.hookahtimer.hall.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HallTickerTest {
    @Test
    fun nextTickIsAlignedToNextWholeSecond() {
        assertEquals(1_000L, nextTimerTickDelayMillis(10_000L))
        assertEquals(999L, nextTimerTickDelayMillis(10_001L))
        assertEquals(1L, nextTimerTickDelayMillis(10_999L))
    }
}
