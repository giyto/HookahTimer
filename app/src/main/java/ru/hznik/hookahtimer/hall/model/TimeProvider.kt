package ru.hznik.hookahtimer.hall.model

fun interface TimeProvider {
    fun nowEpochMillis(): Long
}

data object SystemTimeProvider : TimeProvider {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
