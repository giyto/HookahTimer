package ru.hznik.hookahtimer.hall.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TablePassageTest {
    @Test
    fun defaultListContainsTwoIndependentThirtyMinutePassages() {
        val ids = ArrayDeque(listOf("passage-1", "passage-2"))

        val passages = TablePassage.defaultList { ids.removeFirst() }

        assertEquals(listOf("passage-1", "passage-2"), passages.map { it.id })
        assertEquals(listOf(30, 30), passages.map { it.durationMinutes })
        assertNotEquals(passages[0].id, passages[1].id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroDurationIsRejected() {
        TablePassage(id = "passage", durationMinutes = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankIdIsRejected() {
        TablePassage(id = " ", durationMinutes = 30)
    }
}
