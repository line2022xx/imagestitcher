package com.linexx.imagestitcher

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linexx.imagestitcher.ui.EditorScreen
import com.linexx.imagestitcher.ui.HomeScreen
import com.linexx.imagestitcher.ui.ResultScreen
import com.linexx.imagestitcher.ui.theme.ImageStitcherTheme
import com.linexx.imagestitcher.viewmodel.EditorViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ImageStitcherTheme {
                AppRoot()
            }
        }
    }
}

private enum class Screen { HOME, EDITOR, RESULT }

@SuppressLint("NewApi")
private fun pickLimit(): Int {
    val systemLimit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching { MediaStore.getPickImagesMaxLimit() }.getOrDefault(100)
    } else {
        100
    }
    return minOf(systemLimit, EditorViewModel.MAX_ITEMS).coerceAtLeast(2)
}

@Composable
private fun AppRoot() {
    val viewModel: EditorViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    var screenName by rememberSaveable { mutableStateOf(Screen.HOME.name) }
    val context = LocalContext.current

    val contract = remember { ActivityResultContracts.PickMultipleVisualMedia(pickLimit()) }
    val picker = rememberLauncherForActivityResult(contract = contract) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addImages(uris)
            screenName = Screen.EDITOR.name
        }
    }
    val launchPicker: () -> Unit = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // 一次性提示
    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    // 导出完成后自动进入结果页
    LaunchedEffect(state.result) {
        if (state.result != null) {
            screenName = Screen.RESULT.name
        }
    }

    BackHandler(enabled = screenName != Screen.HOME.name) {
        screenName = if (screenName == Screen.RESULT.name) {
            Screen.EDITOR.name
        } else {
            Screen.HOME.name
        }
    }

    when (screenName) {
        Screen.HOME.name -> HomeScreen(
            itemCount = state.items.size,
            onPick = launchPicker,
            onContinue = { screenName = Screen.EDITOR.name },
        )

        Screen.EDITOR.name -> EditorScreen(
            state = state,
            onPick = launchPicker,
            onRemove = viewModel::removeAt,
            onMove = viewModel::move,
            onClear = viewModel::clearAll,
            onConfigChange = viewModel::updateConfig,
            onExport = viewModel::export,
            onBack = { screenName = Screen.HOME.name },
        )

        else -> {
            val result = state.result
            if (result == null) {
                LaunchedEffect(Unit) { screenName = Screen.EDITOR.name }
            } else {
                ResultScreen(
                    result = result,
                    saved = state.savedUri != null,
                    onSave = viewModel::saveToGallery,
                    onBack = { screenName = Screen.EDITOR.name },
                )
            }
        }
    }
}
