package ru.hznik.hookahtimer.hall.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
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

@Composable
fun TableSettingsScreen(
    state: TableSettingsUiState,
    onAction: (TableSettingsAction) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeEditor by remember { mutableStateOf<SettingsFieldEditor?>(null) }
    BackHandler {
        if (activeEditor != null) {
            activeEditor = null
        } else {
            onCancel()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SettingsTopBar(
                tableLabel = state.tableLabel,
                onSave = { onAction(TableSettingsAction.Save) },
                onCancel = onCancel,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .testTag(TableSettingsTestTags.BACKGROUND),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                EditableSettingValue(
                    value = state.nameInput,
                    label = stringResource(R.string.table_name),
                    onClick = { activeEditor = SettingsFieldEditor.Name },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TableSettingsTestTags.NAME),
                    isError = state.nameHasError,
                    errorText = stringResource(R.string.table_name_error),
                )

                ShapeSelector(
                    selectedShape = state.shape,
                    onShapeSelected = {
                        onAction(TableSettingsAction.ChangeShape(it))
                    },
                )

                Text(
                    text = stringResource(R.string.passages_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                state.passages.forEachIndexed { index, passage ->
                    key(passage.id) {
                        PassageEditor(
                            passage = passage,
                            number = index + 1,
                            canDelete = state.passages.size > 1,
                            onEditDuration = {
                                activeEditor = SettingsFieldEditor.PassageDuration(
                                    passageId = passage.id,
                                    number = index + 1,
                                )
                            },
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
                        .heightIn(min = 52.dp)
                        .testTag(TableSettingsTestTags.ADD_PASSAGE),
                ) {
                    Text(stringResource(R.string.add_passage))
                }
            }
        }
    }

    when (val editor = activeEditor) {
        SettingsFieldEditor.Name -> SettingsFieldEditorDialog(
            editorKey = "name",
            title = stringResource(R.string.table_name),
            value = state.nameInput,
            onValueChange = { onAction(TableSettingsAction.ChangeName(it)) },
            keyboardType = KeyboardType.Text,
            isError = state.nameHasError,
            errorText = stringResource(R.string.table_name_error),
            onDismiss = { activeEditor = null },
        )

        is SettingsFieldEditor.PassageDuration -> {
            val passage = state.passages.firstOrNull { it.id == editor.passageId }
            if (passage != null) {
                SettingsFieldEditorDialog(
                    editorKey = editor.passageId,
                    title = stringResource(R.string.passage_number, editor.number),
                    value = passage.durationInput,
                    onValueChange = { value ->
                        onAction(
                            TableSettingsAction.ChangePassageDuration(
                                passageId = editor.passageId,
                                value = value,
                            ),
                        )
                    },
                    keyboardType = KeyboardType.Number,
                    suffix = stringResource(R.string.minutes_short),
                    isError = passage.durationHasError,
                    errorText = stringResource(R.string.passage_duration_error),
                    onDismiss = { activeEditor = null },
                )
            } else {
                LaunchedEffect(editor) { activeEditor = null }
            }
        }

        null -> Unit
    }
}

@Composable
private fun EditableSettingValue(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    isError: Boolean = false,
    errorText: String? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = listOfNotNull(value.ifEmpty { " " }, suffix).joinToString(" "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (isError && errorText != null) {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
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
        suffix = suffix?.let { suffixText ->
            { Text(suffixText) }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        isError = isError,
        supportingText = if (isError && errorText != null) {
            { Text(errorText) }
        } else {
            null
        },
    )
}

@Composable
private fun SettingsTopBar(
    tableLabel: String,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                )
                .heightIn(min = 72.dp)
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.table_settings_title, tableLabel),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
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

@Composable
private fun ShapeSelector(
    selectedShape: TableShape,
    onShapeSelected: (TableShape) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.table_shape),
            style = MaterialTheme.typography.titleLarge,
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
private fun PassageEditor(
    passage: PassageDraft,
    number: Int,
    canDelete: Boolean,
    onEditDuration: () -> Unit,
    onDelete: () -> Unit,
) {
    val deleteDescription = stringResource(R.string.delete_passage, number)

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.passage_number, number),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onDelete,
                    enabled = canDelete,
                    modifier = Modifier
                        .testTag(TableSettingsTestTags.deletePassage(passage.id))
                        .semantics { contentDescription = deleteDescription },
                ) {
                    Text(stringResource(R.string.delete))
                }
            }
            EditableSettingValue(
                value = passage.durationInput,
                label = stringResource(R.string.passage_duration),
                onClick = onEditDuration,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TableSettingsTestTags.passageDuration(passage.id)),
                suffix = stringResource(R.string.minutes_short),
                isError = passage.durationHasError,
                errorText = stringResource(R.string.passage_duration_error),
            )
        }
    }
}

private sealed interface SettingsFieldEditor {
    data object Name : SettingsFieldEditor

    data class PassageDuration(
        val passageId: String,
        val number: Int,
    ) : SettingsFieldEditor
}

object TableSettingsTestTags {
    const val BACKGROUND = "table_settings_background"
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
