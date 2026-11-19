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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchIntent
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchState
import java.time.ZoneId
import kotlin.time.Instant

/**
 * The widest the screen's content gets. Wider windows, such as tablets, unfolded foldables and
 * split screen, center a column of this width, so lines stay readable. A phone in portrait is
 * narrower. See docs/design/brief.md.
 */
internal val ReadableWidth = 640.dp

/**
 * The Search YouTube screen: its title, the search field, and what the search found. It is
 * stateless: it shows [state], the field's [textFieldState], and time ago counted from [now] in
 * [zone], and reports what the user does through [onIntent].
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
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(modifier = Modifier.widthIn(max = ReadableWidth).fillMaxWidth()) {
                ScreenTitle(
                    modifier =
                        Modifier.padding(
                            start = DewfallSpacing.margin,
                            end = DewfallSpacing.margin,
                            top = DewfallSpacing.md,
                        )
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
                    now = now,
                    zone = zone,
                    onIntent = onIntent,
                    modifier = Modifier.weight(1f),
                )
            }
        }
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
