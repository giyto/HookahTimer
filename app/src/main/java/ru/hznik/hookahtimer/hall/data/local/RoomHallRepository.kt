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
import ru.hznik.hookahtimer.hall.model.TableHookah
import ru.hznik.hookahtimer.hall.model.withAddedHookah
import ru.hznik.hookahtimer.hall.model.withAdvancedHookah
import ru.hznik.hookahtimer.hall.model.withResetHookahs
import java.util.UUID

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
                        // Re-read under the transaction: an action may have already
                        // replaced the invalid snapshot emitted by the flow.
                        val current = dao.getTable(tableId) ?: return@forEach
                        if (current.hookahs.isEmpty()) {
                            dao.insertHookahs(listOf(TableHookah.initial(tableId).toPersisted(tableId)))
                        } else {
                            val passageIds = current.passages.map { it.id }
                            current.hookahs.filter { it.requiresTimerRepair(passageIds) }.forEach {
                                dao.updateHookah(
                                    it.copy(
                                        timerStatus = TimerStatus.IDLE.name,
                                        currentPassageId = null,
                                        endsAtEpochMillis = null,
                                    ),
                                )
                            }
                        }
                    }
                    dao.getTables().map(TableWithPassages::toDomain)
                }
            } else {
                persistedTables.map(TableWithPassages::toDomain)
            }
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
            dao.insertHookahs(persisted.hookahs)
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
            if (!current.isIdle) return@withTransaction
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
        changeHookahs(tableId) { table ->
            when {
                table.hasMultipleHookahs -> table
                table.isCompleted -> table.withResetHookahs(UUID.randomUUID().toString())
                else -> table.withAdvancedHookah(table.hookahs.single().id, nowEpochMillis)
            }
        }
    }

    override suspend fun addHookah(tableId: String, hookahId: String, nowEpochMillis: Long) {
        changeHookahs(tableId) { it.withAddedHookah(hookahId, nowEpochMillis) }
    }

    override suspend fun advanceHookah(
        tableId: String,
        hookahId: String,
        nowEpochMillis: Long,
        expectedState: TableTimerState?,
    ) {
        changeHookahs(tableId) { table ->
            val hookah = table.hookahs.find { it.id == hookahId }
            if (hookah == null || (expectedState != null && hookah.timerState != expectedState)) {
                table
            } else {
                table.withAdvancedHookah(hookahId, nowEpochMillis)
            }
        }
    }

    override suspend fun resetTable(tableId: String, initialHookahId: String) {
        changeHookahs(tableId) { it.withResetHookahs(initialHookahId) }
    }

    private suspend fun changeHookahs(tableId: String, transform: (HallTable) -> HallTable) {
        database.withTransaction {
            val persisted = dao.getTable(tableId) ?: return@withTransaction
            val current = persisted.toDomain()
            val updated = transform(current)
            if (updated == current) return@withTransaction
            val rows = updated.hookahs.map { it.toPersisted(tableId) }
            if (current.hookahs.any { old -> updated.hookahs.none { it.id == old.id } }) {
                // Reset, or activation replacing an unused idle placeholder.
                dao.deleteHookahs(tableId)
                dao.insertHookahs(rows)
            } else {
                val existing = persisted.hookahs.associateBy { it.id }
                rows.forEach { row ->
                    val old = existing[row.id]
                    if (old == null) dao.insertHookahs(listOf(row))
                    else if (old != row) dao.updateHookah(row)
                }
            }
        }
    }
}
