package ru.hznik.hookahtimer.hall.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import ru.hznik.hookahtimer.R
import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.NormalizedPosition
import ru.hznik.hookahtimer.hall.model.PixelPosition
import ru.hznik.hookahtimer.hall.model.PixelSize
import ru.hznik.hookahtimer.hall.model.TableShape
import ru.hznik.hookahtimer.hall.model.clampWithin
import ru.hznik.hookahtimer.hall.model.toNormalizedPosition
import ru.hznik.hookahtimer.hall.model.toPixelPosition

@Composable
internal fun HallTableItem(
    table: HallTable,
    fieldSize: IntSize,
    isEditMode: Boolean,
    onPositionChange: (String, NormalizedPosition) -> Unit,
    onOpenSettings: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = tableDimensions(table.shape)
    val density = LocalDensity.current
    val tablePixelSize = with(density) {
        PixelSize(
            width = dimensions.width.toPx(),
            height = dimensions.height.toPx(),
        )
    }
    val fieldPixelSize = PixelSize(
        width = fieldSize.width.toFloat(),
        height = fieldSize.height.toFloat(),
    )
    val pixelPosition = table.position.toPixelPosition(
        fieldSize = fieldPixelSize,
        tableSize = tablePixelSize,
    )
    val latestPosition by rememberUpdatedState(table.position)
    val latestOnPositionChange by rememberUpdatedState(onPositionChange)
    val shape = when (table.shape) {
        TableShape.CIRCLE -> CircleShape
        TableShape.PILL -> RoundedCornerShape(percent = 50)
    }
    val tableDescription = stringResource(
        if (table.shape == TableShape.CIRCLE) {
            R.string.circle_table_description
        } else {
            R.string.pill_table_description
        },
        table.name,
    )
    val deleteDescription = stringResource(R.string.delete_table, table.name)
    val editBorder = if (isEditMode) {
        Modifier.border(width = 3.dp, color = MaterialTheme.colorScheme.primary, shape = shape)
    } else {
        Modifier
    }
    val settingsModifier = if (isEditMode) {
        Modifier.clickable(
            role = Role.Button,
            onClick = onOpenSettings,
        )
    } else {
        Modifier
    }
    val dragModifier = if (isEditMode && fieldSize != IntSize.Zero) {
        Modifier.pointerInput(table.id, fieldSize, tablePixelSize) {
            var workingPosition = latestPosition.toPixelPosition(
                fieldSize = fieldPixelSize,
                tableSize = tablePixelSize,
            )
            detectDragGestures(
                onDragStart = {
                    workingPosition = latestPosition.toPixelPosition(
                        fieldSize = fieldPixelSize,
                        tableSize = tablePixelSize,
                    )
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    workingPosition = PixelPosition(
                        x = workingPosition.x + dragAmount.x,
                        y = workingPosition.y + dragAmount.y,
                    ).clampWithin(
                        fieldSize = fieldPixelSize,
                        tableSize = tablePixelSize,
                    )
                    latestOnPositionChange(
                        table.id,
                        workingPosition.toNormalizedPosition(
                            fieldSize = fieldPixelSize,
                            tableSize = tablePixelSize,
                        ),
                    )
                },
            )
        }
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .offsetInPixels(pixelPosition)
            .size(width = dimensions.width, height = dimensions.height)
            .then(editBorder)
            .then(settingsModifier)
            .then(dragModifier)
            .testTag(HallTestTags.table(table.id))
            .semantics { contentDescription = tableDescription },
        shape = shape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = table.name,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (isEditMode) {
                FilledIconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(40.dp)
                        .testTag(HallTestTags.deleteTable(table.id))
                        .semantics { contentDescription = deleteDescription },
                ) {
                    Text(
                        text = "×",
                        fontSize = 24.sp,
                        lineHeight = 24.sp,
                    )
                }
            }
        }
    }
}

private fun Modifier.offsetInPixels(position: PixelPosition): Modifier = offset {
    IntOffset(
        x = position.x.roundToInt(),
        y = position.y.roundToInt(),
    )
}

private data class TableDimensions(
    val width: Dp,
    val height: Dp,
)

private fun tableDimensions(shape: TableShape): TableDimensions = when (shape) {
    TableShape.CIRCLE -> TableDimensions(width = 112.dp, height = 112.dp)
    TableShape.PILL -> TableDimensions(width = 184.dp, height = 96.dp)
}
