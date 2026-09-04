package ru.hznik.hookahtimer.hall.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HallDatabaseMigrationTest {
    @Test
    fun emptyVersion2DatabaseMigratesTo3() {
        val name = "hall-empty-v3"
        helper.createDatabase(name, 2).close()
        helper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3).use { db ->
            db.query("SELECT COUNT(*) FROM table_hookahs").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        }
    }

    @Test
    fun version2MigrationMovesEveryTimerWithoutRecalculatingDeadlines() {
        val name = "hall-populated-v3"
        helper.createDatabase(name, 2).use { db ->
            listOf("IDLE", "RUNNING", "COMPLETED", "RUNNING").forEachIndexed { index, status ->
                val passage = if (status == "RUNNING") "'p$index-b'" else "NULL"
                val deadline = if (status == "RUNNING") (123_456L + index).toString() else "NULL"
                db.execSQL(
                    "INSERT INTO hall_tables " +
                        "(table_id, name, shape, position_x, position_y, canvas_x, canvas_y, sort_order, " +
                        "timer_status, current_passage_id, ends_at_epoch_millis) " +
                        "VALUES ('t$index', 'VIP$index', 'PILL', 0.2, 0.3, 450.5, -30.25, $index, '$status', $passage, $deadline)",
                )
                db.execSQL("INSERT INTO table_passages VALUES ('p$index-b', 't$index', 45, 1)")
                db.execSQL("INSERT INTO table_passages VALUES ('p$index-a', 't$index', 15, 0)")
            }
            db.execSQL("INSERT INTO hall_metadata VALUES ('hall', 8)")
        }
        helper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3).use { db ->
            db.query("SELECT hookah_id, number, timer_status, current_passage_id, ends_at_epoch_millis FROM table_hookahs ORDER BY table_id")
                .use { cursor ->
                    assertEquals(4, cursor.count)
                    cursor.moveToFirst()
                    assertEquals("initial:t0", cursor.getString(0))
                    assertEquals(1, cursor.getInt(1))
                    assertEquals("IDLE", cursor.getString(2))
                    cursor.moveToNext()
                    assertEquals("RUNNING", cursor.getString(2))
                    assertEquals("p1-b", cursor.getString(3))
                    assertEquals(123_457L, cursor.getLong(4))
                    cursor.moveToNext()
                    assertEquals("COMPLETED", cursor.getString(2))
                    cursor.moveToNext()
                    assertEquals(123_459L, cursor.getLong(4))
                }
            db.query("SELECT name, shape, canvas_x, canvas_y, sort_order FROM hall_tables ORDER BY sort_order").use {
                it.moveToFirst()
                assertEquals("VIP0", it.getString(0))
                assertEquals("PILL", it.getString(1))
                assertEquals(450.5f, it.getFloat(2), DELTA)
                assertEquals(-30.25f, it.getFloat(3), DELTA)
                assertEquals(0, it.getInt(4))
            }
            db.query("SELECT passage_id, duration_minutes FROM table_passages WHERE table_id = 't1' ORDER BY sort_order").use {
                it.moveToFirst()
                assertEquals("p1-a", it.getString(0))
                assertEquals(15, it.getInt(1))
                it.moveToNext()
                assertEquals("p1-b", it.getString(0))
                assertEquals(45, it.getInt(1))
            }
            db.query("SELECT next_table_number FROM hall_metadata").use {
                it.moveToFirst()
                assertEquals(8, it.getInt(0))
            }
        }
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val room = androidx.room.Room.databaseBuilder(context, HookahTimerDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        try {
            kotlinx.coroutines.runBlocking {
                val repository = RoomHallRepository(room)
                repository.addHookah("t1", "new-order", 200_000L)
                val table = room.hallDao().getTable("t1")!!.toDomain()
                assertEquals(listOf(1, 2), table.hookahs.map { it.number })
                assertEquals(123_457L, (table.hookahs[0].timerState as ru.hznik.hookahtimer.hall.model.TableTimerState.Running).endsAtEpochMillis)
            }
        } finally { room.close() }
    }

    @Test
    fun version1MigratesThroughBothStepsWithoutLosingPositionOrTimer() {
        val name = "hall-chain-v3"
        helper.createDatabase(name, 1).use { db ->
            db.execSQL("INSERT INTO hall_tables VALUES ('t', 'VIP', 'CIRCLE', 0.25, 0.75, 0, 'RUNNING', 'p1', 123456)")
            db.execSQL("INSERT INTO table_passages VALUES ('p1', 't', 30, 0)")
        }
        helper.runMigrationsAndValidate(name, 3, true, MIGRATION_1_2, MIGRATION_2_3).use { db ->
            db.query("SELECT canvas_x, canvas_y FROM hall_tables").use {
                it.moveToFirst()
                assertEquals(272f, it.getFloat(0), DELTA)
                assertEquals(516f, it.getFloat(1), DELTA)
            }
            db.query("SELECT current_passage_id, ends_at_epoch_millis FROM table_hookahs").use {
                it.moveToFirst()
                assertEquals("p1", it.getString(0))
                assertEquals(123_456L, it.getLong(1))
            }
        }
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HookahTimerDatabase::class.java,
    )

    @Test
    fun migration1To2PreservesTablesPassagesAndTimersAndCreatesCanvasCoordinates() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO hall_tables(
                    table_id, name, shape, position_x, position_y, sort_order,
                    timer_status, current_passage_id, ends_at_epoch_millis
                ) VALUES ('circle', 'Круг', 'CIRCLE', 0.25, 0.75, 0, 'RUNNING', 'circle-p1', 123456)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO hall_tables(
                    table_id, name, shape, position_x, position_y, sort_order,
                    timer_status, current_passage_id, ends_at_epoch_millis
                ) VALUES ('pill', 'Пилюля', 'PILL', 0.5, 0.25, 1, 'IDLE', NULL, NULL)
                """.trimIndent(),
            )
            execSQL(
                "INSERT INTO table_passages(passage_id, table_id, duration_minutes, sort_order) " +
                    "VALUES ('circle-p1', 'circle', 30, 0)",
            )
            execSQL(
                "INSERT INTO table_passages(passage_id, table_id, duration_minutes, sort_order) " +
                    "VALUES ('pill-p1', 'pill', 45, 0)",
            )
            execSQL(
                "INSERT INTO hall_metadata(id, next_table_number) VALUES ('hall', 3)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            MIGRATION_1_2,
        )

        migrated.query(
            "SELECT table_id, canvas_x, canvas_y, timer_status, current_passage_id, " +
                "ends_at_epoch_millis FROM hall_tables ORDER BY sort_order",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("circle", cursor.getString(0))
            assertEquals(272f, cursor.getFloat(1), DELTA)
            assertEquals(516f, cursor.getFloat(2), DELTA)
            assertEquals("RUNNING", cursor.getString(3))
            assertEquals("circle-p1", cursor.getString(4))
            assertEquals(123_456L, cursor.getLong(5))

            cursor.moveToNext()
            assertEquals("pill", cursor.getString(0))
            assertEquals(508f, cursor.getFloat(1), DELTA)
            assertEquals(176f, cursor.getFloat(2), DELTA)
            assertEquals("IDLE", cursor.getString(3))
        }
        migrated.query("SELECT COUNT(*) FROM table_passages").use { cursor ->
            cursor.moveToFirst()
            assertEquals(2, cursor.getInt(0))
        }
        migrated.query("SELECT next_table_number FROM hall_metadata WHERE id = 'hall'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(3, cursor.getInt(0))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE = "hall-migration-test"
        const val DELTA = 0.001f
    }
}
