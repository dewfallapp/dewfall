package io.github.dewfallapp.core.data.media

import javax.inject.Qualifier

/**
 * Marks the OkHttp Call.Factory for one progressive format of a video, which the YouTube layer's
 * MediaRequests makes. It isn't scoped, so inject `Provider<Call.Factory>` and take a new one for
 * each media source, because each numbers its requests from 0. See docs/architecture.md#the-player.
 */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ProgressiveMediaCalls

/**
 * Marks the OkHttp Call.Factory for a live stream's HLS manifest and segments, which the YouTube
 * layer's MediaRequests makes. See docs/architecture.md#the-player.
 */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class LiveMediaCalls
