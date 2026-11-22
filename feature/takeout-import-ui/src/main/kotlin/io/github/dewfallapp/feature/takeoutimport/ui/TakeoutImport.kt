package io.github.dewfallapp.feature.takeoutimport.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dewfallapp.feature.takeoutimport.logic.TakeoutImportIntent
import io.github.dewfallapp.feature.takeoutimport.logic.TakeoutImportState

/**
 * The Takeout import screen, with its ViewModel. [onBack] runs for the back arrow, and [onDone] for
 * Done after an import. See openspec/specs/subscriptions/spec.md.
 */
@Composable
fun TakeoutImport(onBack: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    TakeoutImportRoute(onBack = onBack, onDone = onDone, modifier = modifier)
}

/**
 * The types the file picker asks for: every type. The app that holds a file reports its type
 * itself, so a filter could hide the user's own export. The reader checks the content instead. See
 * docs/architecture.md#the-takeout-import.
 */
private val PickerTypes = arrayOf("*/*")

/**
 * Connects the screen to its ViewModel and to Android's file picker. [viewModel] comes from the
 * screen's entry on the app's back stack, as for the other screens. The picker grants access to the
 * picked file for this read only.
 */
@Composable
internal fun TakeoutImportRoute(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TakeoutImportViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.dispatch(TakeoutImportIntent.FilePicked(uri.toString()))
        }
    // Leaving cancels a running import at once, not when the entry leaves the back stack after its
    // exit animation, so a file still being read subscribes to nothing. While a file is read, the
    // back gesture comes here too. Otherwise Navigation 3 handles it, with its predictive
    // animation.
    val leave = {
        viewModel.dispatch(TakeoutImportIntent.Left)
        onBack()
    }
    BackHandler(enabled = state == TakeoutImportState.Reading, onBack = leave)
    TakeoutImportScreen(
        state = state,
        onChooseFile = { picker.launch(PickerTypes) },
        onBack = leave,
        onDone = onDone,
        modifier = modifier,
    )
}
