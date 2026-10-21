package io.github.dewfallapp.core.youtube.newpipe

/**
 * Which of the YouTube layer's functions a call into NewPipeExtractor serves, for log lines. The
 * names are constant text, so LogField.state may log them. See ADR 0007.
 */
internal enum class YouTubeCall {
    SEARCH,
    VIDEO_DETAILS,
    LATEST_UPLOADS,
    STREAM_LINKS,
}
