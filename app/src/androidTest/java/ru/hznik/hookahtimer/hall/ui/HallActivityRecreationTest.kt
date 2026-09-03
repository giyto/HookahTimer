package ru.hznik.hookahtimer.hall.ui

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import ru.hznik.hookahtimer.HookahTimerApplication
import ru.hznik.hookahtimer.MainActivity
import ru.hznik.hookahtimer.hall.settings.ui.TableSettingsTestTags

class HallActivityRecreationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun clearPersistentHall() {
        runBlocking {
            (composeRule.activity.application as HookahTimerApplication)
                .database
                .clearAllTables()
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("Столов пока нет")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun activityRecreationKeepsTablesInViewModel() {
        performHallAction(HallTestTags.TOGGLE_EDIT_MODE)
        performHallAction(HallTestTags.ADD_TABLE)
        waitForText("Стол 1")
        composeRule.onNodeWithText("Стол 1").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithText("Стол 1").assertIsDisplayed()
        composeRule.onNodeWithTag(HallTestTags.EDIT_MODE_INDICATOR).assertIsDisplayed()
    }

    @Test
    fun activityRecreationKeepsOpenSettingsDraft() {
        performHallAction(HallTestTags.TOGGLE_EDIT_MODE)
        performHallAction(HallTestTags.ADD_TABLE)
        waitForText("Стол 1")
        val firstPassageId = findFirstPassageId()
        composeRule.onNodeWithText("Стол 1").performClick()
        editName("Черновик")
        composeRule.onNodeWithTag(TableSettingsTestTags.PILL_SHAPE).performClick()
        editDuration(firstPassageId, "45")

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag(TableSettingsTestTags.NAME).assertTextContains("Черновик")
        composeRule.onNodeWithTag(TableSettingsTestTags.PILL_SHAPE).assertIsSelected()
        composeRule.onNodeWithTag(TableSettingsTestTags.passageDuration(firstPassageId))
            .assertTextContains("45 мин")
    }

    @Test
    fun activityRecreationRestoresRunningTimerFromRoom() {
        performHallAction(HallTestTags.TOGGLE_EDIT_MODE)
        performHallAction(HallTestTags.ADD_TABLE)
        waitForText("Стол 1")
        val tableId = findOnlyTableId()
        performHallAction(HallTestTags.TOGGLE_EDIT_MODE)
        composeRule.onNodeWithTag(HallTestTags.table(tableId)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag(
                HallTestTags.tableTimer(tableId),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(
            HallTestTags.tableTimer(tableId),
            useUnmergedTree = true,
        ).assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag(
            HallTestTags.tableTimer(tableId),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun hallAndSettingsOverlayKeepScreenOn() {
        val activityBeforeBackground = composeRule.activity
        assertTrue(activityBeforeBackground.window.hasKeepScreenOnFlag())

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertFalse(activityBeforeBackground.window.hasKeepScreenOnFlag())
        }

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.activityRule.scenario.onActivity { activity ->
            assertTrue(activity.window.hasKeepScreenOnFlag())
        }
        performHallAction(HallTestTags.TOGGLE_EDIT_MODE)
        performHallAction(HallTestTags.ADD_TABLE)
        waitForText("Стол 1")
        composeRule.onNodeWithText("Стол 1").performClick()
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.onActivity { activity ->
            assertTrue(activity.window.hasKeepScreenOnFlag())
        }
        composeRule.onNodeWithTag(TableSettingsTestTags.CANCEL).performClick()
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.onActivity { activity ->
            assertTrue(activity.window.hasKeepScreenOnFlag())
        }
    }

    @Test
    fun activityRecreationKeepsFullscreenSessionChoice() {
        performHallAction(HallTestTags.TOGGLE_FULLSCREEN)
        openActionMenu()
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_FULLSCREEN)
            .assertContentDescriptionEquals("Выйти из полного экрана")

        composeRule.activityRule.scenario.recreate()

        openActionMenu()
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_FULLSCREEN)
            .assertContentDescriptionEquals("Выйти из полного экрана")
    }

    @Test
    fun activityRecreationKeepsViewportLockSessionChoice() {
        performHallAction(HallTestTags.TOGGLE_VIEWPORT_LOCK)
        composeRule.onNodeWithTag(HallTestTags.VIEWPORT_LOCKED_INDICATOR).assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag(HallTestTags.VIEWPORT_LOCKED_INDICATOR).assertIsDisplayed()
        assertTrue(
            composeRule.onNodeWithTag(HallTestTags.CANVAS_GRID)
                .fetchSemanticsNode().config[CanvasLockedKey],
        )
    }

    @Test
    fun activityRecreationKeepsActiveDurationEditor() {
        performHallAction(HallTestTags.TOGGLE_EDIT_MODE)
        performHallAction(HallTestTags.ADD_TABLE)
        waitForText("Стол 1")
        val firstPassageId = findFirstPassageId()
        composeRule.onNodeWithText("Стол 1").performClick()
        composeRule.onNodeWithTag(TableSettingsTestTags.passageDuration(firstPassageId))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(TableSettingsTestTags.EDITOR_FIELD)
            .performTextReplacement("45")

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag(TableSettingsTestTags.EDITOR_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(TableSettingsTestTags.EDITOR_FIELD)
            .assertTextContains("45")
    }

    @Test
    fun thirtyMixedTablesRestoreWithoutDuplicates() {
        val application = composeRule.activity.application as HookahTimerApplication
        val tableIds = List(30) { index -> "load-table-$index" }
        runBlocking {
            tableIds.forEachIndexed { index, tableId ->
                application.hallRepository.addTable(
                    tableId = tableId,
                    passageIds = listOf("$tableId-p1", "$tableId-p2"),
                )
                when (index % 4) {
                    1 -> application.hallRepository.advanceTimer(
                        tableId,
                        System.currentTimeMillis(),
                    )

                    2 -> application.hallRepository.advanceTimer(tableId, 0L)
                    3 -> repeat(3) {
                        application.hallRepository.advanceTimer(tableId, 0L)
                    }
                }
            }
        }
        waitForUniqueTableNodes(tableIds)

        composeRule.activityRule.scenario.recreate()

        waitForUniqueTableNodes(tableIds)
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openActionMenu() {
        composeRule.onNodeWithTag(HallTestTags.ACTION_MENU).performClick()
        composeRule.waitForIdle()
    }

    private fun performHallAction(testTag: String) {
        openActionMenu()
        composeRule.onNodeWithTag(testTag).performClick()
        composeRule.waitForIdle()
    }

    private fun findOnlyTableId(): String {
        val application = composeRule.activity.application as HookahTimerApplication
        return runBlocking {
            application.hallRepository.tables.first().single().id
        }
    }

    private fun findFirstPassageId(): String {
        val application = composeRule.activity.application as HookahTimerApplication
        return runBlocking {
            application.hallRepository.tables.first().single().passages.first().id
        }
    }

    private fun editName(value: String) {
        composeRule.onNodeWithTag(TableSettingsTestTags.NAME)
            .performScrollTo()
            .performClick()
            .performTextReplacement(value)
    }

    private fun editDuration(passageId: String, value: String) {
        waitForDurationEditorToClose()
        composeRule.onNodeWithTag(TableSettingsTestTags.passageDuration(passageId))
            .performScrollTo()
            .performClick()
        val editorField = composeRule.onNodeWithTag(TableSettingsTestTags.EDITOR_FIELD)
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching { editorField.assertIsDisplayed() }.isSuccess
        }
        editorField.performTextReplacement(value)
        composeRule.onNodeWithTag(TableSettingsTestTags.EDITOR_DONE).performClick()
        waitForDurationEditorToClose()
    }

    private fun waitForDurationEditorToClose() {
        val editorDialog = composeRule.onNodeWithTag(TableSettingsTestTags.EDITOR_DIALOG)
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching { editorDialog.assertDoesNotExist() }.isSuccess
        }
    }

    private fun waitForUniqueTableNodes(tableIds: List<String>) {
        composeRule.waitUntil(timeoutMillis = 15_000L) {
            tableIds.all { tableId ->
                composeRule.onAllNodesWithTag(HallTestTags.table(tableId))
                    .fetchSemanticsNodes().size == 1
            }
        }
    }

    private fun android.view.Window.hasKeepScreenOnFlag(): Boolean =
        attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
}
