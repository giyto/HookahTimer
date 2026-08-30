package ru.hznik.hookahtimer

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.hznik.hookahtimer.hall.data.InMemoryHallRepository
import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.TablePassage
import ru.hznik.hookahtimer.hall.model.TableShape
import ru.hznik.hookahtimer.hall.model.TableTimerState
import ru.hznik.hookahtimer.hall.model.TimeProvider
import ru.hznik.hookahtimer.hall.presentation.HallAction
import ru.hznik.hookahtimer.hall.presentation.HallViewModel
import ru.hznik.hookahtimer.hall.settings.ui.TableSettingsTestTags
import ru.hznik.hookahtimer.hall.ui.HallTestTags
import ru.hznik.hookahtimer.test.dispatchActivityBack
import ru.hznik.hookahtimer.ui.theme.HookahTimerTheme
import ru.hznik.hookahtimer.window.TabletWindowController

class HookahTimerAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun missingTableRouteSafelyReturnsToHall() {
        val viewModel = HallViewModel(repository = InMemoryHallRepository())
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
        editSettingValue(TableSettingsTestTags.NAME, "VIP")
        composeRule.onNodeWithTag(TableSettingsTestTags.PILL_SHAPE).performClick()
        editSettingValue(TableSettingsTestTags.passageDuration("passage-1"), "15")
        editSettingValue(TableSettingsTestTags.passageDuration("passage-2"), "45")
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
        setAppContent(viewModel)
        composeRule.waitForIdle()
        val original = viewModel.state.value.tables.single()

        composeRule.onNodeWithTag(HallTestTags.table("table-1")).performClick()
        editSettingValue(TableSettingsTestTags.NAME, "Не сохранять")
        composeRule.onNodeWithTag(TableSettingsTestTags.CANCEL).performClick()

        composeRule.onNodeWithText("Стол 1").assertIsDisplayed()
        assertEquals(original, viewModel.state.value.tables.single())
    }

    @Test
    fun systemBackLeavesOriginalTableUntouched() {
        val viewModel = configuredHallViewModel()
        setAppContent(viewModel)
        composeRule.waitForIdle()
        val original = viewModel.state.value.tables.single()

        composeRule.onNodeWithTag(HallTestTags.table("table-1")).performClick()
        editSettingValue(TableSettingsTestTags.NAME, "Не сохранять")
        dispatchActivityBack()

        composeRule.onNodeWithText("Стол 1").assertIsDisplayed()
        assertEquals(original, viewModel.state.value.tables.single())
    }

    @Test
    fun activeTableSettingsRouteSafelyReturnsToHall() {
        val passages = configuredPassages()
        val table = HallTable(
            id = "table-1",
            name = "Стол 1",
            passages = passages,
            timerState = TableTimerState.Running("passage-1", 1_801_000L),
        )
        val viewModel = HallViewModel(
            repository = InMemoryHallRepository(initialTables = listOf(table)),
            timeProvider = TimeProvider { 1_000L },
        )
        viewModel.onAction(HallAction.ToggleEditMode)

        setAppContent(
            viewModel = viewModel,
            startDestination = tableSettingsRoute("table-1"),
        )

        composeRule.onNodeWithText("Зал").assertIsDisplayed()
        composeRule.onNodeWithTag(TableSettingsTestTags.NAME).assertDoesNotExist()
    }

    @Test
    fun windowControllerReleasesSettingsAndRestoresHallFullscreenChoice() {
        val viewModel = configuredHallViewModel()
        val controller = RecordingWindowController()
        setAppContent(viewModel, tabletWindowController = controller)

        composeRule.waitForIdle()
        assertEquals("hall:false", controller.calls.last())

        composeRule.onNodeWithTag(HallTestTags.TOGGLE_FULLSCREEN).performClick()
        composeRule.waitForIdle()
        assertEquals("hall:true", controller.calls.last())

        composeRule.onNodeWithTag(HallTestTags.table("table-1")).performClick()
        composeRule.waitForIdle()
        assertEquals("leave", controller.calls.last())

        composeRule.onNodeWithTag(TableSettingsTestTags.CANCEL).performClick()
        composeRule.waitForIdle()
        assertEquals("hall:true", controller.calls.last())
    }

    private fun configuredHallViewModel(): HallViewModel {
        return HallViewModel(
            repository = InMemoryHallRepository(
                initialTables = listOf(
                    HallTable(
                        id = "table-1",
                        name = "Стол 1",
                        passages = configuredPassages(),
                    ),
                ),
            ),
        ).also { viewModel ->
            viewModel.onAction(HallAction.ToggleEditMode)
        }
    }

    private fun configuredPassages(): List<TablePassage> = listOf(
        TablePassage("passage-1", 30),
        TablePassage("passage-2", 30),
    )

    private fun editSettingValue(triggerTag: String, value: String) {
        composeRule.onNodeWithTag(triggerTag)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(TableSettingsTestTags.EDITOR_FIELD)
            .assertIsDisplayed()
            .performTextReplacement(value)
        composeRule.onNodeWithTag(TableSettingsTestTags.EDITOR_DONE).performClick()
    }

    private fun setAppContent(
        viewModel: HallViewModel,
        startDestination: String = HALL_ROUTE,
        tabletWindowController: TabletWindowController? = null,
    ) {
        composeRule.setContent {
            HookahTimerTheme {
                HookahTimerApp(
                    hallViewModel = viewModel,
                    startDestination = startDestination,
                    tabletWindowController = tabletWindowController
                        ?: ru.hznik.hookahtimer.window.NoOpTabletWindowController,
                )
            }
        }
    }

    private class RecordingWindowController : TabletWindowController {
        val calls = mutableListOf<String>()

        override fun showHall(isFullscreen: Boolean) {
            calls += "hall:$isFullscreen"
        }

        override fun leaveHall() {
            calls += "leave"
        }
    }
}
