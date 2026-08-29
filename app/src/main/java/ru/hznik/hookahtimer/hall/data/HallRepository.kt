package ru.hznik.hookahtimer.hall.data

import kotlinx.coroutines.flow.Flow
import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.NormalizedPosition
import ru.hznik.hookahtimer.hall.model.TablePassage
import ru.hznik.hookahtimer.hall.model.TableShape

interface HallRepository {
    val tables: Flow<List<HallTable>>

    suspend fun addTable(
        tableId: String,
        passageIds: List<String>,
    )

    suspend fun moveTable(
        tableId: String,
        position: NormalizedPosition,
    )

    suspend fun updateTableSettings(
        tableId: String,
        name: String,
        shape: TableShape,
        passages: List<TablePassage>,
    )

    suspend fun deleteTable(tableId: String)

    suspend fun advanceTimer(
        tableId: String,
        nowEpochMillis: Long,
    )
}

internal fun initialTablePosition(number: Int): NormalizedPosition {
    val index = (number - 1).coerceAtLeast(0)
    val column = index % INITIAL_COLUMNS
    val row = (index / INITIAL_COLUMNS) % INITIAL_ROWS
    return NormalizedPosition.of(
        x = (column + 1f) / (INITIAL_COLUMNS + 1f),
        y = (row + 1f) / (INITIAL_ROWS + 1f),
    )
}

private const val INITIAL_COLUMNS = 4
private const val INITIAL_ROWS = 3
