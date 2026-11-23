package io.github.dewfallapp.feature.inbox.ui

import androidx.compose.runtime.Composable
import io.github.dewfallapp.core.model.UploadsCheck
import io.github.dewfallapp.core.model.UploadsCheckFailure
import io.github.dewfallapp.feature.inbox.logic.InboxContent
import io.github.dewfallapp.feature.inbox.logic.InboxState

// The inbox with a check for new videos running or stopped early, from InboxPreviews.kt's made-up
// channels and videos. The @PreviewTest wrappers in src/screenshotTest/.../InboxCheckScreenshots.kt
// render them as screenshot tests. See docs/testing.md#screenshot-tests.

private val running = UploadsCheck(running = true)

private fun stopped(failure: UploadsCheckFailure) = UploadsCheck(failure = failure)

@Composable
internal fun CheckingPreview() {
    InboxPreview(loaded(PREVIEW_CHANNELS, previewVideos).copy(check = running))
}

@Composable
internal fun CheckingDynamicPreview() {
    InboxPreview(loaded(PREVIEW_CHANNELS, previewVideos).copy(check = running), dynamicColor = true)
}

@Composable
internal fun NoConnectionPreview() {
    InboxPreview(
        loaded(PREVIEW_CHANNELS, previewVideos)
            .copy(check = stopped(UploadsCheckFailure.CANT_REACH_YOUTUBE)),
    )
}

@Composable
internal fun NoConnectionDynamicPreview() {
    InboxPreview(
        loaded(PREVIEW_CHANNELS, previewVideos)
            .copy(check = stopped(UploadsCheckFailure.CANT_REACH_YOUTUBE)),
        dynamicColor = true,
    )
}

@Composable
internal fun NotLoadingPreview() {
    InboxPreview(
        loaded(PREVIEW_CHANNELS, previewVideos)
            .copy(check = stopped(UploadsCheckFailure.YOUTUBE_NOT_LOADING)),
    )
}

@Composable
internal fun NotLoadingDynamicPreview() {
    InboxPreview(
        loaded(PREVIEW_CHANNELS, previewVideos)
            .copy(check = stopped(UploadsCheckFailure.YOUTUBE_NOT_LOADING)),
        dynamicColor = true,
    )
}

@Composable
internal fun CaughtUpNotLoadingPreview() {
    InboxPreview(
        InboxState(
            InboxContent.CaughtUp(PREVIEW_CHANNELS),
            stopped(UploadsCheckFailure.YOUTUBE_NOT_LOADING),
        ),
    )
}

@Composable
internal fun CaughtUpNotLoadingDynamicPreview() {
    InboxPreview(
        InboxState(
            InboxContent.CaughtUp(PREVIEW_CHANNELS),
            stopped(UploadsCheckFailure.YOUTUBE_NOT_LOADING),
        ),
        dynamicColor = true,
    )
}
