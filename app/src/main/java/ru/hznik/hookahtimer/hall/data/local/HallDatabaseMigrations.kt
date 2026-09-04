package ru.hznik.hookahtimer.hall.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal const val LEGACY_CANVAS_WIDTH = 1_200f
internal const val LEGACY_CANVAS_HEIGHT = 800f
internal const val LEGACY_CIRCLE_SIZE = 112f
internal const val LEGACY_PILL_WIDTH = 184f
internal const val LEGACY_PILL_HEIGHT = 96f

/**
 * Back up passages before rebuilding their parent: dropping hall_tables with
 * foreign keys enabled would cascade. Room runs the migration in a transaction.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TEMP TABLE migration_tables AS SELECT * FROM hall_tables")
        db.execSQL("CREATE TEMP TABLE migration_passages AS SELECT * FROM table_passages")
        db.execSQL("DROP TABLE table_passages")
        db.execSQL("DROP TABLE hall_tables")
        db.execSQL(
            """
            CREATE TABLE hall_tables (
                table_id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL, shape TEXT NOT NULL,
                position_x REAL NOT NULL, position_y REAL NOT NULL,
                canvas_x REAL NOT NULL, canvas_y REAL NOT NULL,
                sort_order INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO hall_tables
            SELECT table_id, name, shape, position_x, position_y, canvas_x, canvas_y, sort_order
            FROM migration_tables
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE table_passages (
                passage_id TEXT NOT NULL PRIMARY KEY,
                table_id TEXT NOT NULL, duration_minutes INTEGER NOT NULL, sort_order INTEGER NOT NULL,
                FOREIGN KEY(table_id) REFERENCES hall_tables(table_id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX index_table_passages_table_id ON table_passages(table_id)")
        db.execSQL("CREATE UNIQUE INDEX index_table_passages_table_id_sort_order ON table_passages(table_id, sort_order)")
        db.execSQL("INSERT INTO table_passages SELECT passage_id, table_id, duration_minutes, sort_order FROM migration_passages")
        db.execSQL(
            """
            CREATE TABLE table_hookahs (
                hookah_id TEXT NOT NULL PRIMARY KEY,
                table_id TEXT NOT NULL, number INTEGER NOT NULL, timer_status TEXT NOT NULL,
                current_passage_id TEXT, ends_at_epoch_millis INTEGER,
                FOREIGN KEY(table_id) REFERENCES hall_tables(table_id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX index_table_hookahs_table_id ON table_hookahs(table_id)")
        db.execSQL("CREATE UNIQUE INDEX index_table_hookahs_table_id_number ON table_hookahs(table_id, number)")
        db.execSQL(
            """
            INSERT INTO table_hookahs
            SELECT 'initial:' || table_id, table_id, 1, timer_status, current_passage_id, ends_at_epoch_millis
            FROM migration_tables
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE migration_passages")
        db.execSQL("DROP TABLE migration_tables")
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE hall_tables ADD COLUMN canvas_x REAL NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE hall_tables ADD COLUMN canvas_y REAL NOT NULL DEFAULT 0",
        )
        db.execSQL(
            """
            UPDATE hall_tables
            SET canvas_x = position_x * CASE
                    WHEN shape = 'PILL' THEN ${LEGACY_CANVAS_WIDTH - LEGACY_PILL_WIDTH}
                    ELSE ${LEGACY_CANVAS_WIDTH - LEGACY_CIRCLE_SIZE}
                END,
                canvas_y = position_y * CASE
                    WHEN shape = 'PILL' THEN ${LEGACY_CANVAS_HEIGHT - LEGACY_PILL_HEIGHT}
                    ELSE ${LEGACY_CANVAS_HEIGHT - LEGACY_CIRCLE_SIZE}
                END
            """.trimIndent(),
        )
    }
}
