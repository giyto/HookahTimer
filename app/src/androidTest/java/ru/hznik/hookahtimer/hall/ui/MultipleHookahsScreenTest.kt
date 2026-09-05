package ru.hznik.hookahtimer.hall.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.hznik.hookahtimer.hall.data.InMemoryHallRepository
import ru.hznik.hookahtimer.hall.model.*
import ru.hznik.hookahtimer.hall.presentation.HallAction
import ru.hznik.hookahtimer.hall.presentation.HallViewModel
import ru.hznik.hookahtimer.test.dispatchActivityBack
import ru.hznik.hookahtimer.ui.theme.HookahTimerTheme

class MultipleHookahsScreenTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var inputModeManager: InputModeManager

    private fun base() = HallTable("t", "VIP", position = CanvasPosition(160f, 140f),
        passages = listOf(TablePassage("p1", 2), TablePassage("p2", 3)))
    private fun active() = base().withAddedHookah("h1", 0L)
    private fun multiple(count: Int = 2): HallTable {
        var table = active()
        for (number in 2..count) table = table.withAddedHookah("h$number", number * 100L)
        return table
    }

    private fun show(
        table: HallTable,
        isEditing: Boolean = false,
        nowEpochMillis: Long = 1_000L,
        hapticFeedback: HapticFeedback? = null,
    ): HallViewModel {
        var nextId = 10
        val vm = HallViewModel(InMemoryHallRepository(listOf(table)), TimeProvider { nowEpochMillis },
            hookahIdFactory = { "added-${nextId++}" })
        if (isEditing) vm.onAction(HallAction.ToggleEditMode)
        compose.setContent {
            val currentInputModeManager = LocalInputModeManager.current
            val platformHapticFeedback = LocalHapticFeedback.current
            SideEffect { inputModeManager = currentInputModeManager }
            val state by vm.state.collectAsState()
            CompositionLocalProvider(
                LocalHapticFeedback provides (hapticFeedback ?: platformHapticFeedback),
            ) {
                HookahTimerTheme {
                    HallScreen(state, vm::onAction, timeProvider = vm.timeProvider)
                }
            }
        }
        compose.waitUntil(5_000L) {
            compose.onAllNodesWithTag(HallTestTags.table("t")).fetchSemanticsNodes().isNotEmpty()
        }
        return vm
    }

    @Test fun holdAndReleaseAddsExactlyOneWithoutTapOrViewportChange() {
        val hapticFeedback = RecordingHapticFeedback()
        val vm = show(active(), hapticFeedback = hapticFeedback)
        val original = vm.state.value.tables.single()
        val viewport = compose.onNodeWithTag(HallTestTags.CANVAS_GRID).fetchSemanticsNode().config
        compose.onNodeWithTag(HallTestTags.table("t")).performTouchInput { longClick(durationMillis = 1_500L) }
        compose.waitUntil(5_000L) { vm.state.value.tables.single().hookahs.size == 2 }
        val current = vm.state.value.tables.single()
        assertEquals(original.hookahs[0], current.hookahs[0])
        assertEquals(original.position, current.position)
        assertNull(vm.state.value.selectedHookahTableId)
        val after = compose.onNodeWithTag(HallTestTags.CANVAS_GRID).fetchSemanticsNode().config
        assertEquals(viewport[CanvasScaleKey], after[CanvasScaleKey])
        assertEquals(viewport[CanvasOffsetXKey], after[CanvasOffsetXKey])
        assertEquals(viewport[CanvasOffsetYKey], after[CanvasOffsetYKey])
        compose.onNodeWithTag(HookahTestTags.tableCount("t"), useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("2")
        assertEquals(listOf(HapticFeedbackType.LongPress), hapticFeedback.events)
    }

    @Test fun recognizedHoldReleasesVisualBeforeFeedbackAndCommand() {
        val events = mutableListOf<String>()
        compose.setContent {
            Box(
                Modifier
                    .size(120.dp)
                    .testTag("hookah_hold_gesture")
                    .hookahTableGestures(
                        tableId = "gesture-test",
                        tapLabel = "Tap",
                        addLabel = "Hold",
                        onTap = { events += "tap" },
                        onHold = { events += "hold" },
                        onHoldFeedback = { events += "feedback" },
                        onPressed = { events += "pressed:$it" },
                    ),
            )
        }
        compose.onNodeWithTag("hookah_hold_gesture")
            .performTouchInput { longClick(durationMillis = 1_500L) }
        compose.runOnIdle {
            assertEquals(
                listOf("pressed:true", "pressed:false", "feedback", "hold", "pressed:false"),
                events,
            )
        }
    }

    @Test fun freeTableHoldStartsNumberOneAndSingleTapStillAdvancesDirectly() {
        val hapticFeedback = RecordingHapticFeedback()
        val vm = show(base(), hapticFeedback = hapticFeedback)
        compose.onNodeWithTag(HallTestTags.table("t")).performTouchInput { longClick() }
        compose.waitUntil(5_000L) { !vm.state.value.tables.single().isIdle }
        assertEquals(1, vm.state.value.tables.single().hookahs.size)
        assertEquals(listOf(HapticFeedbackType.LongPress), hapticFeedback.events)
        hapticFeedback.events.clear()
        compose.onNodeWithTag(HallTestTags.table("t")).performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(TableTimerState.Running("p2", 181_000L), vm.state.value.tables.single().timerState)
        assertNull(vm.state.value.selectedHookahTableId)
        assertTrue(hapticFeedback.events.isEmpty())
    }

    @Test fun movementCancelsHoldWithoutAddingOrAdvancing() {
        val hapticFeedback = RecordingHapticFeedback()
        val vm = show(active(), hapticFeedback = hapticFeedback)
        val before = vm.state.value.tables.single().hookahs
        compose.onNodeWithTag(HallTestTags.table("t")).performTouchInput {
            down(center)
            moveBy(Offset(70f, 0f))
            advanceEventTime(1_000L)
            up()
        }
        compose.waitForIdle()
        assertEquals(before, vm.state.value.tables.single().hookahs)
        assertTrue(hapticFeedback.events.isEmpty())
    }

    @Test fun cancelledTouchDoesNotAddOrAdvance() {
        val hapticFeedback = RecordingHapticFeedback()
        val vm = show(active(), hapticFeedback = hapticFeedback)
        val before = vm.state.value.tables.single()
        compose.onNodeWithTag(HallTestTags.table("t")).performTouchInput {
            down(center)
            advanceEventTime(100L)
            cancel()
        }
        compose.waitForIdle()
        assertEquals(before, vm.state.value.tables.single())
        assertNull(vm.state.value.selectedHookahTableId)
        assertTrue(hapticFeedback.events.isEmpty())
    }

    @Test fun secondFingerCancelsHoldEvenWhenViewportIsLocked() {
        val hapticFeedback = RecordingHapticFeedback()
        val vm = show(active(), hapticFeedback = hapticFeedback)
        compose.onNodeWithTag(HallTestTags.ACTION_MENU).performClick()
        compose.onNodeWithTag(HallTestTags.TOGGLE_VIEWPORT_LOCK).performClick()
        val before = vm.state.value.tables.single().hookahs
        compose.onNodeWithTag(HallTestTags.table("t")).performTouchInput {
            down(0, center)
            advanceEventTime(100L)
            down(1, center + Offset(12f, 0f))
            advanceEventTime(1_000L)
            up(1)
            up(0)
        }
        compose.waitForIdle()
        assertEquals(before, vm.state.value.tables.single().hookahs)
        assertTrue(hapticFeedback.events.isEmpty())
        compose.onNodeWithTag(HallTestTags.table("t")).performTouchInput { longClick() }
        compose.waitUntil(5_000L) { vm.state.value.tables.single().hookahs.size == 2 }
        assertTrue(compose.onNodeWithTag(HallTestTags.CANVAS_GRID).fetchSemanticsNode().config[CanvasLockedKey])
        assertEquals(listOf(HapticFeedbackType.LongPress), hapticFeedback.events)
    }

    @Test fun holdingInEditModeDoesNotAddHookah() {
        val hapticFeedback = RecordingHapticFeedback()
        val vm = show(active(), isEditing = true, hapticFeedback = hapticFeedback)
        compose.onNodeWithTag(HallTestTags.table("t")).performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(1, vm.state.value.tables.single().hookahs.size)
        assertNull(vm.state.value.selectedHookahTableId)
        assertTrue(hapticFeedback.events.isEmpty())
    }

    @Test fun gridHasTwoColumnsSquareCardsAndOnlySelectedHookahAdvances() {
        val vm = show(multiple(3))
        val before = vm.state.value.tables.single()
        compose.onNodeWithTag(HallTestTags.table("t")).performTouchInput { click() }
        compose.onNodeWithTag(HookahTestTags.OVERLAY).assertIsDisplayed()
        compose.onNodeWithTag(HallTestTags.ACTION_MENU).assertDoesNotExist()
        val first = compose.onNodeWithTag(HookahTestTags.card("h1")).fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag(HookahTestTags.card("h2")).fetchSemanticsNode().boundsInRoot
        assertEquals(first.width, first.height, 1f)
        assertEquals(first.top, second.top, 1f)
        assertTrue(second.left > first.left)
        compose.onNodeWithTag(HookahTestTags.card("h2")).performClick()
        compose.waitForIdle()
        val after = vm.state.value.tables.single()
        assertEquals(before.hookahs[0], after.hookahs[0])
        assertEquals(before.hookahs[2], after.hookahs[2])
        assertEquals(TableTimerState.Running("p2", 181_000L), after.hookahs[1].timerState)
        compose.onNodeWithTag(HookahTestTags.CLOSE).performClick()
        compose.onNodeWithTag(HallTestTags.table("t")).assertIsDisplayed()
    }

    @Test fun eightCardsScrollWithoutMovingFixedCloseAction() {
        show(multiple(8))
        compose.onNodeWithTag(HallTestTags.table("t")).performClick()
        val closeBefore = compose.onNodeWithTag(HookahTestTags.CLOSE).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag(HookahTestTags.GRID).performScrollToIndex(7)
        compose.onNodeWithTag(HookahTestTags.card("h8")).assertIsDisplayed()
        val closeAfter = compose.onNodeWithTag(HookahTestTags.CLOSE).fetchSemanticsNode().boundsInRoot
        assertEquals(closeBefore, closeAfter)
    }

    @Test fun completionStaysInWindowAndBackOrOutsideNeverResetsTable() {
        val initial = multiple().copy(hookahs = multiple().hookahs.map {
            it.copy(timerState = TableTimerState.Running("p2", 10_000L))
        })
        val vm = show(initial)
        compose.onNodeWithTag(HallTestTags.table("t")).performClick()
        compose.onNodeWithTag(HookahTestTags.card("h1")).performClick()
        compose.onNodeWithTag(HookahTestTags.card("h2")).performClick()
        compose.onNodeWithTag(HookahTestTags.OVERLAY).assertIsDisplayed()
        compose.onNodeWithTag(HookahTestTags.card("h1")).assertIsNotEnabled()
        assertTrue(vm.state.value.tables.single().isCompleted)
        dispatchActivityBack()
        compose.waitUntil(5_000L) { vm.state.value.selectedHookahTableId == null }
        assertTrue(vm.state.value.tables.single().isCompleted)
        compose.onNodeWithTag(HallTestTags.table("t")).performTouchInput { longClick() }
        compose.waitUntil(5_000L) { vm.state.value.tables.single().hookahs.size == 3 }
        assertEquals(3, vm.state.value.tables.single().hookahs.last().number)
        compose.onNodeWithTag(HallTestTags.table("t")).performClick()
        val beforeDismiss = vm.state.value.tables.single()
        compose.onNodeWithTag(HookahTestTags.DISMISS_AREA).performTouchInput { click(Offset(2f, 2f)) }
        compose.waitForIdle()
        assertEquals(beforeDismiss, vm.state.value.tables.single())
        assertNull(vm.state.value.selectedHookahTableId)
        compose.onNodeWithTag(HallTestTags.table("t")).performClick()
        val thirdId = vm.state.value.tables.single().hookahs.last().id
        compose.onNodeWithTag(HookahTestTags.GRID).performScrollToIndex(2)
        compose.onNodeWithTag(HookahTestTags.card(thirdId)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(HookahTestTags.card(thirdId)).performClick()
        compose.onNodeWithTag(HookahTestTags.CLOSE).performClick()
        compose.onNodeWithTag(HallTestTags.table("t")).performClick()
        compose.waitForIdle()
        val reset = vm.state.value.tables.single()
        assertTrue(reset.isIdle)
        assertEquals(1, reset.hookahs.single().number)
        assertEquals(initial.name, reset.name)
        assertEquals(initial.passages, reset.passages)
        assertEquals(initial.position, reset.position)
    }

    @Test fun cardsAndSummaryShareWarningAndOverdueRulesAndPillLayout() {
        val table = base().copy(shape = TableShape.PILL, hookahs = listOf(
            TableHookah("h1", 1, TableTimerState.Running("p1", 500L)),
            TableHookah("h2", 2, TableTimerState.Running("p1", 61_000L)),
        ))
        show(table)
        compose.onNodeWithTag(HallTestTags.table("t"))
            .assert(SemanticsMatcher.expectValue(TableTimerVisualStateKey, TableTimerVisualState.OVERDUE))
        compose.onNodeWithTag(HallTestTags.tableName("t"), true).assertTextEquals("VIP")
        compose.onNodeWithTag(HallTestTags.tablePassage("t"), true).assertDoesNotExist()
        compose.onNodeWithTag(HallTestTags.table("t")).performClick()
        compose.onNodeWithTag(HookahTestTags.card("h1"))
            .assert(SemanticsMatcher.expectValue(TableTimerVisualStateKey, TableTimerVisualState.OVERDUE))
        compose.onNodeWithTag(HookahTestTags.card("h2"))
            .assert(SemanticsMatcher.expectValue(TableTimerVisualStateKey, TableTimerVisualState.ENDING_SOON))
    }

    @Test fun completedTableHasSeparateAccessibleResetAndAddActions() {
        val completed = multiple().copy(hookahs = multiple().hookahs.map {
            it.copy(timerState = TableTimerState.Completed)
        })
        val vm = show(completed)
        val table = compose.onNodeWithTag(HallTestTags.table("t"))
        table.assert(clickLabelIs("Сбросить стол"))
            .assert(longClickLabelIs("Добавить кальян"))
        table.performSemanticsAction(SemanticsActions.OnClick) { action -> assertTrue(action()) }
        compose.waitUntil(5_000L) { vm.state.value.tables.single().isIdle }
        val reset = vm.state.value.tables.single()
        assertEquals(1, reset.hookahs.single().number)
        assertEquals(completed.name, reset.name)
        assertEquals(completed.passages, reset.passages)
        assertEquals(completed.position, reset.position)
        assertNull(vm.state.value.selectedHookahTableId)
        table.assert(clickLabelIs("Запустить кальян"))
    }

    @Test fun accessibleHoldAddsNextHookahWithoutResettingCompletedTable() {
        val hapticFeedback = RecordingHapticFeedback()
        val completed = multiple().copy(hookahs = multiple().hookahs.map {
            it.copy(timerState = TableTimerState.Completed)
        })
        val vm = show(completed, hapticFeedback = hapticFeedback)
        val table = compose.onNodeWithTag(HallTestTags.table("t"))
        table.assert(longClickLabelIs("Добавить кальян"))
            .performSemanticsAction(SemanticsActions.OnLongClick) { action -> assertTrue(action()) }
        compose.waitUntil(5_000L) { vm.state.value.tables.single().hookahs.size == 3 }
        val updated = vm.state.value.tables.single()
        assertEquals(completed.hookahs, updated.hookahs.take(2))
        assertEquals(3, updated.hookahs.last().number)
        assertEquals(TableTimerState.Running("p1", 121_000L), updated.hookahs.last().timerState)
        assertNull(vm.state.value.selectedHookahTableId)
        table.assert(clickLabelIs("Открыть кальяны стола"))
        assertTrue(hapticFeedback.events.isEmpty())
    }

    @Test fun tableClickLabelFollowsIdleRunningAndMultipleStates() {
        val vm = show(base())
        val table = compose.onNodeWithTag(HallTestTags.table("t"))
        table.assert(clickLabelIs("Запустить кальян"))
            .performSemanticsAction(SemanticsActions.OnClick) { action -> assertTrue(action()) }
        compose.waitUntil(5_000L) { !vm.state.value.tables.single().isIdle }
        table.assert(clickLabelIs("Провести проходку"))
            .performSemanticsAction(SemanticsActions.OnLongClick) { action -> assertTrue(action()) }
        compose.waitUntil(5_000L) { vm.state.value.tables.single().hasMultipleHookahs }
        val beforeOpening = vm.state.value.tables.single()
        table.assert(clickLabelIs("Открыть кальяны стола"))
            .performSemanticsAction(SemanticsActions.OnClick) { action -> assertTrue(action()) }
        compose.onNodeWithTag(HookahTestTags.OVERLAY).assertIsDisplayed()
        assertEquals(beforeOpening, vm.state.value.tables.single())
    }

    @Test fun accessibleDescriptionsDistinguishHookahCountNumberAndPassage() {
        val table = base().copy(hookahs = listOf(
            TableHookah("h1", 1, TableTimerState.Completed),
            TableHookah("h2", 2, TableTimerState.Running("p1", 121_000L)),
            TableHookah("h3", 3, TableTimerState.Running("p2", 61_000L)),
            TableHookah("h4", 4),
        ))
        show(table)
        assertSingleDescription(
            HallTestTags.table("t"),
            "VIP, кальянов: 4. Кальян №3, проходка 2 из 2, скоро закончится, осталось 01:00",
        )
        compose.onNodeWithTag(HallTestTags.table("t")).performClick()
        assertSingleDescription(HookahTestTags.card("h1"), "Кальян №1, обслуживание завершено")
        compose.onNodeWithTag(HookahTestTags.card("h1")).assertIsNotEnabled()
        assertSingleDescription(
            HookahTestTags.card("h2"), "Кальян №2, проходка 1 из 2, осталось 02:00",
        )
        compose.onNodeWithTag(HookahTestTags.GRID).performScrollToIndex(2)
        assertSingleDescription(
            HookahTestTags.card("h3"), "Кальян №3, проходка 2 из 2, скоро закончится, осталось 01:00",
        )
        assertSingleDescription(HookahTestTags.card("h4"), "Кальян №4, Не запущен")
        compose.onNodeWithTag(HookahTestTags.card("h4")).assert(clickLabelIs("Запустить кальян"))
    }

    @Test fun overdueCardHasOneAnnouncementAndAdvancesOnlyItsHookah() {
        val original = base().copy(hookahs = listOf(
            TableHookah("h1", 1, TableTimerState.Running("p1", 1_000L)),
            TableHookah("h2", 2, TableTimerState.Running("p1", 62_000L)),
        ))
        val vm = show(original, nowEpochMillis = 2_000L)
        assertSingleDescription(
            HallTestTags.table("t"),
            "VIP, кальянов: 2. Кальян №1, проходка 1 из 2, просрочка -00:01",
        )
        compose.onNodeWithTag(HallTestTags.table("t")).performClick()
        assertSingleDescription(
            HookahTestTags.card("h1"), "Кальян №1, проходка 1 из 2, просрочка -00:01",
        )
        compose.onNodeWithTag(HookahTestTags.card("h1"))
            .assert(clickLabelIs("Провести проходку"))
            .performSemanticsAction(SemanticsActions.OnClick) { action -> assertTrue(action()) }
        compose.waitUntil(5_000L) {
            vm.state.value.tables.single().hookahs[0].timerState == TableTimerState.Running("p2", 182_000L)
        }
        assertEquals(original.hookahs[1], vm.state.value.tables.single().hookahs[1])
        assertSingleDescription(
            HookahTestTags.card("h1"), "Кальян №1, проходка 2 из 2, осталось 03:00",
        )
    }

    @Test fun modalHidesBackgroundAccessibilityActionsAndRestoresInputFocus() {
        show(multiple())
        val previousInputMode = compose.runOnIdle { inputModeManager.inputMode }
        try {
            // Material buttons accept input focus in keyboard mode, not touch mode.
            // Semantic activation preserves that mode; performClick injects a touch.
            compose.runOnIdle {
                assertTrue(inputModeManager.requestInputMode(InputMode.Keyboard))
            }
            compose.waitUntil(5_000L) {
                compose.runOnIdle { inputModeManager.inputMode == InputMode.Keyboard }
            }
            compose.onNodeWithTag(HallTestTags.table("t"))
                .performSemanticsAction(SemanticsActions.OnClick) { action -> assertTrue(action()) }
            compose.onNodeWithTag(HookahTestTags.CLOSE).assertIsFocused()
            compose.onNodeWithTag(HallTestTags.table("t")).assertDoesNotExist()
            compose.onNodeWithTag(HallTestTags.ACTION_MENU).assertDoesNotExist()
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
                .assertCountEquals(0)
            compose.onNodeWithTag(HookahTestTags.CLOSE)
                .performSemanticsAction(SemanticsActions.OnClick) { action -> assertTrue(action()) }
            compose.onNodeWithTag(HallTestTags.table("t"))
                .assertIsFocused()
                .assert(longClickLabelIs("Добавить кальян"))
        } finally {
            // InputModeManager cannot switch Android back into touch mode programmatically.
            InstrumentationRegistry.getInstrumentation()
                .setInTouchMode(previousInputMode == InputMode.Touch)
        }
    }

    private fun assertSingleDescription(tag: String, description: String) {
        compose.onNodeWithTag(tag).assertContentDescriptionEquals(description)
        // Neither a decorative icon nor another descendant may add an announcement.
        val subtree = hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))
        compose.onAllNodes(
            subtree and SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
            useUnmergedTree = true,
        ).assertCountEquals(1)
    }

    private fun clickLabelIs(label: String) = SemanticsMatcher("Click label is '$label'") {
        it.config.contains(SemanticsActions.OnClick) && it.config[SemanticsActions.OnClick].label == label
    }

    private fun longClickLabelIs(label: String) = SemanticsMatcher("Long click label is '$label'") {
        it.config.contains(SemanticsActions.OnLongClick) && it.config[SemanticsActions.OnLongClick].label == label
    }

    private class RecordingHapticFeedback : HapticFeedback {
        val events = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            events += hapticFeedbackType
        }
    }
}
