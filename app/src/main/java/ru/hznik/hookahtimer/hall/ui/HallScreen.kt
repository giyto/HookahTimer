package ru.hznik.hookahtimer.hall.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.floor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import ru.hznik.hookahtimer.R
import ru.hznik.hookahtimer.hall.model.CanvasItemBounds
import ru.hznik.hookahtimer.hall.model.CanvasPosition
import ru.hznik.hookahtimer.hall.model.CanvasRect
import ru.hznik.hookahtimer.hall.model.CanvasSize
import ru.hznik.hookahtimer.hall.model.CanvasTransform
import ru.hznik.hookahtimer.hall.model.CanvasViewport
import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.ScreenPosition
import ru.hznik.hookahtimer.hall.model.SystemTimeProvider
import ru.hznik.hookahtimer.hall.model.TableShape
import ru.hznik.hookahtimer.hall.model.TimeProvider
import ru.hznik.hookahtimer.hall.model.calculateCanvasContentBounds
import ru.hznik.hookahtimer.hall.model.gridDetailForScale
import ru.hznik.hookahtimer.hall.model.toCanvasPosition
import ru.hznik.hookahtimer.hall.presentation.HallAction
import ru.hznik.hookahtimer.hall.presentation.HallUiState
import ru.hznik.hookahtimer.ui.theme.HookahTimerTheme
import ru.hznik.hookahtimer.ui.icons.AppIcons

