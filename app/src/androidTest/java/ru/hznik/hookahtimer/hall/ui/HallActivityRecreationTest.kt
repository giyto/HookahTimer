package ru.hznik.hookahtimer.hall.ui

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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
}
