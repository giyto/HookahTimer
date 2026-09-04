package ru.hznik.hookahtimer.hall.data.local

import ru.hznik.hookahtimer.hall.model.CanvasPosition
import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.TableHookah
import ru.hznik.hookahtimer.hall.model.TablePassage
import ru.hznik.hookahtimer.hall.model.TableShape
import ru.hznik.hookahtimer.hall.model.TableTimerState

internal data class PersistedHallTable(
    val table: HallTableEntity,
    val passages: List<TablePassageEntity>,
    val hookahs: List<TableHookahEntity>,
)

internal fun HallTable.toPersisted(sortOrder: Int): PersistedHallTable =
    PersistedHallTable(
        table = HallTableEntity(
            id = id,
            name = name,
            shape = shape.name,
            positionX = position.toLegacyNormalizedX(shape),
            positionY = position.toLegacyNormalizedY(shape),
            canvasX = position.x,
            canvasY = position.y,
            sortOrder = sortOrder,
        ),
        passages = passages.mapIndexed { index, passage ->
            TablePassageEntity(passage.id, id, passage.durationMinutes, index)
        },
        hookahs = hookahs.map { it.toPersisted(id) },
    )

internal fun TableHookah.toPersisted(tableId: String): TableHookahEntity {
    val running = timerState as? TableTimerState.Running
    return TableHookahEntity(
        id = id,
        tableId = tableId,
        number = number,
        timerStatus = when (timerState) {
            TableTimerState.Idle -> TimerStatus.IDLE.name
            TableTimerState.Completed -> TimerStatus.COMPLETED.name
            is TableTimerState.Running -> TimerStatus.RUNNING.name
        },
        currentPassageId = running?.passageId,
        endsAtEpochMillis = running?.endsAtEpochMillis,
    )
}

internal fun TableWithPassages.toDomain(): HallTable {
    val domainPassages = passages.sortedBy { it.sortOrder }
        .map { TablePassage(it.id, it.durationMinutes) }
    return HallTable(
        id = table.id,
        name = table.name,
        shape = TableShape.entries.firstOrNull { it.name == table.shape } ?: TableShape.CIRCLE,
        position = CanvasPosition.of(table.canvasX, table.canvasY),
        passages = domainPassages,
        hookahs = hookahs.sortedBy { it.number }.map { hookah ->
            TableHookah(hookah.id, hookah.number, hookah.toDomainTimer(domainPassages))
        }.ifEmpty { listOf(TableHookah.initial(table.id)) },
    )
}

internal fun TableWithPassages.requiresTimerRepair(): Boolean =
    hookahs.isEmpty() || hookahs.any { it.requiresTimerRepair(passages.map { passage -> passage.id }) }

internal fun TableHookahEntity.requiresTimerRepair(passageIds: List<String>): Boolean =
    when (timerStatus) {
        TimerStatus.RUNNING.name ->
            currentPassageId.isNullOrBlank() ||
                endsAtEpochMillis == null ||
                endsAtEpochMillis <= 0L ||
                currentPassageId !in passageIds
        TimerStatus.IDLE.name, TimerStatus.COMPLETED.name ->
            currentPassageId != null || endsAtEpochMillis != null
        else -> true
    }

private fun TableHookahEntity.toDomainTimer(passages: List<TablePassage>): TableTimerState =
    when (timerStatus) {
        TimerStatus.RUNNING.name -> {
            val passageId = currentPassageId
            val endTime = endsAtEpochMillis
            if (!passageId.isNullOrBlank() && endTime != null && endTime > 0L &&
                passages.any { it.id == passageId }
            ) {
                TableTimerState.Running(passageId, endTime)
            } else {
                TableTimerState.Idle
            }
        }
        TimerStatus.COMPLETED.name -> TableTimerState.Completed
        else -> TableTimerState.Idle
    }

internal enum class TimerStatus { IDLE, RUNNING, COMPLETED }

internal fun CanvasPosition.toLegacyNormalizedX(shape: TableShape): Float {
    val tableWidth = when (shape) {
        TableShape.CIRCLE -> LEGACY_CIRCLE_SIZE
        TableShape.PILL -> LEGACY_PILL_WIDTH
    }
    return (x / (LEGACY_CANVAS_WIDTH - tableWidth)).coerceIn(0f, 1f)
}

internal fun CanvasPosition.toLegacyNormalizedY(shape: TableShape): Float {
    val tableHeight = when (shape) {
        TableShape.CIRCLE -> LEGACY_CIRCLE_SIZE
        TableShape.PILL -> LEGACY_PILL_HEIGHT
    }
    return (y / (LEGACY_CANVAS_HEIGHT - tableHeight)).coerceIn(0f, 1f)
}
