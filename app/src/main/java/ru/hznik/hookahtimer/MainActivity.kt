package ru.hznik.hookahtimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.hznik.hookahtimer.hall.presentation.HallViewModel
import ru.hznik.hookahtimer.ui.theme.HookahTimerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HookahTimerTheme {
                val hallViewModel: HallViewModel = viewModel(
                    factory = HallViewModel.factory(
                        repository = (application as HookahTimerApplication).hallRepository,
                    ),
                )
                HookahTimerApp(hallViewModel = hallViewModel)
            }
        }
    }
}
