package ru.hznik.hookahtimer.hall.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.focus.focusProperties
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
import ru.hznik.hookahtimer.hall.model.findNearestAvailableTablePosition
import ru.hznik.hookahtimer.hall.model.gridDetailForScale
import ru.hznik.hookahtimer.hall.model.toCanvasPosition
import ru.hznik.hookahtimer.hall.presentation.HallAction
import ru.hznik.hookahtimer.hall.presentation.HallUiState
import ru.hznik.hookahtimer.ui.theme.HookahTimerTheme
import ru.hznik.hookahtimer.ui.icons.AppIcons
import ru.hznik.hookahtimer.ui.theme.HallCanvasColor
import ru.hznik.hookahtimer.ui.theme.HallMajorGridColor
import ru.hznik.hookahtimer.ui.theme.HallMinorGridColor

@Composable
fun HallScreen(
    state: HallUiState,
    onAction: (HallAction) -> Unit,
    onOpenSettings: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    timeProvider: TimeProvider = SystemTimeProvider,
) {
    val pendingDeleteTable = state.tables.firstOrNull { it.id == state.pendingDeleteTableId }
    val hookahTable = state.tables.find {
        it.id == state.selectedHookahTableId && it.hasMultipleHookahs && !state.isEditMode
    }
    var previousHookahTableId by remember { mutableStateOf<String?>(null) }
    var restoreFocusTableId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hookahTable?.id) {
        restoreFocusTableId = if (hookahTable == null) previousHookahTableId else null
        previousHookahTableId = hookahTable?.id
    }
    val snackbar = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.hall_command_error)
    LaunchedEffect(state.hasCommandError) {
        if (state.hasCommandError) {
            snackbar.showSnackbar(errorMessage)
            onAction(HallAction.DismissCommandError)
        }
    }
    val nowEpochMillis = rememberCurrentEpochMillis(timeProvider)
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    var viewportScale by rememberSaveable { mutableFloatStateOf(1f) }
    var viewportOffsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var viewportOffsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var isViewportLocked by rememberSaveable { mutableStateOf(false) }
    var isActionMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val viewportSize = with(density) {
        CanvasSize(
            width = fieldSize.width.toDp().value,
            height = fieldSize.height.toDp().value,
        )
    }
    val tableBounds = remember(state.tables) {
        state.tables.map { table ->
            val dimensions = tableCanvasDimensions(table.shape)
            CanvasItemBounds(
                position = table.position,
                width = dimensions.width.value,
                height = dimensions.height.value,
            )
        }
    }
    val contentBounds = remember(tableBounds, viewportSize) {
        calculateCanvasContentBounds(tableBounds, viewportSize)
    }
    val requestedViewport = CanvasViewport(
        scale = viewportScale,
        offset = CanvasPosition(viewportOffsetX, viewportOffsetY),
    )
    // Do not overwrite a restored viewport before the recreated field is measured.
    val viewport = if (viewportSize == CanvasSize.Zero) {
        requestedViewport
    } else {
        requestedViewport.clampTo(contentBounds, viewportSize)
    }
    val newTablePosition = remember(viewport, viewportSize, tableBounds) {
        if (viewportSize == CanvasSize.Zero) {
            CanvasPosition.Default
        } else {
            findNearestAvailableTablePosition(
                preferredCenter = viewport.center(viewportSize),
                tableSize = CanvasSize(
                    width = tableCanvasDimensions(TableShape.CIRCLE).width.value,
                    height = tableCanvasDimensions(TableShape.CIRCLE).height.value,
                ),
                occupiedItems = tableBounds,
                visibleBounds = viewport.visibleRect(viewportSize),
            )
        }
    }

    fun updateViewport(newViewport: CanvasViewport) {
        viewportScale = newViewport.scale
        viewportOffsetX = newViewport.offset.x
        viewportOffsetY = newViewport.offset.y
    }

    LaunchedEffect(contentBounds, viewportSize) {
        if (viewport != requestedViewport) updateViewport(viewport)
    }
    LaunchedEffect(state.isEditMode) {
        isActionMenuExpanded = false
    }

    val contentWindowInsets = hallContentWindowInsets(state.isFullscreenEnabled)
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize().then(
                if (hookahTable != null) Modifier.clearAndSetSemantics {}
                    .focusProperties { canFocus = false } else Modifier,
            ),
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                HallFloatingActions(
                    state = state,
                    isExpanded = isActionMenuExpanded,
                    isViewportLocked = isViewportLocked,
                    onToggleExpanded = { isActionMenuExpanded = !isActionMenuExpanded },
                    onToggleViewportLock = {
                        isViewportLocked = !isViewportLocked
                        isActionMenuExpanded = false
                    },
                    onAddTable = {
                        isActionMenuExpanded = false
                        onAction(HallAction.AddTable(newTablePosition))
                    },
                    onAction = { action ->
                        isActionMenuExpanded = false
                        onAction(action)
                    },
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = contentWindowInsets,
        ) { contentPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                HallField(
                    state = state,
                    viewport = viewport,
                    viewportSize = viewportSize,
                    contentBounds = contentBounds,
                    isViewportLocked = isViewportLocked,
                    onViewportChange = ::updateViewport,
                    onFieldSizeChanged = { fieldSize = it },
                    onAction = onAction,
                    onOpenSettings = onOpenSettings,
                    nowEpochMillis = nowEpochMillis,
                    restoreFocusTableId = restoreFocusTableId,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(12.dp),
                )
                if (isActionMenuExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { isActionMenuExpanded = false }
                            }
                            .testTag(HallTestTags.ACTION_MENU_DISMISS_AREA),
                    )
                }
            }
        }

        if (hookahTable != null) {
            TableHookahsOverlay(
                table = hookahTable,
                nowEpochMillis = nowEpochMillis,
                onAdvance = { onAction(HallAction.AdvanceHookah(hookahTable.id, it)) },
                onClose = { onAction(HallAction.CloseHookahs) },
                contentWindowInsets = contentWindowInsets,
                hasCommandError = state.hasCommandError,
            )
        }
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
    isExpanded: Boolean,
    isViewportLocked: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleViewportLock: () -> Unit,
    onAddTable: () -> Unit,
    onAction: (HallAction) -> Unit,
) {
    val addDescription = stringResource(R.string.add_table)
    val editDescription = stringResource(R.string.edit_hall)
    val finishDescription = stringResource(R.string.finish_editing)
    val fullscreenDescription = stringResource(
        if (state.isFullscreenEnabled) R.string.exit_fullscreen else R.string.enter_fullscreen,
    )
    val menuDescription = stringResource(
        if (isExpanded) R.string.close_hall_actions else R.string.open_hall_actions,
    )
    val lockDescription = stringResource(
        if (isViewportLocked) R.string.unlock_canvas else R.string.lock_canvas,
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.End,
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (state.isEditMode) {
                    HallSpeedDialAction(
                        label = addDescription,
                        icon = AppIcons.Add,
                        testTag = HallTestTags.ADD_TABLE,
                        onClick = onAddTable,
                    )
                    HallSpeedDialAction(
                        label = finishDescription,
                        icon = AppIcons.Check,
                        testTag = HallTestTags.TOGGLE_EDIT_MODE,
                        onClick = { onAction(HallAction.ToggleEditMode) },
                    )
                } else {
                    HallSpeedDialAction(
                        label = fullscreenDescription,
                        icon = if (state.isFullscreenEnabled) {
                            AppIcons.FullscreenExit
                        } else {
                            AppIcons.Fullscreen
                        },
                        testTag = HallTestTags.TOGGLE_FULLSCREEN,
                        onClick = { onAction(HallAction.ToggleFullscreen) },
                    )
                    HallSpeedDialAction(
                        label = editDescription,
                        icon = AppIcons.Edit,
                        testTag = HallTestTags.TOGGLE_EDIT_MODE,
                        onClick = { onAction(HallAction.ToggleEditMode) },
                    )
                    HallSpeedDialAction(
                        label = lockDescription,
                        icon = if (isViewportLocked) AppIcons.LockOpen else AppIcons.Lock,
                        testTag = HallTestTags.TOGGLE_VIEWPORT_LOCK,
                        onClick = onToggleViewportLock,
                    )
                }
            }
        }
        Box {
            FloatingActionButton(
                onClick = onToggleExpanded,
                modifier = Modifier
                    .testTag(HallTestTags.ACTION_MENU)
                    .semantics { contentDescription = menuDescription },
            ) {
                Icon(
                    imageVector = if (isExpanded) AppIcons.Close else AppIcons.Menu,
                    contentDescription = null,
                )
            }
            if (isViewportLocked) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 5.dp, y = (-5).dp)
                        .size(22.dp)
                        .testTag(HallTestTags.VIEWPORT_LOCKED_INDICATOR),
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        imageVector = AppIcons.Lock,
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HallSpeedDialAction(
    label: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 3.dp,
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            modifier = Modifier
                .testTag(testTag)
                .semantics { contentDescription = label },
        ) {
            Icon(imageVector = icon, contentDescription = null)
        }
    }
}

