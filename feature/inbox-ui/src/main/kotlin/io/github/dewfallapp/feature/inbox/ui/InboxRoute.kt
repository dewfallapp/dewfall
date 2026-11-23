package io.github.dewfallapp.feature.inbox.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.VideoId
import java.time.ZoneId
import kotlin.time.Clock

/**
 * The inbox, the app's home screen, with its ViewModel. [onOpenVideo] runs when the user taps a
 * video, [onOpenChannel] when they tap a card's channel row, [onOpenSearch] when they tap a search
 * button, and [onImportFromTakeout] when they tap "Import from Google Takeout". See the inbox spec
 * in openspec/.
 */
@Composable
fun Inbox(
    onOpenVideo: (VideoId) -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    onOpenSearch: () -> Unit,
    onImportFromTakeout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InboxRoute(
        onOpenVideo = onOpenVideo,
        onOpenChannel = onOpenChannel,
        onOpenSearch = onOpenSearch,
        onImportFromTakeout = onImportFromTakeout,
        modifier = modifier,
    )
}

/**
 * Connects the screen to its ViewModel. [viewModel] comes from the current ViewModelStoreOwner, the
 * inbox's entry on the app's back stack, whose default factory is Hilt's. The state is collected
 * with the lifecycle, so the ViewModel stops reading while the inbox doesn't show.
 */
@Composable
internal fun InboxRoute(
    onOpenVideo: (VideoId) -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    onOpenSearch: () -> Unit,
    onImportFromTakeout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InboxViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The clock is read once per answer, so the screen itself stays a function of what it gets.
    val now = remember(state.content) { Clock.System.now() }
    val zone = remember(state.content) { ZoneId.systemDefault() }
    InboxScreen(
        state = state,
        now = now,
        zone = zone,
        onOpenVideo = onOpenVideo,
        onOpenChannel = onOpenChannel,
        onOpenSearch = onOpenSearch,
        onImportFromTakeout = onImportFromTakeout,
        modifier = modifier,
    )
}
