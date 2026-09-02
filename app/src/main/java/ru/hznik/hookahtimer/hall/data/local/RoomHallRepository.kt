package ru.hznik.hookahtimer.hall.data.local

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ru.hznik.hookahtimer.hall.data.HallRepository
import ru.hznik.hookahtimer.hall.data.initialTablePosition
import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.CanvasPosition
import ru.hznik.hookahtimer.hall.model.TablePassage
import ru.hznik.hookahtimer.hall.model.TableShape
import ru.hznik.hookahtimer.hall.model.TableTimerState
import ru.hznik.hookahtimer.hall.model.advanceTableTimer

class RoomHallRepository(
    private val database: HookahTimerDatabase,
) : HallRepository {
    private val dao = database.hallDao()

    override val tables: Flow<List<HallTable>> = dao.observeTables()
        .map { persistedTables ->
            val invalidTimerTableIds = persistedTables
                .filter { it.requiresTimerRepair() }
                .map { it.table.id }
            if (invalidTimerTableIds.isNotEmpty()) {
                database.withTransaction {
                    invalidTimerTableIds.forEach { tableId ->
                        dao.updateTimer(
                            tableId = tableId,
                            status = TimerStatus.IDLE.name,
                            passageId = null,
                            endsAtEpochMillis = null,
                        )
                    }
                }
            }
            persistedTables.map(TableWithPassages::toDomain)
        }
        .distinctUntilChanged()

    override suspend fun addTable(
        tableId: String,
        passageIds: List<String>,
        position: CanvasPosition?,
    ) {
        require(passageIds.size == TablePassage.DEFAULT_PASSAGE_COUNT)
        database.withTransaction {
            if (dao.getTable(tableId) != null) return@withTransaction
            val metadata = dao.getMetadata() ?: HallMetadataEntity(nextTableNumber = 1)
            val number = metadata.nextTableNumber
            val table = HallTable(
                id = tableId,
                name = "Стол $number",
                position = position ?: initialTablePosition(number),
                passages = TablePassage.defaultList(passageIds.iterator()::next),
            )
            val persisted = table.toPersisted(
                sortOrder = (dao.getMaxTableSortOrder() ?: -1) + 1,
            )
            dao.insertTable(persisted.table)
            dao.insertPassages(persisted.passages)
            dao.upsertMetadata(metadata.copy(nextTableNumber = number + 1))
        }
    }

    override suspend fun moveTable(tableId: String, position: CanvasPosition) {
        val table = dao.getTable(tableId)?.toDomain() ?: return
        dao.updatePosition(
            tableId = tableId,
            positionX = position.toLegacyNormalizedX(table.shape),
            positionY = position.toLegacyNormalizedY(table.shape),
            canvasX = position.x,
            canvasY = position.y,
        )
    }

    override suspend fun updateTableSettings(
        tableId: String,
        name: String,
        shape: TableShape,
        passages: List<TablePassage>,
    ) {
        val trimmedName = name.trim()
        if (
            trimmedName.isEmpty() ||
            passages.isEmpty() ||
            passages.distinctBy { it.id }.size != passages.size
        ) {
            return
        }
        database.withTransaction {
            val persistedCurrent = dao.getTable(tableId) ?: return@withTransaction
            val current = persistedCurrent.toDomain()
            if (current.timerState != TableTimerState.Idle) return@withTransaction
            val updated = current.copy(
                name = trimmedName,
                shape = shape,
                passages = passages.toList(),
            ).toPersisted(sortOrder = persistedCurrent.table.sortOrder)
            dao.deletePassages(tableId)
            dao.updateTable(updated.table)
            dao.insertPassages(updated.passages)
        }
    }

    override suspend fun deleteTable(tableId: String) {
        dao.deleteTable(tableId)
    }

    override suspend fun advanceTimer(tableId: String, nowEpochMillis: Long) {
        database.withTransaction {
            val persisted = dao.getTable(tableId) ?: return@withTransaction
            val table = persisted.toDomain()
            val nextState = advanceTableTimer(table, nowEpochMillis)
            val timer = table.copy(timerState = nextState)
                .toPersisted(persisted.table.sortOrder)
                .table
            dao.updateTimer(
                tableId = tableId,
                status = timer.timerStatus,
                passageId = timer.currentPassageId,
                endsAtEpochMillis = timer.endsAtEpochMillis,
            )
        }
    }
}