@Composable
private fun HallField(
    state: HallUiState,
    viewport: CanvasViewport,
    viewportSize: CanvasSize,
    contentBounds: CanvasRect,
    isViewportLocked: Boolean,
    onViewportChange: (CanvasViewport) -> Unit,
    onFieldSizeChanged: (IntSize) -> Unit,
    onAction: (HallAction) -> Unit,
    onOpenSettings: (String) -> Unit,
    nowEpochMillis: Long,
    restoreFocusTableId: String?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val latestViewport by rememberUpdatedState(viewport)

    val fieldShape = RoundedCornerShape(24.dp)
    val borderColor = if (state.isEditMode) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val gridDescription = stringResource(R.string.canvas_grid_description)
    val logoDescription = stringResource(R.string.venue_logo)

    Box(
        modifier = modifier
            .clip(fieldShape)
            .background(HallCanvasColor)
            .border(
                width = if (state.isEditMode) 3.dp else 1.dp,
                color = borderColor,
                shape = fieldShape,
            )
            .then(
                if (isViewportLocked) {
                    Modifier
                } else {
                    Modifier.pointerInput(contentBounds, viewportSize) {
                        var workingViewport = latestViewport
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val focus = ScreenPosition(
                                x = centroid.x / density.density,
                                y = centroid.y / density.density,
                            ).toCanvasPosition(
                                CanvasTransform(
                                    workingViewport.scale,
                                    workingViewport.offset,
                                ),
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
                            onViewportChange(workingViewport)
                        }
                    }
                }
            )
            .onSizeChanged(onFieldSizeChanged)
            .testTag(HallTestTags.HALL_FIELD),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawCanvasGrid(
                    viewport = viewport,
                    minorColor = HallMinorGridColor,
                    majorColor = HallMajorGridColor,
                )
                .testTag(HallTestTags.CANVAS_GRID)
                .semantics {
                    contentDescription = gridDescription
                    canvasScale = viewport.scale
                    canvasOffsetX = viewport.offset.x
                    canvasOffsetY = viewport.offset.y
                    canvasLocked = isViewportLocked
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
                    onAddHookah = { onAction(HallAction.AddHookah(table.id)) },
                    onDelete = { onAction(HallAction.RequestDelete(table.id)) },
                    restoreFocus = table.id == restoreFocusTableId,
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(76.dp)
                .alpha(0.9f),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            tonalElevation = 2.dp,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_logo),
                contentDescription = logoDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .testTag(HallTestTags.VENUE_LOGO),
            )
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
    const val ACTION_MENU = "hall_action_menu"
    const val ACTION_MENU_DISMISS_AREA = "hall_action_menu_dismiss_area"
    const val TOGGLE_EDIT_MODE = "toggle_edit_mode"
    const val TOGGLE_FULLSCREEN = "toggle_fullscreen"
    const val TOGGLE_VIEWPORT_LOCK = "toggle_viewport_lock"
    const val VIEWPORT_LOCKED_INDICATOR = "viewport_locked_indicator"
    const val VENUE_LOGO = "venue_logo"
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
internal val CanvasLockedKey = SemanticsPropertyKey<Boolean>("CanvasLocked")
internal var SemanticsPropertyReceiver.canvasScale by CanvasScaleKey
internal var SemanticsPropertyReceiver.canvasOffsetX by CanvasOffsetXKey
internal var SemanticsPropertyReceiver.canvasOffsetY by CanvasOffsetYKey
internal var SemanticsPropertyReceiver.canvasLocked by CanvasLockedKey

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
