package io.github.dewfallapp.core.model

/**
 * The first page of a YouTube search, in the order YouTube gave. Search shows channels to follow
 * and videos to stream, so it leaves out playlists and ads. Either list can be empty.
 */
data class SearchResults(val channels: List<Channel>, val videos: List<VideoSummary>)
