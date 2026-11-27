package io.github.dewfallapp.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.dewfallapp.R
import io.github.dewfallapp.core.designsystem.R as DesignR
import io.github.dewfallapp.feature.channelpage.ui.ChannelPage
import io.github.dewfallapp.feature.channels.ui.Channels
import io.github.dewfallapp.feature.downloads.ui.Downloads
import io.github.dewfallapp.feature.inbox.ui.Inbox
import io.github.dewfallapp.feature.player.ui.MiniPlayer
import io.github.dewfallapp.feature.player.ui.PlayerRoute
import io.github.dewfallapp.feature.takeoutimport.ui.TakeoutImport
import io.github.dewfallapp.feature.youtubesearch.ui.YouTubeSearch

/**
 * The metadata key that marks an entry that fills the window, the video screen's, which keeps no
 * room at its bottom. See [keepRoomAtBottom].
 */
private const val FILLS_WINDOW = "io.github.dewfallapp.navigation.fillsWindow"

/**
 * The metadata key that marks an entry of a screen that shows the navigation bar, which keeps the
 * bar's room at its bottom too. See [keepRoomAtBottom].
 */
private const val SHOWS_NAVIGATION_BAR = "io.github.dewfallapp.navigation.showsNavigationBar"

/**
 * The app's screens, its back stacks, the navigation bar and the mini player under them. Only :app
 * knows Navigation 3. Features take plain actions, such as opening a video, and never see each
 * other. The back stacks and the selected destination survive configuration changes and process
 * death, and each entry keeps its own saved state and ViewModels, which are cleared when it leaves
 * its back stack. See docs/architecture.md#navigation.
 *
 * Each destination of the navigation bar, Inbox, Channels and Downloads, has its own back stack,
 * decorated on its own, so a hidden destination keeps its screens and their state. NavDisplay shows
 * the inbox's entries, then the selected destination's entries when it isn't the inbox, so going
 * back from the Channels or Downloads screen shows the inbox, as Navigation 3's multiple back
 * stacks recipe does.
 *
 * The navigation bar and the mini player lie over the bottom of NavDisplay, the mini player just
 * above the bar. The bar shows on the inbox, the Channels and Downloads screens and channel pages,
 * and the mini player while no video screen is on top. The screens end at their top edge: every
 * entry but the video screen's keeps the mini player's height at its bottom, and the bar's too when
 * it is one of the bar's screens, and gives that height up from the window insets, so a screen's
 * own safe drawing padding leaves out the system's navigation bar, and keeps the part of the
 * keyboard that reaches above them. While the video screen is on top, neither is drawn but their
 * last heights stay, so a screen that leaves for the video screen keeps its size, and a list at its
 * end keeps its place (#241). The Box behind them draws the theme's surface color, so the strip a
 * screen keeps for them during a transition matches the screens.
 */
@Composable
internal fun DewfallNavigation(modifier: Modifier = Modifier) {
    val inboxStack = rememberNavBackStack(InboxKey)
    val channelsStack = rememberNavBackStack(ChannelsKey)
    val downloadsStack = rememberNavBackStack(DownloadsKey)
    val selection = rememberSaveable { mutableStateOf(Destination.INBOX) }
    val navigation =
        remember(inboxStack, channelsStack, downloadsStack, selection) {
            AppNavigation(inboxStack, channelsStack, downloadsStack, selection)
        }
    // The last measured heights of the mini player's box and of the navigation bar, which the
    // screens keep at their bottom.
    val miniPlayerHeight = remember { mutableStateOf(0.dp) }
    val navigationBarHeight = remember { mutableStateOf(0.dp) }
    val keepRoom = remember { keepRoomAtBottom(miniPlayerHeight, navigationBarHeight) }
    val provider = entryProvider { screens(navigation) }
    val inboxEntries = rememberDecoratedStack(inboxStack, keepRoom, provider)
    val channelsEntries = rememberDecoratedStack(channelsStack, keepRoom, provider)
    val downloadsEntries = rememberDecoratedStack(downloadsStack, keepRoom, provider)
    val showsNavigationBar = navigation.showsNavigationBar()
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        NavDisplay(
            entries = navigation.shown(inboxEntries, channelsEntries, downloadsEntries),
            onBack = { navigation.goBack() },
            modifier = Modifier.fillMaxSize(),
        )
        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            if (navigation.showsMiniPlayer()) {
                MiniPlayerBox(
                    navigation = navigation,
                    aboveNavigationBar = showsNavigationBar,
                    onHeight = { miniPlayerHeight.value = it },
                )
            }
            if (showsNavigationBar) {
                DestinationsBar(
                    navigation = navigation,
                    onHeight = { navigationBarHeight.value = it },
                )
            }
        }
    }
}

