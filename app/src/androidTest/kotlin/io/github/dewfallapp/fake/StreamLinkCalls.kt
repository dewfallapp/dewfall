package io.github.dewfallapp.fake

import io.github.dewfallapp.core.model.VideoId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Counts the calls to the fake YouTube layer's stream links for each video, answered or refused
 * because [FakeNetwork] had YouTube off, so a test can check that a video played from its files
 * asked for no links. Hilt makes one for each test. See docs/testing.md#the-fakes.
 */
class StreamLinkCalls {
    private val calls = ConcurrentHashMap<VideoId, AtomicInteger>()

    /** Counts one call for [videoId]'s links. */
    fun record(videoId: VideoId) {
        calls.getOrPut(videoId) { AtomicInteger() }.incrementAndGet()
    }

    /** How many calls asked for [videoId]'s links. */
    fun countFor(videoId: VideoId): Int = calls[videoId]?.get() ?: 0
}
