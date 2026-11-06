package com.notecraft

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import com.notecraft.model.ThemeMode
import com.notecraft.ui.NoteCraftApp
import com.notecraft.ui.NoteCraftViewModel
import com.notecraft.ui.theme.NoteCraftTheme

class MainActivity : ComponentActivity() {

    private val viewModel: NoteCraftViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureDisplayCutout()
        setContent {
            val darkTheme = when (viewModel.settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            NoteCraftTheme(darkTheme = darkTheme) {
                NoteCraftApp(viewModel)
            }
        }
    }

    /**
     * The editor draws edge-to-edge, including behind display cutouts. Use the most permissive
     * cutout mode the platform supports so system bars and insets stay stable when the window
     * enters and leaves immersive layouts.
     */
    private fun configureDisplayCutout() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = mode
        }
    }
}
