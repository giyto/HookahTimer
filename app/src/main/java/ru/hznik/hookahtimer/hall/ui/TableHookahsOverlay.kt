package ru.hznik.hookahtimer.hall.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.hznik.hookahtimer.R
import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.TableHookah
import ru.hznik.hookahtimer.hall.model.timerPresentation
import ru.hznik.hookahtimer.ui.icons.AppIcons

/** Same Activity window: never changes immersive mode or the window's insets. */
@Composable
internal fun TableHookahsOverlay(
    table: HallTable,
    nowEpochMillis: Long,
    onAdvance: (String) -> Unit,
    onClose: () -> Unit,
    contentWindowInsets: WindowInsets,
    hasCommandError: Boolean = false,
) {
    BackHandler(onBack = onClose)
    val closeFocus = remember(table.id) { FocusRequester() }
    val title = stringResource(R.string.hookahs_title, table.name)
    val closeDescription = stringResource(R.string.close_hookahs)
    LaunchedEffect(table.id) { closeFocus.requestFocus() }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(onClickLabel = closeDescription, onClick = onClose)
                .testTag(HookahTestTags.DISMISS_AREA)
                .semantics { contentDescription = closeDescription },
        )
        Box(
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(contentWindowInsets).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().fillMaxHeight(0.9f)
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                    .testTag(HookahTestTags.OVERLAY)
                    .semantics { paneTitle = title; isTraversalGroup = true },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.focusRequester(closeFocus).testTag(HookahTestTags.CLOSE),
                        ) {
                            Icon(AppIcons.Close, stringResource(R.string.close_hookahs))
                        }
                    }
                    if (hasCommandError) {
                        Text(
                            stringResource(R.string.hall_command_error),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f).testTag(HookahTestTags.GRID),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(table.hookahs, key = { it.id }) { hookah ->
                            HookahCard(table, hookah, nowEpochMillis) { onAdvance(hookah.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HookahCard(
    table: HallTable,
    hookah: TableHookah,
    nowEpochMillis: Long,
    onAdvance: () -> Unit,
) {
    val timer = hookah.timerPresentation(table.passages, nowEpochMillis)
    val title = stringResource(R.string.hookah_number, hookah.number)
    val description = when {
        timer.isCompleted -> stringResource(R.string.completed_table_description, title)
        timer.isOverdue -> stringResource(
            R.string.overdue_table_description, title, checkNotNull(timer.passageNumber),
            table.passages.size, checkNotNull(timer.timerText),
        )
        timer.isEndingSoon -> stringResource(
            R.string.ending_soon_table_description, title, checkNotNull(timer.passageNumber),
            table.passages.size, checkNotNull(timer.timerText),
        )
        timer.timerText != null -> stringResource(
            R.string.running_table_description, title, checkNotNull(timer.passageNumber),
            table.passages.size, timer.timerText,
        )
        else -> "$title, ${stringResource(R.string.hookah_idle)}"
    }
    Surface(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            .clickable(
                enabled = !timer.isCompleted,
                role = Role.Button,
                onClickLabel = stringResource(
                    if (timer.timerText == null) R.string.start_hookah else R.string.advance_hookah,
                ),
                onClick = onAdvance,
            )
            .testTag(HookahTestTags.card(hookah.id))
            .semantics {
                contentDescription = description
                tableTimerVisualState = when {
                    timer.isOverdue -> TableTimerVisualState.OVERDUE
                    timer.isEndingSoon -> TableTimerVisualState.ENDING_SOON
                    else -> TableTimerVisualState.NORMAL
                }
            },
        shape = RoundedCornerShape(16.dp),
        color = if (timer.isOverdue) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (timer.isOverdue) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
        border = if (timer.isEndingSoon) BorderStroke(3.dp, Color.Red) else null,
    ) {
        // The card has one complete announcement; decorative children are silent.
        Box(Modifier.fillMaxSize().padding(12.dp).clearAndSetSemantics {}) {
            Row(
                modifier = Modifier.align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Hookah, null, Modifier.size(22.dp))
                Text("№${hookah.number}", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Text(
                text = if (timer.isCompleted) "×" else timer.timerText ?: stringResource(R.string.hookah_idle),
                modifier = Modifier.align(Alignment.Center),
                color = if (timer.isCompleted || timer.isEndingSoon) Color.Red else Color.Unspecified,
                fontSize = if (timer.isCompleted) 42.sp else if (timer.timerText == null) 16.sp else 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (timer.passageNumber != null) {
                Text(
                    text = stringResource(R.string.table_passage_indicator, timer.passageNumber, table.passages.size),
                    modifier = Modifier.align(Alignment.BottomCenter),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

internal object HookahTestTags {
    const val OVERLAY = "table_hookahs_overlay"
    const val DISMISS_AREA = "table_hookahs_dismiss"
    const val CLOSE = "table_hookahs_close"
    const val GRID = "table_hookahs_grid"
    fun card(id: String) = "hookah_card_$id"
    fun tableCount(id: String) = "table_hookahs_count_$id"
}
