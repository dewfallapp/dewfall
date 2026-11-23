package io.github.dewfallapp.feature.youtubesearch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.dewfallapp.core.designsystem.R as DesignR
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.designsystem.theme.ReadableWidth
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.ui.UnsubscribeDialog
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchIntent
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchState
import java.time.ZoneId
import kotlin.time.Instant

/**
 * The Search YouTube screen: its title, the search field, and what the search found. It is
 * stateless: it shows [state], the field's [textFieldState], and time ago counted from [now] in
 * [zone], and reports what the user does through [onIntent], a tap on a video through
 * [onOpenVideo], a tap on a channel through [onOpenChannel], and a tap on the back arrow through
 * [onBack]. It shows the Unsubscribe dialog while the state asks for it.
 *
 * Its background fills the window behind the system bars. Its content sits inside the safe drawing
 * area, so nothing is under the system bars, the display cutout or the keyboard.
 */
@Composable
internal fun YouTubeSearchScreen(
    state: YouTubeSearchState,
    textFieldState: TextFieldState,
    now: Instant,
    zone: ZoneId,
    onIntent: (YouTubeSearchIntent) -> Unit,
    onOpenVideo: (VideoId) -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(modifier = Modifier.widthIn(max = ReadableWidth).fillMaxWidth()) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(horizontal = DewfallSpacing.xs),
                ) {
                    Icon(
                        painter = painterResource(DesignR.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.youtube_search_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                ScreenTitle(
                    modifier =
                        Modifier.padding(
                            start = DewfallSpacing.margin,
                            end = DewfallSpacing.margin,
                        ),
                )
                SearchField(
                    textFieldState = textFieldState,
                    onIntent = onIntent,
                    modifier =
                        Modifier.padding(
                            horizontal = DewfallSpacing.margin,
                            vertical = DewfallSpacing.md,
                        ),
                )
                SearchResultsArea(
                    results = state.results,
                    subscribedIds = state.subscribedIds,
                    now = now,
                    zone = zone,
                    onIntent = onIntent,
                    onOpenVideo = onOpenVideo,
                    onOpenChannel = onOpenChannel,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    val dialogRow = state.unsubscribeDialogRow
    if (dialogRow != null) {
        UnsubscribeDialog(
            channelName = dialogRow.name,
            onConfirm = { onIntent(YouTubeSearchIntent.UnsubscribeConfirmed(dialogRow.id)) },
            onDismiss = { onIntent(YouTubeSearchIntent.UnsubscribeCancelled) },
        )
    }
}

/** The screen's title and the line that says what it finds. */
@Composable
private fun ScreenTitle(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DewfallSpacing.xs)) {
        Text(
            text = stringResource(R.string.youtube_search_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.youtube_search_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