@Composable
fun HallScreen(
    state: HallUiState,
    onAction: (HallAction) -> Unit,
    onOpenSettings: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    timeProvider: TimeProvider = SystemTimeProvider,
) {
    val pendingDeleteTable = state.tables.firstOrNull { it.id == state.pendingDeleteTableId }
    val nowEpochMillis = rememberCurrentEpochMillis(timeProvider)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            HallFloatingActions(
                state = state,
                onAction = onAction,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        HallField(
            state = state,
            onAction = onAction,
            onOpenSettings = onOpenSettings,
            nowEpochMillis = nowEpochMillis,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(12.dp),
        )
    }

    if (pendingDeleteTable != null) {
        AlertDialog(
            onDismissRequest = { onAction(HallAction.CancelDelete) },
            title = { Text(stringResource(R.string.delete_table_title)) },
            text = { Text(stringResource(R.string.delete_table_message, pendingDeleteTable.name)) },
            confirmButton = {
                Button(
                    onClick = { onAction(HallAction.ConfirmDelete) },
                    modifier = Modifier.testTag(HallTestTags.CONFIRM_DELETE),
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onAction(HallAction.CancelDelete) },
                    modifier = Modifier.testTag(HallTestTags.CANCEL_DELETE),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun HallFloatingActions(
    state: HallUiState,
    onAction: (HallAction) -> Unit,
) {
    val addDescription = stringResource(R.string.add_table)
    val editDescription = stringResource(R.string.edit_hall)
    val finishDescription = stringResource(R.string.finish_editing)
    val fullscreenDescription = stringResource(
        if (state.isFullscreenEnabled) R.string.exit_fullscreen else R.string.enter_fullscreen,
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.End,
    ) {
        if (state.isEditMode) {
            FloatingActionButton(
                onClick = { onAction(HallAction.AddTable) },
                modifier = Modifier
                    .testTag(HallTestTags.ADD_TABLE)
                    .semantics { contentDescription = addDescription },
            ) {
                Icon(AppIcons.Add, contentDescription = null)
            }
            FloatingActionButton(
                onClick = { onAction(HallAction.ToggleEditMode) },
                modifier = Modifier
                    .testTag(HallTestTags.TOGGLE_EDIT_MODE)
                    .semantics { contentDescription = finishDescription },
            ) {
                Icon(AppIcons.Check, contentDescription = null)
            }
        } else {
            FloatingActionButton(
                onClick = { onAction(HallAction.ToggleFullscreen) },
                modifier = Modifier
                    .testTag(HallTestTags.TOGGLE_FULLSCREEN)
                    .semantics { contentDescription = fullscreenDescription },
            ) {
                Icon(
                    imageVector = if (state.isFullscreenEnabled) {
                        AppIcons.FullscreenExit
                    } else {
                        AppIcons.Fullscreen
                    },
                    contentDescription = null,
                )
            }
            FloatingActionButton(
                onClick = { onAction(HallAction.ToggleEditMode) },
                modifier = Modifier
                    .testTag(HallTestTags.TOGGLE_EDIT_MODE)
                    .semantics { contentDescription = editDescription },
            ) {
                Icon(AppIcons.Edit, contentDescription = null)
            }
        }
    }
}

@Composable
private fun HallField(
    state: HallUiState,
    onAction: (HallAction) -> Unit,
    onOpenSettings: (String) -> Unit,
    nowEpochMillis: Long,
    modifier: Modifier = Modifier,
) {
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    var viewportScale by rememberSaveable { mutableFloatStateOf(1f) }
    var viewportOffsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var viewportOffsetY by rememberSaveable { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val viewportSize = with(density) {
        CanvasSize(
            width = fieldSize.width.toDp().value,
            height = fieldSize.height.toDp().value,
        )
    }
    val contentBounds = remember(state.tables, viewportSize) {
        calculateCanvasContentBounds(
            items = state.tables.map { table ->
                val dimensions = tableCanvasDimensions(table.shape)
                CanvasItemBounds(
                    position = table.position,
                    width = dimensions.width.value,
                    height = dimensions.height.value,
                )
            },
            viewportSize = viewportSize,
        )
    }
    val requestedViewport = CanvasViewport(
        scale = viewportScale,
        offset = CanvasPosition(viewportOffsetX, viewportOffsetY),
    )
    val viewport = requestedViewport.clampTo(contentBounds, viewportSize)
    val latestViewport by rememberUpdatedState(viewport)

    fun updateViewport(newViewport: CanvasViewport) {
        viewportScale = newViewport.scale
        viewportOffsetX = newViewport.offset.x
        viewportOffsetY = newViewport.offset.y
    }

    LaunchedEffect(contentBounds, viewportSize) {
        if (viewport != requestedViewport) updateViewport(viewport)
    }

    val fieldShape = RoundedCornerShape(24.dp)
    val borderColor = if (state.isEditMode) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val gridDescription = stringResource(R.string.canvas_grid_description)
    val minorGridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)
    val majorGridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .clip(fieldShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (state.isEditMode) 3.dp else 1.dp,
                color = borderColor,
                shape = fieldShape,
            )
            .onSizeChanged { fieldSize = it }
            .testTag(HallTestTags.HALL_FIELD),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawCanvasGrid(
                    viewport = viewport,
                    minorColor = minorGridColor,
                    majorColor = majorGridColor,
                )
                .pointerInput(contentBounds, viewportSize) {
                    var workingViewport = latestViewport
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val focus = ScreenPosition(
                            x = centroid.x / density.density,
                            y = centroid.y / density.density,
                        ).toCanvasPosition(
                            CanvasTransform(workingViewport.scale, workingViewport.offset),
                        )
                        val zoomedViewport = workingViewport.zoomBy(
                            zoom,
                            focus,
                            contentBounds,
                            viewportSize,
                        )
                        workingViewport = zoomedViewport.panBy(
                                canvasDeltaX = pan.x / density.density / zoomedViewport.scale,
                                canvasDeltaY = pan.y / density.density / zoomedViewport.scale,
                                contentBounds = contentBounds,
                                viewportSize = viewportSize,
                            )
                        updateViewport(workingViewport)
                    }
                }
                .testTag(HallTestTags.CANVAS_GRID)
                .semantics {
                    contentDescription = gridDescription
                    canvasScale = viewport.scale
                    canvasOffsetX = viewport.offset.x
                    canvasOffsetY = viewport.offset.y
                },
        )

        if (state.tables.isEmpty()) {
            EmptyHall(modifier = Modifier.align(Alignment.Center))
        }

        state.tables.forEach { table ->
            androidx.compose.runtime.key(table.id) {
                HallTableItem(
                    table = table,
                    viewport = viewport,
                    isEditMode = state.isEditMode,
                    nowEpochMillis = nowEpochMillis,
                    onPositionChange = { tableId, position ->
                        onAction(HallAction.MoveTable(tableId, position))
                    },
                    onOpenSettings = { onOpenSettings(table.id) },
                    onAdvanceTimer = { onAction(HallAction.AdvanceTimer(table.id)) },
                    onDelete = { onAction(HallAction.RequestDelete(table.id)) },
                )
            }
        }

        if (state.isEditMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .testTag(HallTestTags.EDIT_MODE_INDICATOR),
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.edit_mode_active),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private fun Modifier.drawCanvasGrid(
    viewport: CanvasViewport,
    minorColor: Color,
    majorColor: Color,
): Modifier = drawBehind {
    val detail = gridDetailForScale(viewport.scale)
    val visibleWidth = size.width / density / viewport.scale
    val visibleHeight = size.height / density / viewport.scale

    fun drawGrid(step: Float, color: Color, strokeWidth: Float) {
        var worldX = floor(viewport.offset.x / step) * step
        val endX = viewport.offset.x + visibleWidth
        while (worldX <= endX) {
            val x = (worldX - viewport.offset.x) * viewport.scale * density
            drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth)
            worldX += step
        }
        var worldY = floor(viewport.offset.y / step) * step
        val endY = viewport.offset.y + visibleHeight
        while (worldY <= endY) {
            val y = (worldY - viewport.offset.y) * viewport.scale * density
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth)
            worldY += step
        }
    }

    if (detail.showMinorLines) drawGrid(detail.minorStep, minorColor, 1.dp.toPx())
    drawGrid(detail.majorStep, majorColor, 1.5.dp.toPx())
}

@Composable
private fun rememberCurrentEpochMillis(timeProvider: TimeProvider): Long {
    val lifecycleOwner = LocalLifecycleOwner.current
    var nowEpochMillis by remember(timeProvider) {
        mutableLongStateOf(timeProvider.nowEpochMillis())
    }
    LaunchedEffect(lifecycleOwner, timeProvider) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                nowEpochMillis = timeProvider.nowEpochMillis()
                delay(nextTimerTickDelayMillis(nowEpochMillis))
            }
        }
    }
    return nowEpochMillis
}

