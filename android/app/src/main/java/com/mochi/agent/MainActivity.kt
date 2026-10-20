package com.mochi.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mochi.agent.ui.MainScreen
import com.mochi.agent.ui.theme.MochiTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MochiApplication
        val factory = MochiViewModelFactory(app)

        setContent {
            MochiTheme {
                val viewModel: com.mochi.agent.viewmodel.MochiViewModel = viewModel(factory = factory)
                MainScreen(viewModel)
            }
        }
    }
}

/** Builds [MochiViewModel] with the Application-scoped dependencies. */
private class MochiViewModelFactory(
    private val app: MochiApplication,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(com.mochi.agent.viewmodel.MochiViewModel::class.java)) {
            return com.mochi.agent.viewmodel.MochiViewModel(app) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}