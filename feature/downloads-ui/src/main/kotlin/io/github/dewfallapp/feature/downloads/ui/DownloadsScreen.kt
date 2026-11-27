package io.github.dewfallapp.feature.downloads.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.dewfallapp.core.designsystem.R as DesignR
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.designsystem.theme.ReadableWidth
import io.github.dewfallapp.core.model.Download
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.ui.rowGroupShape
import io.github.dewfallapp.feature.downloads.logic.DownloadsIntent
import io.github.dewfallapp.feature.downloads.logic.DownloadsList
import io.github.dewfallapp.feature.downloads.logic.DownloadsState

/** The round badge behind a message's glyph, as on the Channels screen. */
private val MessageBadgeSize = 64.dp

/** What the Downloads screen reports: a row's tap, the search button's, and the rows' buttons. */
internal class DownloadsActions(
    val onOpenVideo: (VideoId) -> Unit,
    val onOpenSearch: () -> Unit,
    val onIntent: (DownloadsIntent) -> Unit,
)

/**
 * The Downloads screen: its top bar, then the "Downloading" and "On this phone" sections, or the
 * empty state, or the message that the downloads can't be read. It is stateless: it shows [state],
 * shows its messages in [snackbarHostState], and reports taps through [actions]. Before the phone's
 * first answer it shows only the top bar.
 *
 * Its background fills the window behind the system bars. Its content sits in one centered column
 * at most ReadableWidth wide, inside the safe drawing area, so nothing is under the system bars,
 * the display cutout, the mini player or the navigation bar.
 */
@Composable
internal fun DownloadsScreen(
    state: DownloadsState,
    snackbarHostState: SnackbarHostState,
    actions: DownloadsActions,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(modifier = Modifier.widthIn(max = ReadableWidth).fillMaxWidth()) {
                TopBar(onOpenSearch = actions.onOpenSearch)
                when (val list = state.list) {
                    DownloadsList.Loading -> Unit
                    is DownloadsList.Shown -> {
                        DownloadList(list = list, actions = actions, modifier = Modifier.weight(1f))
                    }
                    DownloadsList.NoDownloads -> NoDownloads(modifier = Modifier.weight(1f))
                    DownloadsList.Unreadable -> Unreadable(modifier = Modifier.weight(1f))
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = ReadableWidth),
            )
        }
    }
}

/** "Dewfall", "Downloads" under it, and the search button, as in the "Downloads" frame. */
@Composable
private fun TopBar(onOpenSearch: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = DewfallSpacing.margin, end = DewfallSpacing.xs)
                .padding(vertical = DewfallSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(
                text = stringResource(R.string.downloads_app_name),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.downloads_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSearch) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_search),
                contentDescription = stringResource(R.string.downloads_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The two sections, each a header and one card of rows. The list and its scroll state live only
 * here, so after process death the list is first laid out with its rows, and the saved scroll
 * position holds. The items hold no saveable state, so the state Android saves holds no video ID or
 * title. #57's storage meter goes above the first section.
 */
@Composable
private fun DownloadList(
    list: DownloadsList.Shown,
    actions: DownloadsActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(horizontal = DewfallSpacing.margin, vertical = DewfallSpacing.sm),
    ) {
        section(
            key = "downloading",
            title = R.string.downloads_section_downloading,
            downloads = list.downloading,
        ) { download ->
            UnfinishedRow(download = download, actions = actions)
        }
        section(
            key = "onPhone",
            title = R.string.downloads_section_on_phone,
            downloads = list.onPhone,
        ) { download ->
            FinishedRow(download = download, actions = actions)
        }
    }
}

/** A section's header and its rows in one card, or nothing when [downloads] is empty. */
private fun LazyListScope.section(
    key: String,
    @StringRes title: Int,
    downloads: List<Download>,
    row: @Composable (Download) -> Unit,
) {
    if (downloads.isEmpty()) return
    item(key = key) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.padding(horizontal = DewfallSpacing.sm)
                    .padding(top = DewfallSpacing.md, bottom = DewfallSpacing.sm)
                    .semantics { heading() },
        )
    }
    itemsIndexed(items = downloads, key = { _, item -> key + item.video.id.value }) {
        index,
        download ->
        Box(
            modifier =
                Modifier.clip(rowGroupShape(index, downloads.size))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = DewfallSpacing.sm, vertical = DewfallSpacing.xs),
        ) {
            row(download)
        }
    }
}

/** "No downloads yet": the download glyph in a round badge, the title and the body. */
@Composable
private fun NoDownloads(modifier: Modifier = Modifier) {
    Message(
        glyph = DesignR.drawable.ic_download_for_offline,
        glyphDescription = stringResource(R.string.downloads_empty_icon),
        glyphTint = MaterialTheme.colorScheme.onSurfaceVariant,
        title = stringResource(R.string.downloads_empty_title),
        body = stringResource(R.string.downloads_empty_body),
        modifier = modifier,
    )
}

/**
 * The phone couldn't read what it keeps: the error glyph, "Your downloads can't be shown" and why,
 * as the Channels screen says it. The top bar's search button still works.
 */
@Composable
private fun Unreadable(modifier: Modifier = Modifier) {
    Message(
        glyph = DesignR.drawable.ic_error,
        glyphDescription = stringResource(R.string.downloads_unreadable_icon),
        glyphTint = MaterialTheme.colorScheme.error,
        title = stringResource(R.string.downloads_unreadable_title),
        body = stringResource(R.string.downloads_unreadable_body),
        modifier = modifier,
    )
}

/**
 * A message in place of the list: a glyph in a round badge, a title and a body, as the Channels
 * screen's. It scrolls, so a large font or a small window never cuts it off.
 */
@Composable
private fun Message(
    @DrawableRes glyph: Int,
    glyphDescription: String,
    glyphTint: Color,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DewfallSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DewfallSpacing.md),
    ) {
        Box(
            modifier =
                Modifier.size(MessageBadgeSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(glyph),
                contentDescription = glyphDescription,
                tint = glyphTint,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
