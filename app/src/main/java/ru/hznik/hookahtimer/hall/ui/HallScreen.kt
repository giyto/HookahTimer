package ru.hznik.hookahtimer.hall.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ru.hznik.hookahtimer.R
import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.NormalizedPosition
import ru.hznik.hookahtimer.hall.model.TableShape
import ru.hznik.hookahtimer.hall.presentation.HallAction
import ru.hznik.hookahtimer.hall.presentation.HallUiState
import ru.hznik.hookahtimer.ui.theme.HookahTimerTheme

@Composable
fun HallScreen(
    state: HallUiState,
    onAction: (HallAction) -> Unit,
    onOpenSettings: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val pendingDeleteTable = state.tables.firstOrNull { it.id == state.pendingDeleteTableId }
    val addTableDescription = stringResource(R.string.add_table)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            HallTopBar(
                isEditMode = state.isEditMode,
                onToggleEditMode = { onAction(HallAction.ToggleEditMode) },
            )
        },
        floatingActionButton = {
            if (state.isEditMode) {
                FloatingActionButton(
                    onClick = { onAction(HallAction.AddTable) },
                    modifier = Modifier
                        .testTag(HallTestTags.ADD_TABLE)
                        .semantics { contentDescription = addTableDescription },
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        HallField(
            state = state,
            onAction = onAction,
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(16.dp),
        )
    }

    if (pendingDeleteTable != null) {
        AlertDialog(
            onDismissRequest = { onAction(HallAction.CancelDelete) },
            title = { Text(stringResource(R.string.delete_table_title)) },
            text = {
                Text(stringResource(R.string.delete_table_message, pendingDeleteTable.name))
            },
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
private fun HallTopBar(
    isEditMode: Boolean,
    onToggleEditMode: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = 72.dp)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.hall_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Button(
                onClick = onToggleEditMode,
                modifier = Modifier.testTag(HallTestTags.TOGGLE_EDIT_MODE),
            ) {
                Text(
                    stringResource(
                        if (isEditMode) R.string.finish_editing else R.string.edit_hall,
                    ),
                )
            }
        }
    }
}

@Composable
private fun HallField(
    state: HallUiState,
    onAction: (HallAction) -> Unit,
    onOpenSettings: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    val fieldShape = RoundedCornerShape(24.dp)
    val borderColor = if (state.isEditMode) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

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
        if (state.tables.isEmpty()) {
            EmptyHall(modifier = Modifier.align(Alignment.Center))
        }

        state.tables.forEach { table ->
            androidx.compose.runtime.key(table.id) {
                HallTableItem(
                    table = table,
                    fieldSize = fieldSize,
                    isEditMode = state.isEditMode,
                    onPositionChange = { tableId, position ->
                        onAction(HallAction.MoveTable(tableId, position))
                    },
                    onOpenSettings = { onOpenSettings(table.id) },
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
    const val TOGGLE_EDIT_MODE = "toggle_edit_mode"
    const val EDIT_MODE_INDICATOR = "edit_mode_indicator"
    const val ADD_TABLE = "add_table"
    const val CONFIRM_DELETE = "confirm_delete"
    const val CANCEL_DELETE = "cancel_delete"

    fun table(id: String): String = "table_$id"

    fun deleteTable(id: String): String = "delete_table_$id"
}

@Preview(widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun HallScreenTabletPreview() {
    HookahTimerTheme {
        HallScreen(
            state = HallUiState(
                tables = listOf(
                    HallTable(
                        id = "one",
                        name = "Стол 1",
                        position = NormalizedPosition.of(0.2f, 0.3f),
                    ),
                    HallTable(
                        id = "two",
                        name = "VIP",
                        shape = TableShape.PILL,
                        position = NormalizedPosition.of(0.7f, 0.6f),
                    ),
                ),
                isEditMode = true,
            ),
            onAction = {},
        )
    }
}
