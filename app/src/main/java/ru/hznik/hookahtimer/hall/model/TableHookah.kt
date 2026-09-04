package ru.hznik.hookahtimer.hall.model

data class TableHookah(
    val id: String,
    val number: Int,
    val timerState: TableTimerState = TableTimerState.Idle,
) {
    init {
        require(id.isNotBlank()) { "Hookah id must not be blank" }
        require(number > 0) { "Hookah number must be positive" }
    }

    companion object {
        fun initial(tableId: String): TableHookah = TableHookah("initial:$tableId", 1)
    }
}
