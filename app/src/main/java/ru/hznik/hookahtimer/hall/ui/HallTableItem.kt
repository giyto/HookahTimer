package ru.hznik.hookahtimer.hall.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.hznik.hookahtimer.R
import ru.hznik.hookahtimer.hall.model.CanvasPosition
import ru.hznik.hookahtimer.hall.model.CanvasTransform
import ru.hznik.hookahtimer.hall.model.CanvasViewport
import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.TableShape
import ru.hznik.hookahtimer.hall.model.timerPresentation
import ru.hznik.hookahtimer.hall.model.toScreenPosition
import ru.hznik.hookahtimer.ui.icons.AppIcons

@Composable
internal fun HallTableItem(
    table: HallTable,
    viewport: CanvasViewport,
    isEditMode: Boolean,
    nowEpochMillis: Long,
    onPositionChange: (String, CanvasPosition) -> Unit,
    onOpenSettings: () -> Unit,
    onAdvanceTimer: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onAddHookah: () -> Unit = {},
    restoreFocus: Boolean = false,
) {
    var isPressed by remember(table.id) { mutableStateOf(false) }
    val pressScale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "Table press")
    val focusRequester = remember(table.id) { FocusRequester() }
    LaunchedEffect(restoreFocus) {
        if (restoreFocus && !isEditMode) focusRequester.requestFocus()
    }
    val dimensions = tableCanvasDimensions(table.shape)
    val density = LocalDensity.current
    val overflow = DELETE_HANDLE_OVERFLOW
    var draggedPosition by remember(table.id, table.position) {
        mutableStateOf<CanvasPosition?>(null)
    }
    val displayedPosition = draggedPosition ?: table.position
    val latestPosition by rememberUpdatedState(table.position)
    val latestOnPositionChange by rememberUpdatedState(onPositionChange)
    val transform = CanvasTransform(viewport.scale, viewport.offset)
    val screenPosition = displayedPosition.toScreenPosition(transform)
    val shape = when (table.shape) {
        TableShape.CIRCLE -> CircleShape
        TableShape.PILL -> RoundedCornerShape(percent = 50)
    }
    val timer = table.timerPresentation(nowEpochMillis)
    val shapeDescription = stringResource(
        if (table.shape == TableShape.CIRCLE) {
            R.string.circle_table_description
        } else {
            R.string.pill_table_description
        },
        table.name,
    )
    val timerSubject = if (table.hasMultipleHookahs && table.mostUrgentHookah != null) {
        stringResource(R.string.hookah_number, checkNotNull(table.mostUrgentHookah).number)
    } else {
        shapeDescription
    }
    val singleDescription = when {
        timer.isCompleted -> stringResource(R.string.completed_table_description, timerSubject)
        timer.isOverdue -> stringResource(
            R.string.overdue_table_description,
            timerSubject,
            checkNotNull(timer.passageNumber),
            table.passages.size,
            checkNotNull(timer.timerText),
        )
        timer.isEndingSoon -> stringResource(
            R.string.ending_soon_table_description,
            timerSubject,
            checkNotNull(timer.passageNumber),
            table.passages.size,
            checkNotNull(timer.timerText),
        )
        timer.timerText != null -> stringResource(
            R.string.running_table_description,
            timerSubject,
            checkNotNull(timer.passageNumber),
            table.passages.size,
            timer.timerText,
        )
        table.hasMultipleHookahs -> stringResource(R.string.hookah_idle)
        else -> shapeDescription
    }
    val tableDescription = if (table.hasMultipleHookahs) {
        stringResource(
            R.string.multiple_hookahs_description,
            table.name,
            table.hookahs.size,
            singleDescription,
        )
    } else {
        singleDescription
    }
    val deleteDescription = stringResource(R.string.delete_table, table.name)
    val clickModifier = when {
        !isEditMode -> Modifier.focusRequester(focusRequester).hookahTableGestures(
            tableId = table.id,
            tapLabel = stringResource(
                when {
                    table.isCompleted -> R.string.reset_table
                    table.hasMultipleHookahs -> R.string.open_hookahs
                    table.isIdle -> R.string.start_hookah
                    else -> R.string.advance_hookah
                },
            ),
            addLabel = stringResource(R.string.add_hookah),
            onTap = onAdvanceTimer,
            onHold = onAddHookah,
            onPressed = { isPressed = it },
        )
        table.isIdle -> Modifier.clickable(
            role = Role.Button,
            onClick = onOpenSettings,
        )
        else -> Modifier
    }
    val dragModifier = if (isEditMode) {
        Modifier.pointerInput(table.id, table.position, viewport.scale) {
            var workingPosition = latestPosition
            detectDragGestures(
                onDragStart = {
                    workingPosition = latestPosition
                    draggedPosition = workingPosition
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    workingPosition = CanvasPosition.of(
                        x = workingPosition.x + dragAmount.x / density.density / viewport.scale,
                        y = workingPosition.y + dragAmount.y / density.density / viewport.scale,
                    )
                    draggedPosition = workingPosition
                },
                onDragEnd = { latestOnPositionChange(table.id, workingPosition) },
                onDragCancel = { draggedPosition = null },
            )
        }
    } else {
        Modifier
    }
    val containerColor = if (timer.isOverdue) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (timer.isOverdue) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val timerVisualState = when {
        timer.isOverdue -> TableTimerVisualState.OVERDUE
        timer.isEndingSoon -> TableTimerVisualState.ENDING_SOON
        else -> TableTimerVisualState.NORMAL
    }

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    x = with(density) { screenPosition.x.dp.roundToPx() },
                    y = with(density) {
                        (screenPosition.y - overflow.value * viewport.scale).dp.roundToPx()
                    },
                )
            }
            .graphicsLayer(
                scaleX = viewport.scale,
                scaleY = viewport.scale,
                transformOrigin = TransformOrigin(0f, 0f),
            )
            .size(
                width = dimensions.width + overflow,
                height = dimensions.height + overflow,
            ),
    ) {
        Surface(
            modifier = Modifier
                .offset(y = overflow)
                .size(width = dimensions.width, height = dimensions.height)
                .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                .then(
                    if (timer.isEndingSoon) {
                        Modifier.border(3.dp, Color.Red, shape)
                    } else if (isEditMode) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
                    } else {
                        Modifier
                    },
                )
                .then(clickModifier)
                .then(dragModifier)
                .testTag(HallTestTags.table(table.id))
                .semantics {
                    contentDescription = tableDescription
                    tableTimerVisualState = timerVisualState
                },
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = 4.dp,
            shadowElevation = 2.dp,
        ) {
            Box(modifier = Modifier.size(dimensions.width, dimensions.height)) {
                when {
                    table.hasMultipleHookahs -> MultipleHookahsTableContent(table, nowEpochMillis)
                    timer.isCompleted -> CompletedTableContent(table)
                    timer.timerText != null -> RunningTableContent(
                        table = table,
                        timerText = timer.timerText,
                        passageNumber = checkNotNull(timer.passageNumber),
                        passageCount = table.passages.size,
                        isEndingSoon = timer.isEndingSoon,
                    )
                    else -> IdleTableContent(table)
                }
            }
        }

        if (isEditMode) {
            FilledIconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(40.dp)
                    .testTag(HallTestTags.deleteTable(table.id))
                    .semantics { contentDescription = deleteDescription },
            ) {
                Icon(imageVector = AppIcons.Close, contentDescription = null)
            }
        }
    }
}

