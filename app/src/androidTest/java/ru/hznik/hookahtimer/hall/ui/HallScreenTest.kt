package ru.hznik.hookahtimer.hall.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.hznik.hookahtimer.hall.data.InMemoryHallRepository
import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.CanvasPosition
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
        composeRule.onNodeWithText("Зал").assertDoesNotExist()
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_EDIT_MODE).assertIsDisplayed()
        composeRule.onNodeWithTag(HallTestTags.ADD_TABLE).assertDoesNotExist()
        composeRule.onNodeWithTag(HallTestTags.CANVAS_GRID).assertExists()
    }

    @Test
    fun editModeShowsIndicatorAndAddAction() {
        setStaticContent(HallUiState(isEditMode = true))

        composeRule.onNodeWithTag(HallTestTags.EDIT_MODE_INDICATOR).assertIsDisplayed()
        composeRule.onNodeWithTag(HallTestTags.ADD_TABLE)
            .assertContentDescriptionEquals("Добавить стол")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_EDIT_MODE)
            .assertContentDescriptionEquals("Готово")
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_FULLSCREEN).assertDoesNotExist()
    }

    @Test
    fun workModeShowsOnlyFullscreenAndEditFloatingActions() {
        setStaticContent(HallUiState())

        composeRule.onNodeWithTag(HallTestTags.TOGGLE_FULLSCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_EDIT_MODE)
            .assertContentDescriptionEquals("Редактировать")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(HallTestTags.ADD_TABLE).assertDoesNotExist()
    }

    @Test
    fun fullscreenActionHasCorrectLabelAndDispatchesToggle() {
        var action: HallAction? = null
        setStaticContent(
            state = HallUiState(isFullscreenEnabled = true),
            onAction = { action = it },
        )

        composeRule.onNodeWithTag(HallTestTags.TOGGLE_FULLSCREEN)
            .assertContentDescriptionEquals("Выйти из полного экрана")
            .assertHasClickAction()
            .performClick()

        assertEquals(HallAction.ToggleFullscreen, action)
    }

    @Test
    fun pinchChangesViewportWithoutDispatchingTableAction() {
        var action: HallAction? = null
        setStaticContent(HallUiState(), onAction = { action = it })

        composeRule.onNodeWithTag(HallTestTags.CANVAS_GRID).performTouchInput {
            down(0, center + Offset(-80f, 0f))
            down(1, center + Offset(80f, 0f))
            moveTo(0, center + Offset(-180f, 0f))
            moveTo(1, center + Offset(180f, 0f))
            up(0)
            up(1)
        }
        composeRule.waitForIdle()

        val scale = composeRule.onNodeWithTag(HallTestTags.CANVAS_GRID)
            .fetchSemanticsNode().config[CanvasScaleKey]
        assertTrue(scale > 1f)
        assertEquals(null, action)
    }

    @Test
    fun freeAreaSwipePansTowardFarTableWithoutMovingIt() {
        val table = HallTable(
            id = "far",
            name = "Дальний",
            position = CanvasPosition(2_500f, 200f),
        )
        val actions = mutableListOf<HallAction>()
        setStaticContent(HallUiState(tables = listOf(table)), onAction = actions::add)

        composeRule.onNodeWithTag(HallTestTags.CANVAS_GRID).performTouchInput {
            swipe(center, center + Offset(-400f, 0f), durationMillis = 500)
        }
        composeRule.waitForIdle()

        val offsetX = composeRule.onNodeWithTag(HallTestTags.CANVAS_GRID)
            .fetchSemanticsNode().config[CanvasOffsetXKey]
        assertTrue(offsetX > 0f)
        assertTrue(actions.none { it is HallAction.MoveTable })
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
                        position = CanvasPosition(500f, 400f),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithTag(HallTestTags.table("circle"))
            .assertContentDescriptionEquals("Круглый стол Обычный")
        composeRule.onNodeWithTag(HallTestTags.table("pill"))
            .assertContentDescriptionEquals("Стол-пилюля VIP")
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
    fun deleteHandleIsOutsideAndOverlapsCircleAndPillTables() {
        setStaticContent(
            HallUiState(
                tables = listOf(
                    HallTable("circle", "Круг", position = CanvasPosition(100f, 120f)),
                    HallTable(
                        "pill",
                        "Пилюля",
                        TableShape.PILL,
                        CanvasPosition(360f, 120f),
                    ),
                ),
                isEditMode = true,
            ),
        )

        listOf("circle", "pill").forEach { id ->
            val table = composeRule.onNodeWithTag(HallTestTags.table(id))
                .fetchSemanticsNode().boundsInRoot
            val delete = composeRule.onNodeWithTag(HallTestTags.deleteTable(id))
                .assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertTrue(delete.top < table.top)
            assertTrue(delete.bottom > table.top)
            assertTrue(delete.right > table.right)
            assertTrue(delete.left < table.right)
        }
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
    fun dragInEditModeMovesTableInLogicalSpace() {
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
        assertTrue(position.x > CanvasPosition.Default.x)
        assertTrue(position.y > CanvasPosition.Default.y)
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
                        position = CanvasPosition(80f, 100f),
                        passages = passages,
                        timerState = TableTimerState.Running("p1", 150_001L),
                    ),
                    HallTable(
                        id = "overdue",
                        name = "Просрочен",
                        position = CanvasPosition(360f, 260f),
                        passages = passages,
                        timerState = TableTimerState.Running("p2", 60_001L),
                    ),
                    HallTable(
                        id = "idle",
                        name = "Свободен",
                        position = CanvasPosition(80f, 500f),
                        passages = passages,
                    ),
                    HallTable(
                        id = "completed",
                        name = "Готов",
                        position = CanvasPosition(650f, 500f),
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
            HallTestTags.tablePassage("running"),
            useUnmergedTree = true,
        )
            .assertTextEquals("1/2")
        composeRule.onNodeWithTag(
            HallTestTags.tablePassage("overdue"),
            useUnmergedTree = true,
        )
            .assertTextEquals("2/2")
        composeRule.onNodeWithTag(
            HallTestTags.tablePassage("idle"),
            useUnmergedTree = true,
        )
            .assertDoesNotExist()
        composeRule.onNodeWithTag(
            HallTestTags.tablePassage("completed"),
            useUnmergedTree = true,
        )
            .assertDoesNotExist()
        composeRule.onNodeWithTag(
            HallTestTags.completedMark("completed"),
            useUnmergedTree = true,
        )
            .assertTextEquals("×")
        composeRule.onNodeWithTag(HallTestTags.table("running"))
            .assertContentDescriptionEquals(
                "Стол-пилюля Работает, проходка 1 из 2, осталось 00:30",
            )
        composeRule.onNodeWithTag(HallTestTags.table("overdue"))
            .assertContentDescriptionEquals(
                "Круглый стол Просрочен, проходка 2 из 2, просрочка -01:00",
            )
        composeRule.onNodeWithTag(HallTestTags.table("completed"))
            .assertContentDescriptionEquals(
                "Круглый стол Готов, обслуживание завершено",
            )
        listOf("running", "overdue").forEach { id ->
            val tableBounds = composeRule.onNodeWithTag(HallTestTags.table(id))
                .fetchSemanticsNode().boundsInRoot
            val passageBounds = composeRule.onNodeWithTag(
                HallTestTags.tablePassage(id),
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot
            assertTrue(passageBounds.left >= tableBounds.left)
            assertTrue(passageBounds.top >= tableBounds.top)
            assertTrue(passageBounds.right <= tableBounds.right)
            assertTrue(passageBounds.bottom <= tableBounds.bottom)
        }
    }

    @Test
    fun runningOverdueAndCompletedTablesCannotOpenSettingsInEditMode() {
        val table = HallTable(id = "table", name = "Стол")
        setStaticContent(
            state = HallUiState(
                tables = listOf(
                    table.copy(
                        id = "running",
                        position = CanvasPosition(80f, 100f),
                        timerState = TableTimerState.Running(
                            table.passages.first().id,
                            1_801_000L,
                        ),
                    ),
                    table.copy(
                        id = "overdue",
                        position = CanvasPosition(360f, 260f),
                        timerState = TableTimerState.Running(
                            table.passages.first().id,
                            500L,
                        ),
                    ),
                    table.copy(
                        id = "completed",
                        position = CanvasPosition(650f, 500f),
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
    fun twoPassageCycleAdvancesAndResetsWithOneTapPerStage() {
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
        composeRule.onNodeWithTag(
            HallTestTags.tablePassage("id-1"),
            useUnmergedTree = true,
        ).assertTextEquals("1/2")

        tableNode.performClick()
        composeRule.onNodeWithText("Завершить проходку раньше?").assertDoesNotExist()
        assertEquals(
            viewModel.state.value.tables.single().passages[1].id,
            (viewModel.state.value.tables.single().timerState as TableTimerState.Running).passageId,
        )
        composeRule.onNodeWithTag(
            HallTestTags.tablePassage("id-1"),
            useUnmergedTree = true,
        ).assertTextEquals("2/2")

        tableNode.performClick()
        composeRule.onNodeWithText("Завершить проходку раньше?").assertDoesNotExist()
        assertEquals(TableTimerState.Completed, viewModel.state.value.tables.single().timerState)
        composeRule.onNodeWithTag(
            HallTestTags.completedMark("id-1"),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            HallTestTags.tablePassage("id-1"),
            useUnmergedTree = true,
        ).assertDoesNotExist()

        tableNode.performClick()
        composeRule.onNodeWithText("Сбросить стол?").assertDoesNotExist()
        assertEquals(TableTimerState.Idle, viewModel.state.value.tables.single().timerState)
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_EDIT_MODE).performClick()
        tableNode.assertHasClickAction()
    }

    @Test
    fun toolbarActionsRemainVisibleAtMinimumTabletWidth() {
        composeRule.setContent {
            HookahTimerTheme {
                Box(
                    modifier = Modifier
                        .requiredWidth(600.dp)
                        .height(800.dp),
                ) {
                    HallScreen(
                        state = HallUiState(),
                        onAction = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeRule.onNodeWithTag(HallTestTags.TOGGLE_FULLSCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(HallTestTags.TOGGLE_EDIT_MODE).assertIsDisplayed()
    }

    @Test
    fun thirtyMixedTablesHaveUniqueNodesAndKeepIndependentActions() {
        val passages = listOf(TablePassage("p1", 1), TablePassage("p2", 1))
        val tables = List(30) { index ->
            val timerState = when (index % 4) {
                1 -> TableTimerState.Running("p1", 120_000L)
                2 -> TableTimerState.Running("p2", 500L)
                3 -> TableTimerState.Completed
                else -> TableTimerState.Idle
            }
            HallTable(
                id = "table-$index",
                name = "Стол ${index + 1}",
                shape = if (index % 2 == 0) TableShape.CIRCLE else TableShape.PILL,
                position = CanvasPosition(
                    x = (index % 6) * 220f,
                    y = (index / 6) * 170f,
                ),
                passages = passages,
                timerState = timerState,
            )
        }
        var action: HallAction? = null
        setStaticContent(
            state = HallUiState(tables = tables),
            onAction = { action = it },
            timeProvider = TimeProvider { 60_000L },
        )

        tables.forEach { table ->
            assertEquals(
                1,
                composeRule.onAllNodesWithTag(HallTestTags.table(table.id))
                    .fetchSemanticsNodes().size,
            )
        }
        composeRule.onNodeWithTag(HallTestTags.table("table-0")).performClick()
        assertEquals(HallAction.AdvanceTimer("table-0"), action)
    }

    @Test
    fun resizingHallDoesNotEmitMoveCommandsForEdgeTables() {
        val width = mutableStateOf(600.dp)
        val actions = mutableListOf<HallAction>()
        val tables = listOf(
            HallTable(
                id = "top-left",
                name = "Слева сверху",
                position = CanvasPosition(0f, 0f),
            ),
            HallTable(
                id = "top-right",
                name = "Справа сверху",
                shape = TableShape.PILL,
                position = CanvasPosition(800f, 0f),
            ),
            HallTable(
                id = "bottom-left",
                name = "Слева снизу",
                shape = TableShape.PILL,
                position = CanvasPosition(0f, 600f),
            ),
            HallTable(
                id = "bottom-right",
                name = "Справа снизу",
                position = CanvasPosition(800f, 600f),
            ),
        )
        composeRule.setContent {
            HookahTimerTheme {
                HallScreen(
                    state = HallUiState(tables = tables, isEditMode = true),
                    onAction = actions::add,
                    modifier = Modifier
                        .requiredWidth(width.value)
                        .height(700.dp),
                )
            }
        }

        composeRule.runOnIdle { width.value = 1_000.dp }
        composeRule.waitForIdle()

        assertTrue(actions.none { it is HallAction.MoveTable })
        tables.forEach { table ->
            composeRule.onNodeWithTag(HallTestTags.table(table.id)).assertExists()
        }
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

}
