package io.github.dewfallapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.dewfallapp.feature.player.ui.PlayerRoute
import io.github.dewfallapp.feature.youtubesearch.ui.YouTubeSearch

/**
 * The app's screens and the back stack between them. Only :app knows Navigation 3. Features take
 * plain actions, such as opening a video, and never see each other. The back stack survives
 * configuration changes and process death, and each entry keeps its own saved state and ViewModels,
 * which are cleared when it leaves the back stack. See docs/architecture.md#navigation.
 */
@Composable
internal fun DewfallNavigation(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(SearchKey)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.goBack() },
        modifier = modifier,
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<SearchKey> {
                    YouTubeSearch(onOpenVideo = { videoId -> backStack.openVideo(videoId) })
                }
                entry<PlayerKey>(clazzContentKey = { key -> key.entryId }) { key ->
                    PlayerRoute(videoId = key.videoId, onClose = { backStack.close(key) })
                }
            },
    )
}
