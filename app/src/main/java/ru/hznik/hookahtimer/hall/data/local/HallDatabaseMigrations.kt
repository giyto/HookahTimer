package ru.hznik.hookahtimer.hall.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal const val LEGACY_CANVAS_WIDTH = 1_200f
internal const val LEGACY_CANVAS_HEIGHT = 800f
internal const val LEGACY_CIRCLE_SIZE = 112f
internal const val LEGACY_PILL_WIDTH = 184f
internal const val LEGACY_PILL_HEIGHT = 96f

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