internal fun nextTimerTickDelayMillis(nowEpochMillis: Long): Long {
    val remainder = Math.floorMod(nowEpochMillis, MILLIS_PER_SECOND)
    return if (remainder == 0L) MILLIS_PER_SECOND else MILLIS_PER_SECOND - remainder
}

private const val MILLIS_PER_SECOND = 1_000L

@Composable
private fun EmptyHall(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.empty_hall_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.empty_hall_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

object HallTestTags {
    const val HALL_FIELD = "hall_field"
    const val CANVAS_GRID = "canvas_grid"
    const val TOGGLE_EDIT_MODE = "toggle_edit_mode"
    const val TOGGLE_FULLSCREEN = "toggle_fullscreen"
    const val EDIT_MODE_INDICATOR = "edit_mode_indicator"
    const val ADD_TABLE = "add_table"
    const val CONFIRM_DELETE = "confirm_delete"
    const val CANCEL_DELETE = "cancel_delete"

    fun table(id: String): String = "table_$id"
    fun deleteTable(id: String): String = "delete_table_$id"
    fun tableName(id: String): String = "table_name_$id"
    fun tableTimer(id: String): String = "table_timer_$id"
    fun tablePassage(id: String): String = "table_passage_$id"
    fun completedMark(id: String): String = "completed_mark_$id"
}

internal val CanvasScaleKey = SemanticsPropertyKey<Float>("CanvasScale")
internal val CanvasOffsetXKey = SemanticsPropertyKey<Float>("CanvasOffsetX")
internal val CanvasOffsetYKey = SemanticsPropertyKey<Float>("CanvasOffsetY")
internal var SemanticsPropertyReceiver.canvasScale by CanvasScaleKey
internal var SemanticsPropertyReceiver.canvasOffsetX by CanvasOffsetXKey
internal var SemanticsPropertyReceiver.canvasOffsetY by CanvasOffsetYKey

@Preview(widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun HallScreenTabletPreview() {
    HookahTimerTheme {
        HallScreen(
            state = HallUiState(
                tables = listOf(
                    HallTable(id = "one", name = "Стол 1", position = CanvasPosition(180f, 180f)),
                    HallTable(
                        id = "vip",
                        name = "VIP",
                        shape = TableShape.PILL,
                        position = CanvasPosition(520f, 380f),
                    ),
                ),
            ),
            onAction = {},
        )
    }
}

@Preview(widthDp = 600, heightDp = 800, showBackground = true)
@Composable
private fun HallScreenEditPreview() {
    HookahTimerTheme {
        HallScreen(
            state = HallUiState(
                tables = listOf(
                    HallTable(id = "one", name = "Стол 1"),
                    HallTable(
                        id = "pill",
                        name = "VIP",
                        shape = TableShape.PILL,
                        position = CanvasPosition(360f, 320f),
                    ),
                ),
                isEditMode = true,
            ),
            onAction = {},
        )
    }
}
