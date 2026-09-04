package ru.hznik.hookahtimer.hall.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.CanvasPosition
import ru.hznik.hookahtimer.hall.model.TablePassage
import ru.hznik.hookahtimer.hall.model.TableShape
import ru.hznik.hookahtimer.hall.model.TableTimerState
import ru.hznik.hookahtimer.hall.model.withAddedHookah
import ru.hznik.hookahtimer.hall.model.withAdvancedHookah
import ru.hznik.hookahtimer.hall.model.withResetHookahs
import java.util.UUID

class InMemoryHallRepository(
    initialTables: List<HallTable> = emptyList(),
    initialNextTableNumber: Int = 1,
) : HallRepository {
    private val mutex = Mutex()
    private val mutableTables = MutableStateFlow(initialTables.toList())
    private var nextTableNumber = initialNextTableNumber

    override val tables = mutableTables.asStateFlow()

    override suspend fun addTable(
        tableId: String,
        passageIds: List<String>,
        position: CanvasPosition?,
    ) {
        require(passageIds.size == TablePassage.DEFAULT_PASSAGE_COUNT)
        mutex.withLock {
            if (mutableTables.value.any { it.id == tableId }) return
            val number = nextTableNumber++
            mutableTables.value = mutableTables.value + HallTable(
                id = tableId,
                name = "Стол $number",
                position = position ?: initialTablePosition(number),
                passages = TablePassage.defaultList(passageIds.iterator()::next),
            )
        }
    }

    override suspend fun moveTable(tableId: String, position: CanvasPosition) {
        mutex.withLock {
            mutableTables.value = mutableTables.value.map { table ->
                if (table.id == tableId) table.copy(position = position) else table
            }
        }
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
        mutex.withLock {
            mutableTables.value = mutableTables.value.map { table ->
                if (table.id == tableId && table.isIdle) {
                    table.copy(
                        name = trimmedName,
                        shape = shape,
                        passages = passages.toList(),
                    )
                } else {
                    table
                }
            }
        }
    }

    override suspend fun deleteTable(tableId: String) {
        mutex.withLock {
            mutableTables.value = mutableTables.value.filterNot { it.id == tableId }
        }
    }

    override suspend fun advanceTimer(tableId: String, nowEpochMillis: Long) {
        changeTable(tableId) { table ->
            when {
                table.hasMultipleHookahs -> table
                table.isCompleted -> table.withResetHookahs(UUID.randomUUID().toString())
                else -> table.withAdvancedHookah(table.hookahs.single().id, nowEpochMillis)
            }
        }
    }

    override suspend fun addHookah(tableId: String, hookahId: String, nowEpochMillis: Long) {
        changeTable(tableId) { it.withAddedHookah(hookahId, nowEpochMillis) }
    }

    override suspend fun advanceHookah(
        tableId: String,
        hookahId: String,
        nowEpochMillis: Long,
        expectedState: TableTimerState?,
    ) {
        changeTable(tableId) { table ->
            val hookah = table.hookahs.find { it.id == hookahId }
            if (hookah == null || (expectedState != null && hookah.timerState != expectedState)) table
            else table.withAdvancedHookah(hookahId, nowEpochMillis)
        }
    }

    override suspend fun resetTable(tableId: String, initialHookahId: String) {
        changeTable(tableId) { it.withResetHookahs(initialHookahId) }
    }

    private suspend fun changeTable(tableId: String, transform: (HallTable) -> HallTable) {
        mutex.withLock {
            mutableTables.value = mutableTables.value.map { if (it.id == tableId) transform(it) else it }
        }
    }
}
