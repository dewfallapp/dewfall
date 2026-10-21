package io.github.dewfallapp.core.model

/** What kind of video a list item is, so a screen can leave out the kinds it doesn't show. */
enum class VideoKind {
    /**
     * A video that can be watched from start to end: a regular upload, a premiere that has
     * finished, or the recording of a live stream that has ended.
     */
    VIDEO,

    /** A YouTube Short. */
    SHORT,

    /**
     * A live stream that is on air now, or a premiere that is playing now. Both can only be watched
     * as they play.
     */
    LIVE,

    /**
     * A premiere or live stream that hasn't started. Its [VideoSummary.published] holds the
     * scheduled start, if YouTube gave one.
     */
    UPCOMING,
}
