package io.github.dewfallapp.feature.youtubedebug.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.feature.youtubedebug.logic.CallStatus
import io.github.dewfallapp.feature.youtubedebug.logic.DebugCall
import io.github.dewfallapp.feature.youtubedebug.logic.Section
import io.github.dewfallapp.feature.youtubedebug.logic.YouTubeDebugIntent
import io.github.dewfallapp.feature.youtubedebug.logic.YouTubeDebugState

/** Draws the debug screen from the ViewModel's state, and sends what the user does back to it. */
@Composable
internal fun YouTubeDebugRoute(viewModel: YouTubeDebugViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    YouTubeDebugScreen(state = state, onIntent = viewModel::dispatch, modifier = modifier)
}

/**
 * The debug screen: a section for each function of the YouTube layer. Its background fills the
 * window behind the system bars. Its content scrolls inside the safe drawing area, so no text or
 * control ever sits under the system bars, the display cutout or the keyboard.
 */
@Composable
internal fun YouTubeDebugScreen(
    state: YouTubeDebugState,
    onIntent: (YouTubeDebugIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(DewfallSpacing.margin),
            verticalArrangement = Arrangement.spacedBy(DewfallSpacing.lg),
        ) {
            Text(
                text = stringResource(R.string.youtube_debug_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.youtube_debug_intro),
                style = MaterialTheme.typography.bodyMedium,
            )
            CallSection(
                call = DebugCall.SEARCH,
                title = stringResource(R.string.youtube_debug_search),
                inputLabel = stringResource(R.string.youtube_debug_search_text),
                section = state.search,
                onIntent = onIntent,
            ) {
                SearchResult(it)
            }
            CallSection(
                call = DebugCall.VIDEO_DETAILS,
                title = stringResource(R.string.youtube_debug_video_details),
                inputLabel = stringResource(R.string.youtube_debug_video_id),
                section = state.videoDetails,
                onIntent = onIntent,
                invalidInputMessage = stringResource(R.string.youtube_debug_not_a_video_id),
            ) {
                VideoDetailsResult(it)
            }
            CallSection(
                call = DebugCall.LATEST_UPLOADS,
                title = stringResource(R.string.youtube_debug_latest_uploads),
                inputLabel = stringResource(R.string.youtube_debug_channel_id),
                section = state.latestUploads,
                onIntent = onIntent,
                invalidInputMessage = stringResource(R.string.youtube_debug_not_a_channel_id),
            ) {
                UploadsResult(it)
            }
            CallSection(
                call = DebugCall.STREAM_LINKS,
                title = stringResource(R.string.youtube_debug_stream_links),
                inputLabel = stringResource(R.string.youtube_debug_video_id),
                section = state.streamLinks,
                onIntent = onIntent,
                invalidInputMessage = stringResource(R.string.youtube_debug_not_a_video_id),
            ) {
                StreamLinksResult(it)
            }
        }
    }
}

/**
 * One call's section: its input, a Run button that turns into Cancel while the call runs, and where
 * the call stands. [result] draws a successful answer's summary.
 */
@Composable
private fun <S> CallSection(
    call: DebugCall,
    title: String,
    inputLabel: String,
    section: Section<*, S>,
    onIntent: (YouTubeDebugIntent) -> Unit,
    modifier: Modifier = Modifier,
    invalidInputMessage: String? = null,
    result: @Composable (S) -> Unit,
) {
    val status = section.status
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DewfallSpacing.sm)) {
        HorizontalDivider()
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        InputField(
            initialText = section.input,
            label = inputLabel,
            errorMessage = invalidInputMessage.takeIf { status == CallStatus.InvalidInput },
            onTextChange = { onIntent(YouTubeDebugIntent.InputChanged(call, it)) },
        )
        if (status is CallStatus.Running) {
            OutlinedButton(onClick = { onIntent(YouTubeDebugIntent.Cancel(call)) }) {
                Text(text = stringResource(R.string.youtube_debug_cancel))
            }
        } else {
            Button(onClick = { onIntent(YouTubeDebugIntent.Run(call)) }) {
                Text(text = stringResource(R.string.youtube_debug_run))
            }
        }
        when (status) {
            CallStatus.Idle,
            CallStatus.InvalidInput -> Unit
            is CallStatus.Running -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(text = stringResource(R.string.youtube_debug_running))
            }
            CallStatus.Cancelled -> Text(text = stringResource(R.string.youtube_debug_cancelled))
            is CallStatus.Failed -> {
                Text(
                    text = stringResource(R.string.youtube_debug_failed, status.error.name),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is CallStatus.Succeeded -> result(status.summary)
        }
    }
}

/**
 * A one-line input field. It holds its own text, as state-based text fields do, keeps it across a
 * rotation, and reports each change to [onTextChange]. Google's guide calls this the conforming
 * approach: https://developer.android.com/develop/ui/compose/text/migrate-state-based
 */
@Composable
private fun InputField(
    initialText: String,
    label: String,
    errorMessage: String?,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textFieldState = rememberTextFieldState(initialText)
    val currentOnTextChange by rememberUpdatedState(onTextChange)
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect { currentOnTextChange(it) }
    }
    OutlinedTextField(
        state = textFieldState,
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = label) },
        supportingText = errorMessage?.let { message -> { Text(text = message) } },
        isError = errorMessage != null,
        // IDs and search text are sent as typed, so the keyboard mustn't change them.
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        lineLimits = TextFieldLineLimits.SingleLine,
    )
}
