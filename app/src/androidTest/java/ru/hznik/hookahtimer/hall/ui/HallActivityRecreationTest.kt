package ru.hznik.hookahtimer.hall.ui

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
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
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_EDIT_MODE).performClick()
        composeRule.onNodeWithTag(HallTestTags.ADD_TABLE).performClick()
        waitForText("Стол 1")
        composeRule.onNodeWithText("Стол 1").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithText("Стол 1").assertIsDisplayed()
        composeRule.onNodeWithTag(HallTestTags.EDIT_MODE_INDICATOR).assertIsDisplayed()
    }

    @Test
    fun activityRecreationKeepsOpenSettingsDraft() {
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_EDIT_MODE).performClick()
        composeRule.onNodeWithTag(HallTestTags.ADD_TABLE).performClick()
        waitForText("Стол 1")
        composeRule.onNodeWithText("Стол 1").performClick()
        composeRule.onNodeWithTag(TableSettingsTestTags.NAME)
            .performTextReplacement("Черновик")
        composeRule.onNodeWithTag(TableSettingsTestTags.PILL_SHAPE).performClick()
        composeRule.onAllNodesWithText("30").onFirst().performTextReplacement("45")

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag(TableSettingsTestTags.NAME).assertTextContains("Черновик")
        composeRule.onNodeWithTag(TableSettingsTestTags.PILL_SHAPE).assertIsSelected()
        composeRule.onNodeWithText("45").assertExists()
    }

    @Test
    fun activityRecreationRestoresRunningTimerFromRoom() {
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_EDIT_MODE).performClick()
        composeRule.onNodeWithTag(HallTestTags.ADD_TABLE).performClick()
        waitForText("Стол 1")
        val tableId = findOnlyTableId()
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_EDIT_MODE).performClick()
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
    fun hallKeepsScreenOnButSettingsReleaseWindowFlag() {
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
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_EDIT_MODE).performClick()
        composeRule.onNodeWithTag(HallTestTags.ADD_TABLE).performClick()
        waitForText("Стол 1")
        composeRule.onNodeWithText("Стол 1").performClick()
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.onActivity { activity ->
            assertFalse(activity.window.hasKeepScreenOnFlag())
        }
        composeRule.onNodeWithTag(TableSettingsTestTags.CANCEL).performClick()
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.onActivity { activity ->
            assertTrue(activity.window.hasKeepScreenOnFlag())
        }
    }

    @Test
    fun activityRecreationKeepsFullscreenSessionChoice() {
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_FULLSCREEN).performClick()
        composeRule.onNodeWithText("Выйти из полного экрана").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithText("Выйти из полного экрана").assertIsDisplayed()
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

    private fun findOnlyTableId(): String {
        val application = composeRule.activity.application as HookahTimerApplication
        return runBlocking {
            application.hallRepository.tables.first().single().id
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