/**
 * The entries of [stack], each decorated with its own saved state, its own ViewModels and
 * [keepRoom]. Each back stack gets its own decorators, so a destination's screens keep their state
 * while the other destination shows. NavDisplay's `entries` overload takes no decorators, so they
 * are applied here.
 */
@Composable
private fun rememberDecoratedStack(
    stack: List<NavKey>,
    keepRoom: NavEntryDecorator<NavKey>,
    provider: (NavKey) -> NavEntry<NavKey>,
): List<NavEntry<NavKey>> =
    rememberDecoratedNavEntries(
        backStack = stack,
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
                keepRoom,
            ),
        entryProvider = provider,
    )

/**
 * The mini player in a box that reports its height through [onHeight]. The box reports 0 when the
 * mini player draws nothing, such as after it closes, so the screens get the whole height back.
 * Above the navigation bar, which pads itself with the system's navigation bar inset, the box takes
 * that inset off, so the mini player doesn't pad for it twice.
 */
@Composable
private fun MiniPlayerBox(
    navigation: AppNavigation,
    aboveNavigationBar: Boolean,
    onHeight: (Dp) -> Unit,
) {
    val density = LocalDensity.current
    Box(
        Modifier.then(
                if (aboveNavigationBar) {
                    Modifier.consumeWindowInsets(
                        WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
                    )
                } else {
                    Modifier
                },
            )
            .onSizeChanged { onHeight(with(density) { it.height.toDp() }) },
    ) {
        MiniPlayer(onOpenVideo = { videoId -> navigation.openVideo(videoId) })
    }
}

/**
 * The navigation bar with the app's destinations, as the design system's NavigationBar notes have
 * it: the selected destination has its filled glyph and the indicator, the others their outlined
 * glyph, and every label always shows. Each glyph sits next to its own label, so it has no content
 * description of its own. The bar grows with a large font rather than cut a label off. It reports
 * its height through [onHeight].
 */
@Composable
private fun DestinationsBar(
    navigation: AppNavigation,
    onHeight: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    NavigationBar(
        containerColor = colors.surfaceContainer,
        modifier = modifier.onSizeChanged { onHeight(with(density) { it.height.toDp() }) },
    ) {
        for (destination in Destination.entries) {
            val selected = navigation.selected == destination
            NavigationBarItem(
                selected = selected,
                onClick = { navigation.select(destination) },
                icon = {
                    Icon(
                        painter =
                            painterResource(
                                if (selected) destination.filledGlyph else destination.glyph,
                            ),
                        contentDescription = null,
                    )
                },
                label = {
                    // At the largest font sizes, "Downloads" is wider than its item, so a label
                    // breaks with a hyphen, "Down-" and "loads", and the bar grows, never mid-word
                    // without one. The maintainer chose this on November 28, 2026.
                    Text(
                        text = stringResource(destination.label),
                        style =
                            LocalTextStyle.current.copy(
                                hyphens = Hyphens.Auto,
                                lineBreak = LineBreak.Paragraph,
                            ),
                        textAlign = TextAlign.Center,
                    )
                },
                alwaysShowLabel = true,
                colors =
                    NavigationBarItemDefaults.colors(
                        indicatorColor = colors.secondaryContainer,
                        selectedIconColor = colors.onSecondaryContainer,
                        selectedTextColor = colors.onSurface,
                        unselectedIconColor = colors.onSurfaceVariant,
                        unselectedTextColor = colors.onSurfaceVariant,
                    ),
            )
        }
    }
}

@get:StringRes
private val Destination.label: Int
    get() =
        when (this) {
            Destination.INBOX -> R.string.navigation_inbox
            Destination.CHANNELS -> R.string.navigation_channels
            Destination.DOWNLOADS -> R.string.navigation_downloads
        }

@get:DrawableRes
private val Destination.glyph: Int
    get() =
        when (this) {
            Destination.INBOX -> DesignR.drawable.ic_inbox
            Destination.CHANNELS -> DesignR.drawable.ic_subscriptions
            Destination.DOWNLOADS -> DesignR.drawable.ic_download_for_offline
        }

