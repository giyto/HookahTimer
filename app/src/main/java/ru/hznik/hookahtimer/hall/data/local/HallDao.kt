package ru.hznik.hookahtimer.hall.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HallDao {
    @Transaction
    @Query("SELECT * FROM hall_tables ORDER BY sort_order")
    fun observeTables(): Flow<List<TableWithPassages>>

    @Transaction
    @Query("SELECT * FROM hall_tables WHERE table_id = :tableId")
    suspend fun getTable(tableId: String): TableWithPassages?

    @Transaction
    @Query("SELECT * FROM hall_tables ORDER BY sort_order")
    suspend fun getTables(): List<TableWithPassages>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHookahs(hookahs: List<TableHookahEntity>)

    @Update
    suspend fun updateHookah(hookah: TableHookahEntity)

    @Query("DELETE FROM table_hookahs WHERE table_id = :tableId")
    suspend fun deleteHookahs(tableId: String)

    @Query("SELECT COUNT(*) FROM table_hookahs WHERE table_id = :tableId")
    suspend fun countHookahs(tableId: String): Int

    @Query("SELECT MAX(sort_order) FROM hall_tables")
    suspend fun getMaxTableSortOrder(): Int?

    @Query("SELECT * FROM hall_metadata WHERE id = :id")
    suspend fun getMetadata(id: String = HallMetadataEntity.SINGLETON_ID): HallMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTable(table: HallTableEntity)

    @Update
    suspend fun updateTable(table: HallTableEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPassages(passages: List<TablePassageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(metadata: HallMetadataEntity)

    @Query("DELETE FROM table_passages WHERE table_id = :tableId")
    suspend fun deletePassages(tableId: String)

    @Query("DELETE FROM hall_tables WHERE table_id = :tableId")
    suspend fun deleteTable(tableId: String)

    @Query(
        """
        UPDATE hall_tables
        SET position_x = :positionX,
            position_y = :positionY,
            canvas_x = :canvasX,
            canvas_y = :canvasY
        WHERE table_id = :tableId
        """,
    )
    suspend fun updatePosition(
        tableId: String,
        positionX: Float,
        positionY: Float,
        canvasX: Float,
        canvasY: Float,
    )

    @Query(
        """
        UPDATE table_hookahs
        SET timer_status = :status,
            current_passage_id = :passageId,
            ends_at_epoch_millis = :endsAtEpochMillis
        WHERE table_id = :tableId AND hookah_id = :hookahId
        """,
    )
    suspend fun updateTimer(
        tableId: String,
        hookahId: String,
        status: String,
        passageId: String?,
        endsAtEpochMillis: Long?,
    )

    @Query("SELECT COUNT(*) FROM table_passages WHERE table_id = :tableId")
    suspend fun countPassages(tableId: String): Int
}
