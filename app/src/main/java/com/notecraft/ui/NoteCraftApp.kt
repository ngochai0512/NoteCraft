package com.notecraft.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.notecraft.ui.editor.EditorScreen
import com.notecraft.ui.library.LibraryScreen

@Composable
fun NoteCraftApp(viewModel: NoteCraftViewModel) {
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.flush()
    }

    if (viewModel.editor != null) {
        EditorScreen(viewModel)
    } else {
        LibraryScreen(viewModel)
    }
}
