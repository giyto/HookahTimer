package ru.hznik.hookahtimer.hall.data.local

import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.CanvasPosition
import ru.hznik.hookahtimer.hall.model.TablePassage
import ru.hznik.hookahtimer.hall.model.TableShape
import ru.hznik.hookahtimer.hall.model.TableTimerState

internal data class PersistedHallTable(
    val table: HallTableEntity,
    val passages: List<TablePassageEntity>,
)

internal fun HallTable.toPersisted(sortOrder: Int): PersistedHallTable {
    val persistedTimer = timerState.toPersistedTimer()
    return PersistedHallTable(
        table = HallTableEntity(
            id = id,
            name = name,
            shape = shape.name,
            positionX = position.toLegacyNormalizedX(shape),
            positionY = position.toLegacyNormalizedY(shape),
            canvasX = position.x,
            canvasY = position.y,
            sortOrder = sortOrder,
            timerStatus = persistedTimer.status,
            currentPassageId = persistedTimer.passageId,
            endsAtEpochMillis = persistedTimer.endsAtEpochMillis,
        ),
        passages = passages.mapIndexed { index, passage ->
            TablePassageEntity(
                id = passage.id,
                tableId = id,
                durationMinutes = passage.durationMinutes,
                sortOrder = index,
            )
        },
    )
}

internal fun TableWithPassages.toDomain(): HallTable {
    val domainPassages = passages
        .sortedBy { it.sortOrder }
        .map { passage ->
            TablePassage(
                id = passage.id,
                durationMinutes = passage.durationMinutes,
            )
        }
    val timerState = table.toDomainTimer(domainPassages)
    return HallTable(
        id = table.id,
        name = table.name,
        shape = TableShape.entries.firstOrNull { it.name == table.shape } ?: TableShape.CIRCLE,
        position = CanvasPosition.of(table.canvasX, table.canvasY),
        passages = domainPassages,
        timerState = timerState,
    )
}

internal fun TableWithPassages.requiresTimerRepair(): Boolean = when (table.timerStatus) {
    TimerStatus.RUNNING.name -> {
        table.currentPassageId.isNullOrBlank() ||
            table.endsAtEpochMillis == null ||
            table.endsAtEpochMillis <= 0L ||
            passages.none { it.id == table.currentPassageId }
    }
    TimerStatus.IDLE.name,
    TimerStatus.COMPLETED.name,
    -> table.currentPassageId != null || table.endsAtEpochMillis != null
    else -> true
}

private fun HallTableEntity.toDomainTimer(passages: List<TablePassage>): TableTimerState =
    when (timerStatus) {
        TimerStatus.RUNNING.name -> {
            val passageId = currentPassageId
            val endTime = endsAtEpochMillis
            if (
                !passageId.isNullOrBlank() &&
                endTime != null &&
                endTime > 0L &&
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

private fun TableTimerState.toPersistedTimer(): PersistedTimer = when (this) {
    TableTimerState.Idle -> PersistedTimer(TimerStatus.IDLE.name, null, null)
    TableTimerState.Completed -> PersistedTimer(TimerStatus.COMPLETED.name, null, null)
    is TableTimerState.Running -> PersistedTimer(
        status = TimerStatus.RUNNING.name,
        passageId = passageId,
        endsAtEpochMillis = endsAtEpochMillis,
    )
}

private data class PersistedTimer(
    val status: String,
    val passageId: String?,
    val endsAtEpochMillis: Long?,
)

internal enum class TimerStatus {
    IDLE,
    RUNNING,
    COMPLETED,
}

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
