package ru.hznik.hookahtimer.hall.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.TablePassage

class HallDaoTest {
    @Test
    fun duplicateNumberAndOrphanHookahAreRejected() = runTest {
        val table = HallTable("t", "VIP").toPersisted(0)
        dao.insertTable(table.table)
        dao.insertPassages(table.passages)
        dao.insertHookahs(table.hookahs)
        var duplicateRejected = false
        try {
            dao.insertHookahs(listOf(table.hookahs.single().copy(id = "duplicate")))
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            duplicateRejected = true
        }
        assertTrue(duplicateRejected)
        assertEquals(1, dao.countHookahs("t"))
        var orphanRejected = false
        try {
            dao.insertHookahs(listOf(table.hookahs.single().copy(id = "orphan", tableId = "missing")))
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            orphanRejected = true
        }
        assertTrue(orphanRejected)
        assertEquals(0, dao.countHookahs("missing"))
    }

    private lateinit var database: HookahTimerDatabase
    private lateinit var dao: HallDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            HookahTimerDatabase::class.java,
        ).build()
        dao = database.hallDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun emptyDatabaseEmitsEmptyHall() = runTest {
        assertTrue(dao.observeTables().first().isEmpty())
    }

    @Test
    fun relationsPreserveTableAndPassageOrderWithoutDuplicates() = runTest {
        val second = HallTable(
            id = "second-table",
            name = "Второй",
            passages = listOf(
                TablePassage("second-b", 45),
                TablePassage("second-a", 15),
            ),
        ).toPersisted(sortOrder = 1)
        val first = HallTable(
            id = "first-table",
            name = "Первый",
            passages = listOf(TablePassage("first-a", 30)),
        ).toPersisted(sortOrder = 0)
        dao.insertTable(second.table)
        dao.insertPassages(second.passages.reversed())
        dao.insertHookahs(second.hookahs)
        dao.insertTable(first.table)
        dao.insertPassages(first.passages)
        dao.insertHookahs(first.hookahs)

        val rows = dao.observeTables().first { it.size == 2 }

        assertEquals(listOf("first-table", "second-table"), rows.map { it.table.id })
        assertEquals(
            listOf("second-b", "second-a"),
            rows.single { it.table.id == "second-table" }.toDomain().passages.map { it.id },
        )
    }

    @Test
    fun deletingTableCascadesToPassages() = runTest {
        val persisted = HallTable(
            id = "table",
            name = "Стол",
            passages = listOf(TablePassage("passage", 30)),
        ).toPersisted(sortOrder = 0)
        dao.insertTable(persisted.table)
        dao.insertPassages(persisted.passages)
        dao.insertHookahs(persisted.hookahs)

        dao.deleteTable("table")

        assertEquals(0, dao.countPassages("table"))
        assertEquals(0, dao.countHookahs("table"))
        assertTrue(dao.observeTables().first().isEmpty())
    }
}