@get:DrawableRes
private val Destination.filledGlyph: Int
    get() =
        when (this) {
            Destination.INBOX -> DesignR.drawable.ic_inbox_filled
            Destination.CHANNELS -> DesignR.drawable.ic_subscriptions_filled
            Destination.DOWNLOADS -> DesignR.drawable.ic_download_for_offline_filled
        }

/**
 * The screens on the back stacks, and the actions that move between them. Only :app sees Navigation
 * 3, so each screen gets plain actions. The video screen's entry fills the window, and the entries
 * of the screens that show the navigation bar say so, for [keepRoomAtBottom]. See
 * docs/architecture.md#navigation.
 */
private fun EntryProviderScope<NavKey>.screens(navigation: AppNavigation) {
    entry<InboxKey>(metadata = mapOf(SHOWS_NAVIGATION_BAR to true)) {
        Inbox(
            onOpenVideo = { videoId -> navigation.openVideo(videoId) },
            onOpenChannel = { channelId -> navigation.openChannel(InboxKey, channelId) },
            onOpenSearch = { navigation.openSearch(InboxKey) },
            onImportFromTakeout = { navigation.openTakeoutImport(InboxKey) },
        )
    }
    entry<ChannelsKey>(metadata = mapOf(SHOWS_NAVIGATION_BAR to true)) {
        Channels(
            onOpenChannel = { channelId -> navigation.openChannel(ChannelsKey, channelId) },
            onOpenSearch = { navigation.openSearch(ChannelsKey) },
            onImportFromTakeout = { navigation.openTakeoutImport(ChannelsKey) },
        )
    }
    entry<DownloadsKey>(metadata = mapOf(SHOWS_NAVIGATION_BAR to true)) {
        Downloads(
            onOpenVideo = { videoId -> navigation.openVideo(videoId) },
            onOpenSearch = { navigation.openSearch(DownloadsKey) },
        )
    }
    entry<SearchKey>(clazzContentKey = { key -> key.entryId }) { key ->
        YouTubeSearch(
            onOpenVideo = { videoId -> navigation.openVideo(videoId) },
            onOpenChannel = { channelId -> navigation.openChannel(key, channelId) },
            onBack = { navigation.close(key) },
        )
    }
    entry<TakeoutImportKey>(clazzContentKey = { key -> key.entryId }) { key ->
        TakeoutImport(onBack = { navigation.close(key) }, onDone = { navigation.close(key) })
    }
    entry<PlayerKey>(
        clazzContentKey = { key -> key.entryId },
        metadata = mapOf(FILLS_WINDOW to true),
    ) { key ->
        PlayerRoute(
            videoId = key.videoId,
            onMinimize = { navigation.close(key) },
            onOpenChannel = { channelId -> navigation.openChannel(key, channelId) },
        )
    }
    entry<ChannelKey>(
        clazzContentKey = { key -> key.entryId },
        metadata = mapOf(SHOWS_NAVIGATION_BAR to true),
    ) { key ->
        ChannelPage(
            channelId = key.channelId,
            onOpenVideo = { videoId -> navigation.openVideo(videoId) },
            onBack = { navigation.close(key) },
        )
    }
}

/**
 * Keeps room at the bottom of every entry but one whose metadata has [FILLS_WINDOW]: [miniPlayer],
 * the mini player's last height, and also [navigationBar], the navigation bar's last height, for an
 * entry whose metadata has [SHOWS_NAVIGATION_BAR]. It takes that room off the window insets that
 * the entry sees. Doing it in one place means a new screen gets it without asking.
 */
private fun keepRoomAtBottom(
    miniPlayer: State<Dp>,
    navigationBar: State<Dp>,
): NavEntryDecorator<NavKey> = NavEntryDecorator { entry ->
    if (entry.metadata[FILLS_WINDOW] == true) {
        entry.Content()
    } else {
        val bar = if (entry.metadata[SHOWS_NAVIGATION_BAR] == true) navigationBar.value else 0.dp
        val bottom = miniPlayer.value + bar
        Box(
            Modifier.fillMaxSize()
                .padding(bottom = bottom)
                .consumeWindowInsets(PaddingValues(bottom = bottom)),
        ) {
            entry.Content()
        }
    }
}
