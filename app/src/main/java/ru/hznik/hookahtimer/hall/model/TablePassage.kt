package ru.hznik.hookahtimer.hall.model

import java.util.UUID

data class TablePassage(
    val id: String,
    val durationMinutes: Int,
) {
    init {
        require(id.isNotBlank()) { "Passage id must not be blank" }
        require(durationMinutes > 0) { "Passage duration must be positive" }
    }

    companion object {
        const val DEFAULT_DURATION_MINUTES = 30
        const val DEFAULT_PASSAGE_COUNT = 2

        fun defaultList(
            idFactory: () -> String = { UUID.randomUUID().toString() },
        ): List<TablePassage> = List(DEFAULT_PASSAGE_COUNT) {
            TablePassage(
                id = idFactory(),
                durationMinutes = DEFAULT_DURATION_MINUTES,
            )
        }
    }
}
