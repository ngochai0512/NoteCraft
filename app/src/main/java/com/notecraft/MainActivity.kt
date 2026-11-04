package com.notecraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.notecraft.ui.NoteCraftApp
import com.notecraft.ui.NoteCraftViewModel
import com.notecraft.ui.theme.NoteCraftTheme

class MainActivity : ComponentActivity() {

    private val viewModel: NoteCraftViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NoteCraftTheme {
                NoteCraftApp(viewModel)
            }
        }
    }
}
