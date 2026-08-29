package ru.hznik.hookahtimer.hall.settings.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
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
        composeRule.onNodeWithText("Проходка 1").assertIsDisplayed()
        composeRule.onNodeWithText("Проходка 2").assertIsDisplayed()
        composeRule.onNodeWithTag(TableSettingsTestTags.passageDuration("passage-1"))
            .assertTextContains("20")
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

        composeRule.onNodeWithTag(TableSettingsTestTags.NAME).performTextReplacement("   ")
        composeRule.onNodeWithTag(TableSettingsTestTags.passageDuration("passage-1"))
            .performTextReplacement("0")
        composeRule.onNodeWithTag(TableSettingsTestTags.SAVE).performClick()

        composeRule.onNodeWithText("Введите название стола").assertIsDisplayed()
        composeRule.onNodeWithText("Укажите целое число минут больше нуля").assertIsDisplayed()
        assertNull(viewModel.state.value.saveResult)
    }

    @Test
    fun validFormProducesTrimmedResultWithIndividualDurations() {
        val viewModel = TableSettingsViewModel(configuredTable())
        setViewModelContent(viewModel)

        composeRule.onNodeWithTag(TableSettingsTestTags.NAME)
            .performTextReplacement("  Терраса  ")
        composeRule.onNodeWithTag(TableSettingsTestTags.CIRCLE_SHAPE).performClick()
        composeRule.onNodeWithTag(TableSettingsTestTags.passageDuration("passage-1"))
            .performTextReplacement("15")
        composeRule.onNodeWithTag(TableSettingsTestTags.passageDuration("passage-2"))
            .performTextReplacement("45")
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

        composeRule.onNodeWithTag(TableSettingsTestTags.NAME)
            .performTextReplacement("Изменение")
        composeRule.onNodeWithTag(TableSettingsTestTags.CANCEL).performClick()

        assertTrue(cancelled)
        assertNull(viewModel.state.value.saveResult)
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
