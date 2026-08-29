package ru.hznik.hookahtimer

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.hznik.hookahtimer.hall.model.TableShape
import ru.hznik.hookahtimer.hall.presentation.HallAction
import ru.hznik.hookahtimer.hall.presentation.HallViewModel
import ru.hznik.hookahtimer.hall.settings.ui.TableSettingsTestTags
import ru.hznik.hookahtimer.hall.ui.HallTestTags
import ru.hznik.hookahtimer.ui.theme.HookahTimerTheme

class HookahTimerAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun missingTableRouteSafelyReturnsToHall() {
        val viewModel = HallViewModel()
        setAppContent(
            viewModel = viewModel,
            startDestination = tableSettingsRoute("missing"),
        )

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Зал").assertIsDisplayed()
    }

    @Test
    fun successfulSettingsSaveUpdatesHallTable() {
        val viewModel = configuredHallViewModel()
        setAppContent(viewModel)

        composeRule.onNodeWithTag(HallTestTags.table("table-1")).performClick()
        composeRule.onNodeWithTag(TableSettingsTestTags.NAME).performTextReplacement("VIP")
        composeRule.onNodeWithTag(TableSettingsTestTags.PILL_SHAPE).performClick()
        composeRule.onNodeWithTag(TableSettingsTestTags.passageDuration("passage-1"))
            .performTextReplacement("15")
        composeRule.onNodeWithTag(TableSettingsTestTags.passageDuration("passage-2"))
            .performTextReplacement("45")
        composeRule.onNodeWithTag(TableSettingsTestTags.SAVE).performClick()

        composeRule.onNodeWithText("VIP").assertIsDisplayed()
        composeRule.onNodeWithTag(HallTestTags.table("table-1"))
            .assertContentDescriptionEquals("Pill-стол VIP")
        val table = viewModel.state.value.tables.single()
        assertEquals(TableShape.PILL, table.shape)
        assertEquals(listOf(15, 45), table.passages.map { it.durationMinutes })
    }

    @Test
    fun cancelLeavesOriginalTableUntouched() {
        val viewModel = configuredHallViewModel()
        val original = viewModel.state.value.tables.single()
        setAppContent(viewModel)

        composeRule.onNodeWithTag(HallTestTags.table("table-1")).performClick()
        composeRule.onNodeWithTag(TableSettingsTestTags.NAME)
            .performTextReplacement("Не сохранять")
        composeRule.onNodeWithTag(TableSettingsTestTags.CANCEL).performClick()

        composeRule.onNodeWithText("Стол 1").assertIsDisplayed()
        assertEquals(original, viewModel.state.value.tables.single())
    }

    @Test
    fun systemBackLeavesOriginalTableUntouched() {
        val viewModel = configuredHallViewModel()
        val original = viewModel.state.value.tables.single()
        setAppContent(viewModel)

        composeRule.onNodeWithTag(HallTestTags.table("table-1")).performClick()
        composeRule.onNodeWithTag(TableSettingsTestTags.NAME)
            .performTextReplacement("Не сохранять")
        closeSoftKeyboard()
        pressBack()

        composeRule.onNodeWithText("Стол 1").assertIsDisplayed()
        assertEquals(original, viewModel.state.value.tables.single())
    }

    private fun configuredHallViewModel(): HallViewModel {
        val passageIds = ArrayDeque(listOf("passage-1", "passage-2"))
        return HallViewModel(
            idFactory = { "table-1" },
            passageIdFactory = { passageIds.removeFirst() },
        ).also { viewModel ->
            viewModel.onAction(HallAction.ToggleEditMode)
            viewModel.onAction(HallAction.AddTable)
        }
    }

    private fun setAppContent(
        viewModel: HallViewModel,
        startDestination: String = HALL_ROUTE,
    ) {
        composeRule.setContent {
            HookahTimerTheme {
                HookahTimerApp(
                    hallViewModel = viewModel,
                    startDestination = startDestination,
                )
            }
        }
    }
}
