package io.github.dewfallapp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.dewfallapp.feature.channelpage.ui.ChannelPage
import io.github.dewfallapp.feature.player.ui.MiniPlayer
import io.github.dewfallapp.feature.player.ui.PlayerRoute
import io.github.dewfallapp.feature.youtubesearch.ui.YouTubeSearch

/**
 * The app's screens, the back stack between them, and the mini player under them. Only :app knows
 * Navigation 3. Features take plain actions, such as opening a video, and never see each other. The
 * back stack survives configuration changes and process death, and each entry keeps its own saved
 * state and ViewModels, which are cleared when it leaves the back stack. See
 * docs/architecture.md#navigation.
 *
 * The mini player sits under the screens while no video screen is on top, and the screens end at
 * its top edge: the box above it gives up the mini player's height from the window insets at its
 * bottom, so a screen's own safe drawing padding leaves out the navigation bar, and keeps the part
 * of the keyboard that reaches above the mini player.
 */
@Composable
internal fun DewfallNavigation(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(SearchKey)
    val showsMiniPlayer = backStack.showsMiniPlayer()
    var miniPlayerHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    Column(modifier = modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.goBack() },
            modifier =
                Modifier.weight(1f)
                    .consumeWindowInsets(
                        PaddingValues(bottom = if (showsMiniPlayer) miniPlayerHeight else 0.dp),
                    ),
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider =
                entryProvider {
                    entry<SearchKey> {
                        YouTubeSearch(
                            onOpenVideo = { videoId -> backStack.openVideo(videoId) },
                            onOpenChannel = { channelId ->
                                backStack.openChannel(SearchKey, channelId)
                            },
                        )
                    }
                    entry<PlayerKey>(clazzContentKey = { key -> key.entryId }) { key ->
                        PlayerRoute(
                            videoId = key.videoId,
                            onMinimize = { backStack.close(key) },
                            onOpenChannel = { channelId -> backStack.openChannel(key, channelId) },
                        )
                    }
                    entry<ChannelKey>(clazzContentKey = { key -> key.entryId }) { key ->
                        ChannelPage(
                            channelId = key.channelId,
                            onOpenVideo = { videoId -> backStack.openVideo(videoId) },
                            onBack = { backStack.close(key) },
                        )
                    }
                },
        )
        if (showsMiniPlayer) {
            // The box reports 0 when the mini player draws nothing, such as after it closes, so
            // the screens above get the navigation bar's inset back.
            Box(
                Modifier.onSizeChanged { miniPlayerHeight = with(density) { it.height.toDp() } },
            ) {
                MiniPlayer(onOpenVideo = { videoId -> backStack.openVideo(videoId) })
            }
        }
    }
}
