package ru.hznik.hookahtimer.hall.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    BackHandler(onBack = onCancel)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

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
                .imePadding()
                .pointerInput(focusManager, keyboardController) {
                    detectTapGestures {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                }
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
                OutlinedTextField(
                    value = state.nameInput,
                    onValueChange = { onAction(TableSettingsAction.ChangeName(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewWhenFocused()
                        .testTag(TableSettingsTestTags.NAME),
                    label = { Text(stringResource(R.string.table_name)) },
                    singleLine = true,
                    isError = state.nameHasError,
                    supportingText = if (state.nameHasError) {
                        { Text(stringResource(R.string.table_name_error)) }
                    } else {
                        null
                    },
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
                            onDurationChange = { value ->
                                onAction(
                                    TableSettingsAction.ChangePassageDuration(
                                        passageId = passage.id,
                                        value = value,
                                    ),
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
    onDurationChange: (String) -> Unit,
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
            OutlinedTextField(
                value = passage.durationInput,
                onValueChange = onDurationChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewWhenFocused()
                    .testTag(TableSettingsTestTags.passageDuration(passage.id)),
                label = { Text(stringResource(R.string.passage_duration)) },
                suffix = { Text(stringResource(R.string.minutes_short)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = passage.durationHasError,
                supportingText = if (passage.durationHasError) {
                    { Text(stringResource(R.string.passage_duration_error)) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun Modifier.bringIntoViewWhenFocused(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return this
        .bringIntoViewRequester(requester)
        .onFocusChanged { focusState ->
            if (focusState.isFocused) {
                scope.launch {
                    withTimeoutOrNull(BRING_INTO_VIEW_TIMEOUT_MILLIS) {
                        requester.bringIntoView()
                    }
                }
            }
        }
}

private const val BRING_INTO_VIEW_TIMEOUT_MILLIS = 1_000L

object TableSettingsTestTags {
    const val BACKGROUND = "table_settings_background"
    const val NAME = "table_settings_name"
    const val CIRCLE_SHAPE = "table_settings_circle"
    const val PILL_SHAPE = "table_settings_pill"
    const val ADD_PASSAGE = "table_settings_add_passage"
    const val SAVE = "table_settings_save"
    const val CANCEL = "table_settings_cancel"

    fun passageDuration(id: String): String = "table_settings_passage_duration_$id"

    fun deletePassage(id: String): String = "table_settings_delete_passage_$id"
}
