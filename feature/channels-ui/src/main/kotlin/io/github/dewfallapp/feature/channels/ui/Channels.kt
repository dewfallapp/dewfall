package io.github.dewfallapp.feature.channels.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dewfallapp.core.model.ChannelId
import java.time.ZoneId
import kotlin.time.Clock

/**
 * The Channels screen, with its ViewModel: the subscribed channels, each opening its page.
 * [onOpenChannel] runs when the user taps a channel, [onOpenSearch] when they tap a search button,
 * and [onImportFromTakeout] when they tap "Import from Google Takeout". See the subscriptions spec
 * in openspec/.
 */
@Composable
fun Channels(
    onOpenChannel: (ChannelId) -> Unit,
    onOpenSearch: () -> Unit,
    onImportFromTakeout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChannelsRoute(
        onOpenChannel = onOpenChannel,
        onOpenSearch = onOpenSearch,
        onImportFromTakeout = onImportFromTakeout,
        modifier = modifier,
    )
}

/**
 * Connects the screen to its ViewModel. [viewModel] comes from the current ViewModelStoreOwner, the
 * Channels screen's entry on the app's back stack, whose default factory is Hilt's. The state is
 * collected with the lifecycle, so the ViewModel stops reading while the screen doesn't show.
 */
@Composable
internal fun ChannelsRoute(
    onOpenChannel: (ChannelId) -> Unit,
    onOpenSearch: () -> Unit,
    onImportFromTakeout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChannelsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The clock is read once per answer, so the screen itself stays a function of what it gets.
    // "Last upload today" can so stay past midnight until the list changes, as on the channel page.
    val now = remember(state.list) { Clock.System.now() }
    val zone = remember(state.list) { ZoneId.systemDefault() }
    ChannelsScreen(
        state = state,
        now = now,
        zone = zone,
        onOpenChannel = onOpenChannel,
        onOpenSearch = onOpenSearch,
        onImportFromTakeout = onImportFromTakeout,
        modifier = modifier,
    )
}
