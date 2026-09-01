package ru.hznik.hookahtimer.hall.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        HallTableEntity::class,
        TablePassageEntity::class,
        HallMetadataEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class HookahTimerDatabase : RoomDatabase() {
    abstract fun hallDao(): HallDao

    companion object {
        const val DATABASE_NAME = "hookah-timer.db"

        fun create(context: Context): HookahTimerDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                HookahTimerDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
