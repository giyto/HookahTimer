package ru.hznik.hookahtimer.hall.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "hall_tables")
data class HallTableEntity(
    @PrimaryKey
    @ColumnInfo(name = "table_id")
    val id: String,
    val name: String,
    val shape: String,
    @ColumnInfo(name = "position_x")
    val positionX: Float,
    @ColumnInfo(name = "position_y")
    val positionY: Float,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "timer_status")
    val timerStatus: String,
    @ColumnInfo(name = "current_passage_id")
    val currentPassageId: String?,
    @ColumnInfo(name = "ends_at_epoch_millis")
    val endsAtEpochMillis: Long?,
)

@Entity(
    tableName = "table_passages",
    foreignKeys = [
        ForeignKey(
            entity = HallTableEntity::class,
            parentColumns = ["table_id"],
            childColumns = ["table_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("table_id"),
        Index(value = ["table_id", "sort_order"], unique = true),
    ],
)
data class TablePassageEntity(
    @PrimaryKey
    @ColumnInfo(name = "passage_id")
    val id: String,
    @ColumnInfo(name = "table_id")
    val tableId: String,
    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
)

@Entity(tableName = "hall_metadata")
data class HallMetadataEntity(
    @PrimaryKey
    val id: String = SINGLETON_ID,
    @ColumnInfo(name = "next_table_number")
    val nextTableNumber: Int,
) {
    companion object {
        const val SINGLETON_ID = "hall"
    }
}

data class TableWithPassages(
    @Embedded
    val table: HallTableEntity,
    @Relation(
        parentColumn = "table_id",
        entityColumn = "table_id",
    )
    val passages: List<TablePassageEntity>,
)
