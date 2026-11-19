package io.github.dewfallapp.feature.youtubesearch.ui

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dewfallapp.core.model.VideoId
import java.time.ZoneId
import kotlin.time.Clock

/**
 * The Search YouTube screen, with its ViewModel. The app shows it as its first screen.
 * [onOpenVideo] runs when the user taps a video. See openspec/specs/youtube-search/spec.md.
 */
@Composable
fun YouTubeSearch(onOpenVideo: (VideoId) -> Unit, modifier: Modifier = Modifier) {
    YouTubeSearchRoute(onOpenVideo = onOpenVideo, modifier = modifier)
}

/**
 * Connects the screen to its ViewModel. [viewModel] comes from the current ViewModelStoreOwner, the
 * screen's entry on the app's back stack, whose default factory is the activity's, which Hilt
 * provides, so @HiltViewModel works with viewModel(). The field keeps its own text, and saves it
 * across process death as rememberSaveable would.
 */
@Composable
internal fun YouTubeSearchRoute(
    onOpenVideo: (VideoId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YouTubeSearchViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val textFieldState = rememberTextFieldState()
    // The clock is read once per answer, so the screen itself stays a function of what it gets.
    val now = remember(state.results) { Clock.System.now() }
    val zone = remember(state.results) { ZoneId.systemDefault() }
    YouTubeSearchScreen(
        state = state,
        textFieldState = textFieldState,
        now = now,
        zone = zone,
        onIntent = viewModel::dispatch,
        onOpenVideo = onOpenVideo,
        modifier = modifier,
    )
}
