package io.github.dewfallapp.feature.player.ui

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
import io.github.dewfallapp.core.model.VideoId
import java.time.ZoneId
import kotlin.time.Clock

/**
 * The video screen for [videoId], with its ViewModel. [onClose] runs when the user taps the close
 * button. The app's navigation gives each screen its own ViewModelStoreOwner, so each video gets
 * its own ViewModel. See openspec/specs/player/spec.md.
 */
@Composable
fun PlayerRoute(videoId: VideoId, onClose: () -> Unit, modifier: Modifier = Modifier) {
    PlayerContent(onClose = onClose, viewModel = playerViewModel(videoId), modifier = modifier)
}

/** Connects the screen to its ViewModel. */
@Composable
internal fun PlayerContent(
    onClose: () -> Unit,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The clock is read once per answer, so the screen itself stays a function of what it gets.
    val now = remember(state.details) { Clock.System.now() }
    val zone = remember(state.details) { ZoneId.systemDefault() }
    PlayerScreen(
        state = state,
        now = now,
        zone = zone,
        onIntent = viewModel::dispatch,
        onClose = onClose,
        modifier = modifier,
    )
}

/**
 * The ViewModel for [videoId], from the current ViewModelStoreOwner's default factory, which is
 * Hilt's. Hilt's creation callback hands the ID to the ViewModel's assisted factory.
 */
@Composable
private fun playerViewModel(videoId: VideoId): PlayerViewModel {
    val owner = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner" }
    val defaults =
        (owner as? HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras
            ?: CreationExtras.Empty
    return viewModel(
        viewModelStoreOwner = owner,
        extras =
            defaults.withCreationCallback<PlayerViewModel.Factory> { factory ->
                factory.create(PlayerArgs(videoId))
            },
    )
}
