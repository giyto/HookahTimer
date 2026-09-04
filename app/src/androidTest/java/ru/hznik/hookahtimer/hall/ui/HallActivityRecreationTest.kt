package ru.hznik.hookahtimer.hall.ui

import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeTimeoutException
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
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    @Test
    fun openHookahWindowSurvivesRecreationAndBackgroundWithoutLosingViewportLock() {
        val application = composeRule.activity.application as HookahTimerApplication
        runBlocking {
            application.hallRepository.addTable("multi", listOf("p1", "p2"))
            application.hallRepository.advanceTimer("multi", System.currentTimeMillis())
            application.hallRepository.addHookah("multi", "h2", System.currentTimeMillis())
        }
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTag(HallTestTags.table("multi")).fetchSemanticsNodes().isNotEmpty()
        }
        performHallAction(HallTestTags.TOGGLE_FULLSCREEN)
        performHallAction(HallTestTags.TOGGLE_VIEWPORT_LOCK)
        assertFullscreenSelected()
        val beforeViewport = awaitStableViewport("before opening hookahs")
        assertTrue(beforeViewport.locked)
        assertImmersiveBarsHidden(beforeViewport, "before opening hookahs")
        val beforeTables = runBlocking { application.hallRepository.tables.first() }
        composeRule.onNodeWithTag(HallTestTags.table("multi")).performClick()
        composeRule.onNodeWithTag(HookahTestTags.OVERLAY).assertIsDisplayed()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag(HookahTestTags.OVERLAY).assertIsDisplayed()
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.onNodeWithTag(HookahTestTags.OVERLAY).assertIsDisplayed()
        org.junit.Assert.assertEquals(beforeTables, runBlocking { application.hallRepository.tables.first() })
        composeRule.onNodeWithTag(HookahTestTags.CLOSE).performClick()
        assertFullscreenSelected()
        val afterViewport = awaitStableViewport("after recreation and backgrounding")
        assertImmersiveBarsHidden(afterViewport, "after recreation and backgrounding")
        org.junit.Assert.assertEquals(
            "Canvas bounds, scale, offsets, lock and immersive bars must survive recreation and backgrounding",
            beforeViewport,
            afterViewport,
        )
    }

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

    private data class ViewportSnapshot(
        val bounds: Rect,
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float,
        val locked: Boolean,
        val immersiveBars: ImmersiveBarsSnapshot,
    )

    private data class ImmersiveBarsSnapshot(
        val statusVisible: Boolean,
        val navigationVisible: Boolean,
        val insets: Insets,
    )

    private fun assertImmersiveBarsHidden(snapshot: ViewportSnapshot, phase: String) {
        assertFalse("Status bar must be hidden ($phase): $snapshot", snapshot.immersiveBars.statusVisible)
        assertFalse("Navigation bar must be hidden ($phase): $snapshot", snapshot.immersiveBars.navigationVisible)
    }

    private fun assertFullscreenSelected() {
        openActionMenu()
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_FULLSCREEN)
            .assertContentDescriptionEquals("Выйти из полного экрана")
        composeRule.onNodeWithTag(HallTestTags.ACTION_MENU).performClick()
        composeRule.waitForIdle()
    }

    private fun awaitStableViewport(phase: String): ViewportSnapshot {
        var lastSnapshot: ViewportSnapshot? = null
        var stableSince = SystemClock.uptimeMillis()
        var diagnostics = "No samples collected"
        var samples = 0
        // Compose idleness alone does not wait for Android's system-bar inset animation.
        // Fullscreen selection and status/navigation visibility are asserted separately.
        // Caption visibility is independent of immersive mode; it is diagnostic only.
        // Its actual effect on canvas bounds still participates in exact comparison.
        try {
            composeRule.waitUntil(timeoutMillis = 15_000L) {
                val (hasWindowFocus, immersiveBars, windowDiagnostics) = composeRule.runOnIdle {
                    val decorView = composeRule.activity.window.decorView
                    val insets = ViewCompat.getRootWindowInsets(decorView)
                    val visibleBars = insets?.let {
                        // Android can report old animated sizes even for hidden bars.
                        // Only visible bars occupy space; keep raw sizes in diagnostics.
                        val visibleTypes = intArrayOf(
                            WindowInsetsCompat.Type.statusBars(),
                            WindowInsetsCompat.Type.navigationBars(),
                        ).filter { type -> it.isVisible(type) }.fold(0) { mask, type -> mask or type }
                        ImmersiveBarsSnapshot(
                            statusVisible = it.isVisible(WindowInsetsCompat.Type.statusBars()),
                            navigationVisible = it.isVisible(WindowInsetsCompat.Type.navigationBars()),
                            insets = it.getInsets(visibleTypes),
                        )
                    }
                    Triple(
                        decorView.hasWindowFocus(),
                        visibleBars,
                        insets?.let {
                            "captionVisible=${it.isVisible(WindowInsetsCompat.Type.captionBar())}, " +
                                "captionInsets=${it.getInsets(WindowInsetsCompat.Type.captionBar())}, " +
                                "rawBarInsets=${it.getInsets(WindowInsetsCompat.Type.systemBars())}"
                        },
                    )
                }
                val nodes = composeRule.onAllNodesWithTag(HallTestTags.CANVAS_GRID)
                    .fetchSemanticsNodes()
                val node = nodes.singleOrNull()
                val snapshot = if (immersiveBars != null && node != null &&
                    node.boundsInRoot.width > 0f && node.boundsInRoot.height > 0f
                ) {
                    ViewportSnapshot(
                        bounds = node.boundsInRoot,
                        scale = node.config[CanvasScaleKey],
                        offsetX = node.config[CanvasOffsetXKey],
                        offsetY = node.config[CanvasOffsetYKey],
                        locked = node.config[CanvasLockedKey],
                        immersiveBars = immersiveBars,
                    )
                } else {
                    null
                }
                val now = SystemClock.uptimeMillis()
                if (snapshot == null || snapshot != lastSnapshot) {
                    lastSnapshot = snapshot
                    stableSince = now
                }
                samples += 1
                diagnostics = "samples=$samples, focus=$hasWindowFocus, nodes=${nodes.size}, " +
                    "bounds=${node?.boundsInRoot}, bars=$immersiveBars, $windowDiagnostics, " +
                    "stableFor=${now - stableSince}ms, viewport=$snapshot"
                snapshot != null && now - stableSince >= 500L
            }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("Viewport did not settle ($phase): $diagnostics", timeout)
        }
        return checkNotNull(lastSnapshot)
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
