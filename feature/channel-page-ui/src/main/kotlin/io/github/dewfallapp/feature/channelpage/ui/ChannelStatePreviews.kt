package io.github.dewfallapp.feature.channelpage.ui

import androidx.compose.runtime.Composable
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.feature.channelpage.logic.ChannelState
import io.github.dewfallapp.feature.channelpage.logic.LoadArea

// The channel page's loading, empty and failure previews, with ChannelPreviews.kt's made-up
// channel and clock. The @PreviewTest wrappers in src/screenshotTest render them.

@Composable
internal fun LoadingPreview() {
    ChannelPreview(ChannelState(openedId = previewId))
}

@Composable
internal fun NoVideosPreview() {
    ChannelPreview(loaded(rows = emptyList()).copy(reads = mapOf(previewId to null)))
}

@Composable
internal fun FailedPreview() {
    ChannelPreview(
        ChannelState(
            openedId = previewId,
            load = LoadArea.Failed(1, YouTubeError.NETWORK),
            reads = mapOf(previewId to null),
        ),
    )
}

@Composable
internal fun SubscribedOfflinePreview() {
    ChannelPreview(
        ChannelState(
            openedId = previewId,
            load = LoadArea.Failed(1, YouTubeError.NETWORK),
            reads = mapOf(previewId to previewChannel),
            shownChannel = previewChannel,
        ),
    )
}

@Composable
internal fun ClosedPreview() {
    ChannelPreview(
        ChannelState(
            openedId = previewId,
            load = LoadArea.Failed(1, YouTubeError.CHANNEL_CLOSED),
            reads = mapOf(previewId to null),
        ),
    )
}
