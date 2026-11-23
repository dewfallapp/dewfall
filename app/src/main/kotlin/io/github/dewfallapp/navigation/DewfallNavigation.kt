package io.github.dewfallapp.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.dewfallapp.feature.channelpage.ui.ChannelPage
import io.github.dewfallapp.feature.player.ui.MiniPlayer
import io.github.dewfallapp.feature.player.ui.PlayerRoute
import io.github.dewfallapp.feature.youtubesearch.ui.YouTubeSearch

/**
 * The metadata key that marks an entry that fills the window, the video screen's, which keeps no
 * room for the mini player. See [keepRoomForMiniPlayer].
 */
private const val FILLS_WINDOW = "io.github.dewfallapp.navigation.fillsWindow"

/**
 * The app's screens, the back stack between them, and the mini player under them. Only :app knows
 * Navigation 3. Features take plain actions, such as opening a video, and never see each other. The
 * back stack survives configuration changes and process death, and each entry keeps its own saved
 * state and ViewModels, which are cleared when it leaves the back stack. See
 * docs/architecture.md#navigation.
 *
 * The mini player lies over the bottom of NavDisplay while no video screen is on top, and the
 * screens end at its top edge: every entry but the video screen's keeps the mini player's height at
 * its bottom, and gives that height up from the window insets, so a screen's own safe drawing
 * padding leaves out the navigation bar, and keeps the part of the keyboard that reaches above the
 * mini player. While the video screen is on top, the mini player isn't drawn but its last height
 * stays, so a screen that leaves for the video screen keeps its size, and a list at its end keeps
 * its place (#241). The Box behind them draws the theme's surface color, so the strip a screen
 * keeps for the mini player during a transition matches the screens.
 */
@Composable
internal fun DewfallNavigation(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(SearchKey)
    val showsMiniPlayer = backStack.showsMiniPlayer()
    // The mini player's box's last measured height, which the screens keep at their bottom.
    val miniPlayerHeight = remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.goBack() },
            modifier = Modifier.fillMaxSize(),
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                    remember { keepRoomForMiniPlayer(miniPlayerHeight) },
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
                    entry<PlayerKey>(
                        clazzContentKey = { key -> key.entryId },
                        metadata = mapOf(FILLS_WINDOW to true),
                    ) { key ->
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
            // the screens get the whole height and the navigation bar's inset back.
            Box(
                Modifier.align(Alignment.BottomCenter).onSizeChanged {
                    miniPlayerHeight.value = with(density) { it.height.toDp() }
                },
            ) {
                MiniPlayer(onOpenVideo = { videoId -> backStack.openVideo(videoId) })
            }
        }
    }
}

/**
 * Keeps [height], the mini player's last height, at the bottom of every entry but one whose
 * metadata has [FILLS_WINDOW], and takes it off the window insets that the entry sees. Doing it in
 * one place means a new screen gets it without asking.
 */
private fun keepRoomForMiniPlayer(height: State<Dp>): NavEntryDecorator<NavKey> =
    NavEntryDecorator { entry ->
        if (entry.metadata[FILLS_WINDOW] == true) {
            entry.Content()
        } else {
            val bottom = height.value
            Box(
                Modifier.fillMaxSize()
                    .padding(bottom = bottom)
                    .consumeWindowInsets(PaddingValues(bottom = bottom)),
            ) {
                entry.Content()
            }
        }
    }
