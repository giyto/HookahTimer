package ru.hznik.hookahtimer.hall.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.hznik.hookahtimer.R
import ru.hznik.hookahtimer.hall.model.TableShape
import ru.hznik.hookahtimer.hall.settings.presentation.PassageDraft
import ru.hznik.hookahtimer.hall.settings.presentation.TableSettingsAction
import ru.hznik.hookahtimer.hall.settings.presentation.TableSettingsUiState
import ru.hznik.hookahtimer.ui.icons.AppIcons

@Composable
fun TableSettingsScreen(
    state: TableSettingsUiState,
    onAction: (TableSettingsAction) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeEditorKey by rememberSaveable { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismiss = {
        if (activeEditorKey != null) activeEditorKey = null else onCancel()
    }
    BackHandler(onBack = dismiss)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .testTag(TableSettingsTestTags.BACKGROUND)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = dismiss,
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val isSidePanel = maxWidth >= SIDE_PANEL_MIN_WIDTH
        Surface(
            modifier = Modifier
                .then(
                    if (isSidePanel) {
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .widthIn(min = 400.dp, max = 480.dp)
                            .fillMaxWidth(0.42f)
                            .testTag(TableSettingsTestTags.SIDE_PANEL)
                    } else {
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .heightIn(max = maxHeight * 0.88f)
                            .testTag(TableSettingsTestTags.BOTTOM_PANEL)
                    },
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                )
                .imePadding(),
            shape = if (isSidePanel) {
                RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
            } else {
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            },
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SettingsHeader(
                    tableLabel = state.tableLabel,
                    onSave = { onAction(TableSettingsAction.Save) },
                    onCancel = onCancel,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OutlinedTextField(
                        value = state.nameInput,
                        onValueChange = { onAction(TableSettingsAction.ChangeName(it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TableSettingsTestTags.NAME),
                        label = { Text(stringResource(R.string.table_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            },
                        ),
                        isError = state.nameHasError,
                        supportingText = if (state.nameHasError) {
                            { Text(stringResource(R.string.table_name_error)) }
                        } else {
                            null
                        },
                    )

                    ShapeSelector(
                        selectedShape = state.shape,
                        onShapeSelected = { onAction(TableSettingsAction.ChangeShape(it)) },
                    )

                    Text(
                        text = stringResource(R.string.passages_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    state.passages.forEachIndexed { index, passage ->
                        key(passage.id) {
                            PassageRow(
                                passage = passage,
                                number = index + 1,
                                canDelete = state.passages.size > 1,
                                onEditDuration = { activeEditorKey = passage.id },
                                onDelete = {
                                    onAction(TableSettingsAction.RemovePassage(passage.id))
                                },
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { onAction(TableSettingsAction.AddPassage) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag(TableSettingsTestTags.ADD_PASSAGE),
                    ) {
                        Text(stringResource(R.string.add_passage))
                    }
                }
            }
        }
    }

    when (val editorKey = activeEditorKey) {
        null -> Unit

        else -> {
            val index = state.passages.indexOfFirst { it.id == editorKey }
            val passage = state.passages.getOrNull(index)
            if (passage == null) {
                LaunchedEffect(editorKey) { activeEditorKey = null }
            } else {
                SettingsFieldEditorDialog(
                    editorKey = editorKey,
                    title = stringResource(R.string.passage_number, index + 1),
                    value = passage.durationInput,
                    onValueChange = { value ->
                        onAction(TableSettingsAction.ChangePassageDuration(editorKey, value))
                    },
                    keyboardType = KeyboardType.Number,
                    suffix = stringResource(R.string.minutes_short),
                    isError = passage.durationHasError,
                    errorText = stringResource(R.string.passage_duration_error),
                    onDismiss = { activeEditorKey = null },
                )
            }
        }
    }
}

@Composable
private fun SettingsFieldEditorDialog(
    editorKey: String,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    onDismiss: () -> Unit,
    suffix: String? = null,
    isError: Boolean = false,
    errorText: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(TableSettingsTestTags.EDITOR_DIALOG),
        title = { Text(title) },
        text = {
            AutoFocusedEditorField(
                editorKey = editorKey,
                value = value,
                onValueChange = onValueChange,
                label = stringResource(R.string.passage_duration).takeIf { suffix != null } ?: title,
                keyboardType = keyboardType,
                suffix = suffix,
                isError = isError,
                errorText = errorText,
                onDone = onDismiss,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TableSettingsTestTags.EDITOR_DONE),
            ) {
                Text(stringResource(R.string.finish_editing))
            }
        },
    )
}

@Composable
private fun AutoFocusedEditorField(
    editorKey: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    onDone: () -> Unit,
    suffix: String? = null,
    isError: Boolean = false,
    errorText: String? = null,
) {
    val focusRequester = remember(editorKey) { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(editorKey) {
        withFrameNanos { }
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .testTag(TableSettingsTestTags.EDITOR_FIELD),
        label = { Text(label) },
        suffix = suffix?.let { suffixText -> { Text(suffixText) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        isError = isError,
        supportingText = if (isError && errorText != null) ({ Text(errorText) }) else null,
    )
}

@Composable
private fun SettingsHeader(
    tableLabel: String,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.table_settings_title, tableLabel),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag(TableSettingsTestTags.CANCEL),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.testTag(TableSettingsTestTags.SAVE),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun ShapeSelector(
    selectedShape: TableShape,
    onShapeSelected: (TableShape) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.table_shape),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = selectedShape == TableShape.CIRCLE,
                onClick = { onShapeSelected(TableShape.CIRCLE) },
                label = { Text(stringResource(R.string.circle_shape)) },
                modifier = Modifier.testTag(TableSettingsTestTags.CIRCLE_SHAPE),
            )
            FilterChip(
                selected = selectedShape == TableShape.PILL,
                onClick = { onShapeSelected(TableShape.PILL) },
                label = { Text(stringResource(R.string.pill_shape)) },
                modifier = Modifier.testTag(TableSettingsTestTags.PILL_SHAPE),
            )
        }
    }
}

@Composable
private fun PassageRow(
    passage: PassageDraft,
    number: Int,
    canDelete: Boolean,
    onEditDuration: () -> Unit,
    onDelete: () -> Unit,
) {
    val deleteDescription = stringResource(R.string.delete_passage, number)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onEditDuration)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag(TableSettingsTestTags.passageDuration(passage.id)),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.passage_number, number),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${passage.durationInput} ${stringResource(R.string.minutes_short)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (passage.durationHasError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (passage.durationHasError) {
                    Text(
                        text = stringResource(R.string.passage_duration_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                enabled = canDelete,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .testTag(TableSettingsTestTags.deletePassage(passage.id))
                    .semantics { contentDescription = deleteDescription },
            ) {
                Icon(AppIcons.Delete, contentDescription = null)
            }
        }
    }
}

object TableSettingsTestTags {
    const val BACKGROUND = "table_settings_background"
    const val SIDE_PANEL = "table_settings_side_panel"
    const val BOTTOM_PANEL = "table_settings_bottom_panel"
    const val NAME = "table_settings_name"
    const val CIRCLE_SHAPE = "table_settings_circle"
    const val PILL_SHAPE = "table_settings_pill"
    const val ADD_PASSAGE = "table_settings_add_passage"
    const val SAVE = "table_settings_save"
    const val CANCEL = "table_settings_cancel"
    const val EDITOR_DIALOG = "table_settings_editor_dialog"
    const val EDITOR_FIELD = "table_settings_editor_field"
    const val EDITOR_DONE = "table_settings_editor_done"

    fun passageDuration(id: String): String = "table_settings_passage_duration_$id"
    fun deletePassage(id: String): String = "table_settings_delete_passage_$id"
}

private val SIDE_PANEL_MIN_WIDTH = 600.dp
