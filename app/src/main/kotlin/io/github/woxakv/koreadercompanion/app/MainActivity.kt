package io.github.woxakv.koreadercompanion.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import io.github.woxakv.koreadercompanion.designsystem.theme.EinkTheme
import io.github.woxakv.koreadercompanion.presentation.navigation.AppNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EinkTheme {
                val viewModel: AppStartViewModel = hiltViewModel()
                val startDestination by viewModel.startDestination.collectAsState()

                // A brief blank frame while HasStorageAccessUseCase resolves,
                // rather than flashing the wrong start destination.
                startDestination?.let { destination ->
                    AppNavHost(startDestination = destination)
                }
            }
        }
    }
}
