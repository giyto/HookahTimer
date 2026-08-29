package ru.hznik.hookahtimer

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.hznik.hookahtimer.hall.presentation.HallAction
import ru.hznik.hookahtimer.hall.presentation.HallViewModel
import ru.hznik.hookahtimer.hall.settings.presentation.TableSettingsResult
import ru.hznik.hookahtimer.hall.settings.presentation.TableSettingsViewModel
import ru.hznik.hookahtimer.hall.settings.ui.TableSettingsScreen
import ru.hznik.hookahtimer.hall.ui.HallScreen

internal const val HALL_ROUTE = "hall"
internal const val TABLE_SETTINGS_ROUTE = "table-settings"
internal const val TABLE_ID_ARGUMENT = "tableId"
internal const val TABLE_SETTINGS_ROUTE_PATTERN = "$TABLE_SETTINGS_ROUTE/{$TABLE_ID_ARGUMENT}"

@Composable
fun HookahTimerApp(
    hallViewModel: HallViewModel,
    modifier: Modifier = Modifier,
    startDestination: String = HALL_ROUTE,
) {
    val navController = rememberNavController()
    val hallState by hallViewModel.state.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(HALL_ROUTE) {
            HallScreen(
                state = hallState,
                onAction = hallViewModel::onAction,
                onOpenSettings = { tableId ->
                    if (hallState.isEditMode) {
                        navController.navigate(tableSettingsRoute(tableId))
                    }
                },
            )
        }
        composable(
            route = TABLE_SETTINGS_ROUTE_PATTERN,
            arguments = listOf(
                navArgument(TABLE_ID_ARGUMENT) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val tableId = backStackEntry.arguments
                ?.getString(TABLE_ID_ARGUMENT)
                ?.let { encodedId -> Uri.decode(encodedId) }
            val table = hallState.tables.firstOrNull { it.id == tableId }

            if (table == null) {
                Box(modifier = Modifier.fillMaxSize())
                LaunchedEffect(tableId) {
                    navController.returnToHall()
                }
            } else {
                val settingsViewModel: TableSettingsViewModel = viewModel(
                    key = "table-settings-${table.id}",
                    factory = TableSettingsViewModel.factory(table),
                )
                val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
                val saveResult = settingsState.saveResult

                LaunchedEffect(saveResult) {
                    if (saveResult != null) {
                        hallViewModel.onAction(saveResult.toHallAction())
                        navController.returnToHall()
                    }
                }

                TableSettingsScreen(
                    state = settingsState,
                    onAction = settingsViewModel::onAction,
                    onCancel = { navController.returnToHall() },
                )
            }
        }
    }
}

internal fun tableSettingsRoute(tableId: String): String =
    "$TABLE_SETTINGS_ROUTE/${Uri.encode(tableId)}"

private fun NavHostController.returnToHall() {
    if (!popBackStack(HALL_ROUTE, inclusive = false)) {
        navigate(HALL_ROUTE) {
            popUpTo(graph.startDestinationId) { inclusive = true }
            launchSingleTop = true
        }
    }
}

private fun TableSettingsResult.toHallAction(): HallAction.UpdateTableSettings =
    HallAction.UpdateTableSettings(
        tableId = tableId,
        name = name,
        shape = shape,
        passages = passages,
    )
