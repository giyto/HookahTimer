package ru.hznik.hookahtimer.hall.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import ru.hznik.hookahtimer.hall.model.HallTable
import ru.hznik.hookahtimer.hall.model.NormalizedPosition
import ru.hznik.hookahtimer.hall.model.TableShape
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

    private fun setStaticContent(state: HallUiState) {
        composeRule.setContent {
            HookahTimerTheme {
                HallScreen(state = state, onAction = {})
            }
        }
    }

    private fun setViewModelContent(): HallViewModel {
        var nextId = 0
        val viewModel = HallViewModel(idFactory = { "id-${++nextId}" })
        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            HookahTimerTheme {
                HallScreen(
                    state = state,
                    onAction = viewModel::onAction,
                )
            }
        }
        return viewModel
    }

    private companion object {
        const val POSITION_DELTA = 0.01f
    }
}
