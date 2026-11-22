package io.github.dewfallapp.feature.channelpage.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.lifecycle.withCreationCallback
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.VideoId
import java.time.ZoneId
import kotlin.time.Clock

/**
 * The channel page for [channelId], with its ViewModel. [onOpenVideo] runs when the user taps an
 * upload, and [onBack] when the user taps the back arrow. The app's navigation gives each page its
 * own ViewModelStoreOwner, so each channel page gets its own ViewModel. See
 * openspec/specs/subscriptions/spec.md.
 */
@Composable
fun ChannelPage(
    channelId: ChannelId,
    onOpenVideo: (VideoId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChannelRoute(
        viewModel = channelViewModel(channelId),
        onOpenVideo = onOpenVideo,
        onBack = onBack,
        modifier = modifier,
    )
}

/** Connects the screen to its ViewModel. */
@Composable
internal fun ChannelRoute(
    viewModel: ChannelViewModel,
    onOpenVideo: (VideoId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The clock is read once per answer, so the screen itself stays a function of what it gets.
    val now = remember(state.load) { Clock.System.now() }
    val zone = remember(state.load) { ZoneId.systemDefault() }
    ChannelScreen(
        state = state,
        now = now,
        zone = zone,
        onIntent = viewModel::dispatch,
        onOpenVideo = onOpenVideo,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * The ViewModel for [channelId], from the current ViewModelStoreOwner's default factory, which is
 * Hilt's. Hilt's creation callback hands the ID to the ViewModel's assisted factory.
 */
@Composable
private fun channelViewModel(channelId: ChannelId): ChannelViewModel {
    val owner = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner" }
    val defaults =
        (owner as? HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras
            ?: CreationExtras.Empty
    return viewModel(
        viewModelStoreOwner = owner,
        extras =
            defaults.withCreationCallback<ChannelViewModel.Factory> { factory ->
                factory.create(ChannelArgs(channelId))
            },
    )
}