@Composable
private fun BoxScope.MultipleHookahsTableContent(table: HallTable, nowEpochMillis: Long) {
    val timer = table.timerPresentation(nowEpochMillis)
    Text(
        text = table.name,
        modifier = Modifier.align(Alignment.TopCenter)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(HallTestTags.tableName(table.id)),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = if (timer.isCompleted) "×" else timer.timerText ?: "—",
        modifier = Modifier.align(Alignment.Center).testTag(
            if (timer.isCompleted) HallTestTags.completedMark(table.id) else HallTestTags.tableTimer(table.id),
        ),
        fontSize = if (timer.isCompleted) 42.sp else 24.sp,
        fontWeight = FontWeight.Bold,
        color = if (timer.isCompleted || timer.isEndingSoon) Color.Red else Color.Unspecified,
        maxLines = 1,
    )
    Row(
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(AppIcons.Hookah, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(
            text = table.hookahs.size.toString(),
            modifier = Modifier.testTag(HookahTestTags.tableCount(table.id)),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BoxScope.IdleTableContent(table: HallTable) {
    Text(
        text = table.name,
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = 16.dp)
            .testTag(HallTestTags.tableName(table.id)),
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun BoxScope.RunningTableContent(
    table: HallTable,
    timerText: String,
    passageNumber: Int,
    passageCount: Int,
    isEndingSoon: Boolean,
) {
    Text(
        text = stringResource(R.string.table_passage_indicator, passageNumber, passageCount),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(HallTestTags.tablePassage(table.id)),
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        maxLines = 1,
    )
    Text(
        text = timerText,
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = 8.dp)
            .testTag(HallTestTags.tableTimer(table.id)),
        color = if (isEndingSoon) Color.Red else Color.Unspecified,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        maxLines = 1,
    )
    Text(
        text = table.name,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(HallTestTags.tableName(table.id)),
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun BoxScope.CompletedTableContent(table: HallTable) {
    Text(
        text = "×",
        modifier = Modifier
            .align(Alignment.Center)
            .testTag(HallTestTags.completedMark(table.id)),
        color = Color.Red,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        lineHeight = 42.sp,
    )
    Text(
        text = table.name,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(HallTestTags.tableName(table.id)),
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

internal data class TableCanvasDimensions(val width: Dp, val height: Dp)

internal fun tableCanvasDimensions(shape: TableShape): TableCanvasDimensions = when (shape) {
    TableShape.CIRCLE -> TableCanvasDimensions(width = 112.dp, height = 112.dp)
    TableShape.PILL -> TableCanvasDimensions(width = 184.dp, height = 96.dp)
}

internal val DELETE_HANDLE_OVERFLOW = 20.dp

internal enum class TableTimerVisualState {
    NORMAL,
    ENDING_SOON,
    OVERDUE,
}

internal val TableTimerVisualStateKey =
    SemanticsPropertyKey<TableTimerVisualState>("TableTimerVisualState")
internal var SemanticsPropertyReceiver.tableTimerVisualState by TableTimerVisualStateKey
