package ru.hznik.hookahtimer.hall.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.TablePassage
import ru.hznik.hookahtimer.hall.model.TableShape
import ru.hznik.hookahtimer.hall.settings.presentation.TableSettingsViewModel
import ru.hznik.hookahtimer.ui.theme.HookahTimerTheme

class TableSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun initialSettingsAndShapeAreDisplayed() {
        setViewModelContent(TableSettingsViewModel(configuredTable()))

        composeRule.onNodeWithTag(TableSettingsTestTags.NAME).assertTextContains("VIP")
        composeRule.onNodeWithTag(TableSettingsTestTags.PILL_SHAPE).assertIsSelected()
        composeRule.onNodeWithText("Пилюля").assertIsDisplayed()
        composeRule.onNodeWithText("Проходка 1").assertIsDisplayed()
        composeRule.onNodeWithText("Проходка 2").assertIsDisplayed()
        composeRule.onNodeWithTag(TableSettingsTestTags.passageDuration("passage-1"))
            .assertTextContains("20 мин")
    }

    @Test
    fun passagesCanBeAddedDeletedAndRenumbered() {
        val viewModel = TableSettingsViewModel(
            initialTable = configuredTable(),
            passageIdFactory = { "passage-3" },
        )
        setViewModelContent(viewModel)

        composeRule.onNodeWithTag(TableSettingsTestTags.ADD_PASSAGE)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(3, viewModel.state.value.passages.size)
        composeRule.onNodeWithText("Проходка 3")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag(TableSettingsTestTags.deletePassage("passage-1"))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Проходка 2")
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(listOf("passage-2", "passage-3"), viewModel.state.value.passages.map { it.id })

        composeRule.onNodeWithTag(TableSettingsTestTags.deletePassage("passage-2"))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TableSettingsTestTags.deletePassage("passage-3"))
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun invalidFieldsShowErrorsAndDoNotProduceSaveResult() {
        val viewModel = TableSettingsViewModel(configuredTable())
        setViewModelContent(viewModel)

        editField(TableSettingsTestTags.NAME, "   ")
        editField(TableSettingsTestTags.passageDuration("passage-1"), "0")
        composeRule.onNodeWithTag(TableSettingsTestTags.SAVE).performClick()

        composeRule.onNodeWithText("Введите название стола").assertIsDisplayed()
        composeRule.onNodeWithText("Укажите целое число минут больше нуля").assertIsDisplayed()
        assertNull(viewModel.state.value.saveResult)
    }

    @Test
    fun validFormProducesTrimmedResultWithIndividualDurations() {
        val viewModel = TableSettingsViewModel(configuredTable())
        setViewModelContent(viewModel)

        editField(TableSettingsTestTags.NAME, "  Терраса  ")
        composeRule.onNodeWithTag(TableSettingsTestTags.CIRCLE_SHAPE).performClick()
        editField(TableSettingsTestTags.passageDuration("passage-1"), "15")
        editField(TableSettingsTestTags.passageDuration("passage-2"), "45")
        composeRule.onNodeWithTag(TableSettingsTestTags.SAVE).performClick()

        val result = requireNotNull(viewModel.state.value.saveResult)
        assertEquals("Терраса", result.name)
        assertEquals(TableShape.CIRCLE, result.shape)
        assertEquals(listOf(15, 45), result.passages.map { it.durationMinutes })
    }

    @Test
    fun cancelUsesDedicatedCallbackWithoutSaving() {
        val viewModel = TableSettingsViewModel(configuredTable())
        var cancelled = false
        setViewModelContent(viewModel, onCancel = { cancelled = true })

        editField(TableSettingsTestTags.NAME, "Изменение")
        composeRule.onNodeWithTag(TableSettingsTestTags.CANCEL).performClick()

        assertTrue(cancelled)
        assertNull(viewModel.state.value.saveResult)
    }

    @Test
    fun nameIsEditedInDedicatedDialog() {
        setViewModelContent(TableSettingsViewModel(configuredTable()))

        val nameValue = composeRule.onNodeWithTag(TableSettingsTestTags.NAME)
        nameValue.performClick()

        composeRule.onNodeWithTag(TableSettingsTestTags.EDITOR_DIALOG).assertIsDisplayed()
        val editorField = composeRule.onNodeWithTag(TableSettingsTestTags.EDITOR_FIELD)
        editorField.assertIsDisplayed()
        waitForEditorFocus()
        editorField
            .assertIsFocused()
            .assertTextContains("VIP")
            .performTextReplacement("Терраса")
        composeRule.onNodeWithTag(TableSettingsTestTags.EDITOR_DONE).performClick()

        composeRule.onNodeWithTag(TableSettingsTestTags.EDITOR_DIALOG).assertDoesNotExist()
        nameValue.assertIsDisplayed().assertTextContains("Терраса")
    }

    @Test
    fun durationIsEditedInDedicatedDialog() {
        setViewModelContent(TableSettingsViewModel(configuredTable()))

        composeRule.onNodeWithTag(TableSettingsTestTags.passageDuration("passage-1"))
            .performScrollTo()
            .performClick()

        val editorField = composeRule.onNodeWithTag(TableSettingsTestTags.EDITOR_FIELD)
        editorField.assertIsDisplayed()
        waitForEditorFocus()
        editorField
            .assertIsFocused()
            .assertTextContains("20", substring = true)
            .performTextReplacement("25")
        composeRule.onNodeWithTag(TableSettingsTestTags.EDITOR_DONE).performClick()

        composeRule.onNodeWithTag(TableSettingsTestTags.passageDuration("passage-1"))
            .assertTextContains("25 мин")
    }

    @Test
    fun firstAndLastFieldsCanBeEditedWhileTopBarActionsStayAvailable() {
        val table = configuredTable().copy(
            passages = List(8) { index ->
                TablePassage(id = "passage-${index + 1}", durationMinutes = 30)
            },
        )
        setViewModelContent(TableSettingsViewModel(table))

        editField(TableSettingsTestTags.NAME, "Большой зал")
        editField(TableSettingsTestTags.passageDuration("passage-8"), "45")

        composeRule.onNodeWithTag(TableSettingsTestTags.SAVE).assertIsDisplayed()
        composeRule.onNodeWithTag(TableSettingsTestTags.CANCEL).assertIsDisplayed()
    }

    @Test
    fun tabletWidthUsesRightSidePanelWithCompactPassageRows() {
        val viewModel = TableSettingsViewModel(configuredTable())
        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            HookahTimerTheme {
                Box(
                    modifier = Modifier
                        .requiredWidth(600.dp)
                        .height(800.dp),
                ) {
                    TableSettingsScreen(
                        state = state,
                        onAction = viewModel::onAction,
                        onCancel = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeRule.onNodeWithTag(TableSettingsTestTags.SIDE_PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(TableSettingsTestTags.BOTTOM_PANEL).assertDoesNotExist()
        val passageBounds = composeRule
            .onNodeWithTag(TableSettingsTestTags.passageDuration("passage-1"))
            .fetchSemanticsNode().boundsInRoot
        val deleteBounds = composeRule
            .onNodeWithTag(TableSettingsTestTags.deletePassage("passage-1"))
            .fetchSemanticsNode().boundsInRoot
        assertEquals(passageBounds.center.y, deleteBounds.center.y, 2f)
    }

    @Test
    fun compactWidthUsesBottomPanel() {
        val viewModel = TableSettingsViewModel(configuredTable())
        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            HookahTimerTheme {
                Box(
                    modifier = Modifier
                        .requiredWidth(599.dp)
                        .height(800.dp),
                ) {
                    TableSettingsScreen(
                        state = state,
                        onAction = viewModel::onAction,
                        onCancel = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeRule.onNodeWithTag(TableSettingsTestTags.BOTTOM_PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(TableSettingsTestTags.SIDE_PANEL).assertDoesNotExist()
        composeRule.onNodeWithTag(TableSettingsTestTags.SAVE).assertIsDisplayed()
        composeRule.onNodeWithTag(TableSettingsTestTags.CANCEL).assertIsDisplayed()
    }

    private fun editField(triggerTag: String, value: String) {
        composeRule.onNodeWithTag(triggerTag)
            .performScrollTo()
            .performClick()
        val editorField = composeRule.onNodeWithTag(TableSettingsTestTags.EDITOR_FIELD)
        editorField.assertIsDisplayed()
        waitForEditorFocus()
        editorField.performTextReplacement(value)
        composeRule.onNodeWithTag(TableSettingsTestTags.EDITOR_DONE).performClick()
    }

    private fun waitForEditorFocus() {
        val editorField = composeRule.onNodeWithTag(TableSettingsTestTags.EDITOR_FIELD)
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching { editorField.assertIsFocused() }.isSuccess
        }
    }

    private fun setViewModelContent(
        viewModel: TableSettingsViewModel,
        onCancel: () -> Unit = {},
    ) {
        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            HookahTimerTheme {
                TableSettingsScreen(
                    state = state,
                    onAction = viewModel::onAction,
                    onCancel = onCancel,
                )
            }
        }
    }

    private fun configuredTable(): HallTable = HallTable(
        id = "table-1",
        name = "VIP",
        shape = TableShape.PILL,
        passages = listOf(
            TablePassage(id = "passage-1", durationMinutes = 20),
            TablePassage(id = "passage-2", durationMinutes = 40),
        ),
    )
}
