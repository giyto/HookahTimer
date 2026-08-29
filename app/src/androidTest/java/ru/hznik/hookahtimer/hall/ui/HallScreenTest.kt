package ru.hznik.hookahtimer.hall.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.hznik.hookahtimer.hall.data.InMemoryHallRepository
import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.NormalizedPosition
import ru.hznik.hookahtimer.hall.model.TablePassage
import ru.hznik.hookahtimer.hall.model.TableShape
import ru.hznik.hookahtimer.hall.model.TableTimerState
import ru.hznik.hookahtimer.hall.model.TimeProvider
import ru.hznik.hookahtimer.hall.presentation.HallAction
import ru.hznik.hookahtimer.hall.presentation.HallUiState
import ru.hznik.hookahtimer.hall.presentation.HallViewModel
import ru.hznik.hookahtimer.ui.theme.HookahTimerTheme

class HallScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyHallShowsEditActionButNotAddAction() {
        setStaticContent(HallUiState())

        composeRule.onNodeWithText("Столов пока нет").assertIsDisplayed()
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_EDIT_MODE).assertIsDisplayed()
        composeRule.onNodeWithTag(HallTestTags.ADD_TABLE).assertDoesNotExist()
    }

    @Test
    fun editModeShowsIndicatorAndAddAction() {
        setStaticContent(HallUiState(isEditMode = true))

        composeRule.onNodeWithTag(HallTestTags.EDIT_MODE_INDICATOR).assertIsDisplayed()
        composeRule.onNodeWithTag(HallTestTags.ADD_TABLE).assertIsDisplayed()
    }

    @Test
    fun addActionCreatesVisibleTableInsideField() {
        val viewModel = setViewModelContent()

        composeRule.onNodeWithTag(HallTestTags.TOGGLE_EDIT_MODE).performClick()
        composeRule.onNodeWithTag(HallTestTags.ADD_TABLE).performClick()

        composeRule.onNodeWithTag(HallTestTags.table("id-1")).assertIsDisplayed()
        composeRule.onNodeWithText("Стол 1").assertIsDisplayed()
        assertEquals(1, viewModel.state.value.tables.size)

        val fieldBounds = composeRule.onNodeWithTag(HallTestTags.HALL_FIELD)
            .fetchSemanticsNode().boundsInRoot
        val tableBounds = composeRule.onNodeWithTag(HallTestTags.table("id-1"))
            .fetchSemanticsNode().boundsInRoot
        assertTrue(tableBounds.left >= fieldBounds.left)
        assertTrue(tableBounds.top >= fieldBounds.top)
        assertTrue(tableBounds.right <= fieldBounds.right)
        assertTrue(tableBounds.bottom <= fieldBounds.bottom)
    }

    @Test
    fun circleAndPillTablesExposeTheirShape() {
        setStaticContent(
            HallUiState(
                tables = listOf(
                    HallTable(id = "circle", name = "Обычный"),
                    HallTable(
                        id = "pill",
                        name = "VIP",
                        shape = TableShape.PILL,
                        position = NormalizedPosition.of(0.7f, 0.7f),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithTag(HallTestTags.table("circle"))
            .assertContentDescriptionEquals("Круглый стол Обычный")
        composeRule.onNodeWithTag(HallTestTags.table("pill"))
            .assertContentDescriptionEquals("Pill-стол VIP")
    }

    @Test
    fun deleteRequiresConfirmationAndCanBeCancelled() {
        val viewModel = setViewModelContent()
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_EDIT_MODE).performClick()
        composeRule.onNodeWithTag(HallTestTags.ADD_TABLE).performClick()

        composeRule.onNodeWithTag(HallTestTags.deleteTable("id-1")).performClick()
        composeRule.onNodeWithText("Удалить стол?").assertIsDisplayed()
        composeRule.onNodeWithTag(HallTestTags.CANCEL_DELETE).performClick()

        composeRule.onNodeWithTag(HallTestTags.table("id-1")).assertIsDisplayed()
        assertEquals(1, viewModel.state.value.tables.size)

        composeRule.onNodeWithTag(HallTestTags.deleteTable("id-1")).performClick()
        composeRule.onNodeWithTag(HallTestTags.CONFIRM_DELETE).performClick()

        composeRule.onNodeWithTag(HallTestTags.table("id-1")).assertDoesNotExist()
        assertEquals(0, viewModel.state.value.tables.size)
    }

    @Test
    fun workingModeHidesDeleteAndIgnoresDrag() {
        val viewModel = setViewModelContent()
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_EDIT_MODE).performClick()
        composeRule.onNodeWithTag(HallTestTags.ADD_TABLE).performClick()
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_EDIT_MODE).performClick()
        val positionBeforeGesture = viewModel.state.value.tables.single().position

        composeRule.onNodeWithTag(HallTestTags.deleteTable("id-1")).assertDoesNotExist()
        composeRule.onNodeWithTag(HallTestTags.table("id-1")).performTouchInput {
            swipe(
                start = center,
                end = center + Offset(500f, 500f),
                durationMillis = 300,
            )
        }
        composeRule.waitForIdle()

        assertEquals(positionBeforeGesture, viewModel.state.value.tables.single().position)
    }

    @Test
    fun dragInEditModeMovesTableAndClampsPosition() {
        val viewModel = setViewModelContent()
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_EDIT_MODE).performClick()
        composeRule.onNodeWithTag(HallTestTags.ADD_TABLE).performClick()

        composeRule.onNodeWithTag(HallTestTags.table("id-1")).performTouchInput {
            swipe(
                start = center,
                end = center + Offset(5_000f, 5_000f),
                durationMillis = 500,
            )
        }
        composeRule.waitForIdle()

        val position = viewModel.state.value.tables.single().position
        assertEquals(1f, position.x, POSITION_DELTA)
        assertEquals(1f, position.y, POSITION_DELTA)
    }

    @Test
    fun completedDragEmitsExactlyOneRepositoryMoveAction() {
        val actions = mutableListOf<HallAction>()
        setStaticContent(
            state = HallUiState(
                tables = listOf(HallTable(id = "table", name = "Стол")),
                isEditMode = true,
            ),
            onAction = actions::add,
        )

        composeRule.onNodeWithTag(HallTestTags.table("table")).performTouchInput {
            swipe(
                start = center,
                end = center + Offset(300f, 200f),
                durationMillis = 500,
            )
        }
        composeRule.waitForIdle()

        assertEquals(1, actions.filterIsInstance<HallAction.MoveTable>().size)
    }

    @Test
    fun tapInEditModeDoesNotMoveTable() {
        val viewModel = setViewModelContent()
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_EDIT_MODE).performClick()
        composeRule.onNodeWithTag(HallTestTags.ADD_TABLE).performClick()
        val positionBeforeTap = viewModel.state.value.tables.single().position

        composeRule.onNodeWithTag(HallTestTags.table("id-1")).performTouchInput {
            click(center)
        }
        composeRule.waitForIdle()

        assertEquals(positionBeforeTap, viewModel.state.value.tables.single().position)
    }

    @Test
    fun settingsOpenOnlyInEditModeAndDragDoesNotOpenThem() {
        var openCount = 0
        setStaticContent(
            state = HallUiState(
                tables = listOf(HallTable(id = "table", name = "Стол")),
                isEditMode = true,
            ),
            onOpenSettings = { openCount += 1 },
        )

        composeRule.onNodeWithTag(HallTestTags.table("table")).performClick()
        assertEquals(1, openCount)

        openCount = 0
        composeRule.onNodeWithTag(HallTestTags.table("table")).performTouchInput {
            swipe(
                start = center,
                end = center + Offset(300f, 200f),
                durationMillis = 300,
            )
        }
        composeRule.waitForIdle()
        assertEquals(0, openCount)
    }

    @Test
    fun workingModeTableHasTimerClickAction() {
        var timerAction: HallAction? = null
        setStaticContent(
            state = HallUiState(
                tables = listOf(HallTable(id = "table", name = "Стол")),
            ),
            onAction = { timerAction = it },
        )

        composeRule.onNodeWithTag(HallTestTags.table("table"))
            .assertHasClickAction()
            .performClick()
        assertEquals(HallAction.AdvanceTimer("table"), timerAction)
    }

    @Test
    fun timerOverdueAndCompletedStatesHaveExpectedContent() {
        val passages = listOf(
            TablePassage("p1", 1),
            TablePassage("p2", 1),
        )
        setStaticContent(
            state = HallUiState(
                tables = listOf(
                    HallTable(
                        id = "running",
                        name = "Работает",
                        shape = TableShape.PILL,
                        position = NormalizedPosition.of(0.15f, 0.2f),
                        passages = passages,
                        timerState = TableTimerState.Running("p1", 150_001L),
                    ),
                    HallTable(
                        id = "overdue",
                        name = "Просрочен",
                        position = NormalizedPosition.of(0.5f, 0.5f),
                        passages = passages,
                        timerState = TableTimerState.Running("p2", 60_001L),
                    ),
                    HallTable(
                        id = "completed",
                        name = "Готов",
                        position = NormalizedPosition.of(0.85f, 0.8f),
                        passages = passages,
                        timerState = TableTimerState.Completed,
                    ),
                ),
            ),
            timeProvider = TimeProvider { 120_001L },
        )

        composeRule.onNodeWithTag(
            HallTestTags.tableTimer("running"),
            useUnmergedTree = true,
        )
            .assertTextEquals("00:30")
        composeRule.onNodeWithTag(
            HallTestTags.tableTimer("overdue"),
            useUnmergedTree = true,
        )
            .assertTextEquals("-01:00")
        composeRule.onNodeWithTag(
            HallTestTags.completedMark("completed"),
            useUnmergedTree = true,
        )
            .assertTextEquals("×")
        composeRule.onNodeWithTag(HallTestTags.table("overdue"))
            .assertContentDescriptionEquals(
                "Круглый стол Просрочен, проходка 2, просрочка -01:00",
            )
        composeRule.onNodeWithTag(HallTestTags.table("completed"))
            .assertContentDescriptionEquals(
                "Круглый стол Готов, обслуживание завершено",
            )
    }

    @Test
    fun runningOverdueAndCompletedTablesCannotOpenSettingsInEditMode() {
        val table = HallTable(id = "table", name = "Стол")
        setStaticContent(
            state = HallUiState(
                tables = listOf(
                    table.copy(
                        id = "running",
                        position = NormalizedPosition.of(0.15f, 0.2f),
                        timerState = TableTimerState.Running(
                            table.passages.first().id,
                            1_801_000L,
                        ),
                    ),
                    table.copy(
                        id = "overdue",
                        position = NormalizedPosition.of(0.5f, 0.5f),
                        timerState = TableTimerState.Running(
                            table.passages.first().id,
                            500L,
                        ),
                    ),
                    table.copy(
                        id = "completed",
                        position = NormalizedPosition.of(0.85f, 0.8f),
                        timerState = TableTimerState.Completed,
                    ),
                ),
                isEditMode = true,
            ),
            timeProvider = TimeProvider { 1_000L },
        )

        listOf("running", "overdue", "completed").forEach { id ->
            composeRule.onNodeWithTag(HallTestTags.table(id)).assertHasNoClickAction()
        }
    }

    @Test
    fun twoPassageCycleRequiresSeparateTapForReset() {
        val viewModel = setViewModelContent()
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_EDIT_MODE).performClick()
        composeRule.onNodeWithTag(HallTestTags.ADD_TABLE).performClick()
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_EDIT_MODE).performClick()
        val tableNode = composeRule.onNodeWithTag(HallTestTags.table("id-1"))

        tableNode.performClick()
        assertTrue(viewModel.state.value.tables.single().timerState is TableTimerState.Running)
        assertEquals(
            viewModel.state.value.tables.single().passages[0].id,
            (viewModel.state.value.tables.single().timerState as TableTimerState.Running).passageId,
        )

        tableNode.performClick()
        assertEquals(
            viewModel.state.value.tables.single().passages[1].id,
            (viewModel.state.value.tables.single().timerState as TableTimerState.Running).passageId,
        )

        tableNode.performClick()
        assertEquals(TableTimerState.Completed, viewModel.state.value.tables.single().timerState)
        composeRule.onNodeWithTag(
            HallTestTags.completedMark("id-1"),
            useUnmergedTree = true,
        ).assertIsDisplayed()

        tableNode.performClick()
        assertEquals(TableTimerState.Idle, viewModel.state.value.tables.single().timerState)
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_EDIT_MODE).performClick()
        tableNode.assertHasClickAction()
    }

    private fun setStaticContent(
        state: HallUiState,
        onOpenSettings: (String) -> Unit = {},
        onAction: (HallAction) -> Unit = {},
        timeProvider: TimeProvider = TimeProvider { 1_000L },
    ) {
        composeRule.setContent {
            HookahTimerTheme {
                HallScreen(
                    state = state,
                    onAction = onAction,
                    onOpenSettings = onOpenSettings,
                    timeProvider = timeProvider,
                )
            }
        }
    }

    private fun setViewModelContent(): HallViewModel {
        var nextId = 0
        val timeProvider = TimeProvider { 1_000L }
        val viewModel = HallViewModel(
            repository = InMemoryHallRepository(),
            timeProvider = timeProvider,
            idFactory = { "id-${++nextId}" },
        )
        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            HookahTimerTheme {
                HallScreen(
                    state = state,
                    onAction = viewModel::onAction,
                    timeProvider = timeProvider,
                )
            }
        }
        return viewModel
    }

    private companion object {
        const val POSITION_DELTA = 0.01f
    }
}
