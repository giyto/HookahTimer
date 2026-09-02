package ru.hznik.hookahtimer.hall.data

import kotlinx.coroutines.flow.Flow
import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.CanvasPosition
import ru.hznik.hookahtimer.hall.model.TablePassage
import ru.hznik.hookahtimer.hall.model.TableShape

interface HallRepository {
    val tables: Flow<List<HallTable>>

    suspend fun addTable(
        tableId: String,
        passageIds: List<String>,
        position: CanvasPosition? = null,
    )

    suspend fun moveTable(
        tableId: String,
        position: CanvasPosition,
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

internal fun initialTablePosition(number: Int): CanvasPosition {
    val index = (number - 1).coerceAtLeast(0)
    val column = index % INITIAL_COLUMNS
    val row = index / INITIAL_COLUMNS
    return CanvasPosition(
        x = INITIAL_MARGIN_X + column * INITIAL_COLUMN_SPACING,
        y = INITIAL_MARGIN_Y + row * INITIAL_ROW_SPACING,
    )
}

private const val INITIAL_COLUMNS = 4
private const val INITIAL_MARGIN_X = 120f
private const val INITIAL_MARGIN_Y = 120f
private const val INITIAL_COLUMN_SPACING = 240f
private const val INITIAL_ROW_SPACING = 190f
