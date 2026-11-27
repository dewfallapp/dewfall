package io.github.dewfallapp.feature.downloads.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.feature.downloads.logic.DownloadsEffect

/**
 * The Downloads screen, with its ViewModel: every download, with its progress, Pause, Resume,
 * Cancel and Remove download. [onOpenVideo] runs when the user taps a row, and [onOpenSearch] when
 * they tap the search button. See the downloads spec in openspec/.
 */
@Composable
fun Downloads(
    onOpenVideo: (VideoId) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DownloadsRoute(onOpenVideo = onOpenVideo, onOpenSearch = onOpenSearch, modifier = modifier)
}

/**
 * Connects the screen to its ViewModel. [viewModel] comes from the current ViewModelStoreOwner, the
 * Downloads screen's entry on the app's back stack, whose default factory is Hilt's. The state is
 * collected with the lifecycle, so the ViewModel stops reading while the screen doesn't show. The
 * pending effects show "Download removed" in the screen's snackbar.
 */
@Composable
internal fun DownloadsRoute(
    onOpenVideo: (VideoId) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingEffects by viewModel.pendingEffects.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val removed = stringResource(R.string.downloads_removed)
    HandleEffects(pendingEffects, viewModel::effectHandled) { effect ->
        when (effect) {
            DownloadsEffect.ShowRemoved -> snackbarHostState.showSnackbar(removed)
        }
    }
    DownloadsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        actions =
            DownloadsActions(
                onOpenVideo = onOpenVideo,
                onOpenSearch = onOpenSearch,
                onIntent = viewModel::dispatch,
            ),
        modifier = modifier,
    )
}
