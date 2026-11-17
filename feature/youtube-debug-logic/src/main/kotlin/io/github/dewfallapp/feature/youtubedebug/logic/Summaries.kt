package io.github.dewfallapp.feature.youtubedebug.logic

import io.github.dewfallapp.core.model.AudioFormat
import io.github.dewfallapp.core.model.AudioTrackKind
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.SearchResults
import io.github.dewfallapp.core.model.StreamLinks
import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.VideoFormat
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import kotlin.time.Duration
import kotlin.time.Instant

// What the debug screen shows of each answer: the parts that prove a function works. The summaries
// hold no URLs, so the screen can't show a stream, thumbnail or HLS link. Stream links carry the
// phone's IP address and other details of its network. See ADR 0007 and docs/testing.md#privacy.

/** What the screen shows of a search. */
data class SearchSummary(
    val channels: List<ChannelLine>,
    val videos: List<VideoLine>,
    /** How many videos there are of each kind, for every kind, in the enum's order. */
    val videoKinds: Map<VideoKind, Int>,
)

/** What the screen shows of a channel in a list. */
data class ChannelLine(val name: String, val avatarCount: Int)

/** What the screen shows of a video in a list. */
data class VideoLine(
    val title: String,
    val channelName: String,
    val kind: VideoKind,
    val duration: Duration?,
    val published: PublishedTime?,
    val thumbnailCount: Int,
)

/** What the screen shows of a video's details. */
data class VideoDetailsSummary(
    val title: String,
    val channelName: String,
    val channelId: ChannelId,
    val duration: Duration?,
    val published: PublishedTime?,
    val isLive: Boolean,
    /** The description's length in characters. */
    val descriptionLength: Int,
    /** How many lines the description has, or 0 if it is empty. */
    val descriptionLineCount: Int,
    val thumbnailCount: Int,
    val channelAvatarCount: Int,
)

/** What the screen shows of a channel's latest uploads. */
data class UploadsSummary(
    val channelName: String,
    val channelId: ChannelId,
    /** False when YouTube sent the channel that was asked for on to another channel. */
    val isChannelAskedFor: Boolean,
    /** The videos, in the order that the YouTube layer returned them. */
    val videos: List<VideoLine>,
    /** How many videos there are of each kind, for every kind, in the enum's order. */
    val videoKinds: Map<VideoKind, Int>,
)

/** What the screen shows of a video's stream links. */
data class StreamLinksSummary(
    val audioFormats: List<AudioFormatLine>,
    val videoFormats: List<VideoFormatLine>,
    /** How many of [videoFormats] have the sound too. */
    val videoFormatsWithSound: Int,
    /** How many audio and video formats there are of each MIME type, in the order first seen. */
    val formatsPerMimeType: Map<String, Int>,
    val hasHlsManifest: Boolean,
    val expiresAt: Instant?,
)

/** What the screen shows of one audio format. Each null field is one that YouTube didn't give. */
data class AudioFormatLine(
    val itag: Int,
    val mimeType: String,
    val codec: String?,
    val bitrate: Int?,
    val contentLength: Long?,
    /** The kind of sound track, or null if the video has only one. */
    val trackKind: AudioTrackKind?,
    /** The sound track's language as a BCP 47 tag, or null if unknown or if there is one track. */
    val trackLanguageTag: String?,
)

/** What the screen shows of one video format. Each null field is one that YouTube didn't give. */
data class VideoFormatLine(
    val itag: Int,
    val mimeType: String,
    val codec: String?,
    val width: Int?,
    val height: Int?,
    val frameRate: Int?,
    val bitrate: Int?,
    val contentLength: Long?,
    val includesAudio: Boolean,
)

internal fun SearchResults.toSummary(): SearchSummary =
    SearchSummary(
        channels = channels.map { it.toLine() },
        videos = videos.map { it.toLine() },
        videoKinds = videos.kindCounts(),
    )

internal fun VideoDetails.toSummary(): VideoDetailsSummary =
    VideoDetailsSummary(
        title = title,
        channelName = channelName,
        channelId = channelId,
        duration = duration,
        published = published,
        isLive = isLive,
        descriptionLength = description.length,
        descriptionLineCount = if (description.isEmpty()) 0 else description.lines().size,
        thumbnailCount = thumbnails.size,
        channelAvatarCount = channelAvatars.size,
    )

internal fun ChannelUploads.toSummary(askedFor: ChannelId): UploadsSummary =
    UploadsSummary(
        channelName = channel.name,
        channelId = channel.id,
        isChannelAskedFor = channel.id == askedFor,
        videos = videos.map { it.toLine() },
        videoKinds = videos.kindCounts(),
    )

internal fun StreamLinks.toSummary(): StreamLinksSummary =
    StreamLinksSummary(
        audioFormats = audioFormats.map { it.toLine() },
        videoFormats = videoFormats.map { it.toLine() },
        videoFormatsWithSound = videoFormats.count { it.includesAudio },
        formatsPerMimeType =
            (audioFormats.map { it.mimeType } + videoFormats.map { it.mimeType })
                .groupingBy { it }
                .eachCount(),
        hasHlsManifest = hlsManifestUrl != null,
        expiresAt = expiresAt,
    )

private fun Channel.toLine(): ChannelLine = ChannelLine(name = name, avatarCount = avatars.size)

private fun VideoSummary.toLine(): VideoLine =
    VideoLine(
        title = title,
        channelName = channelName,
        kind = kind,
        duration = duration,
        published = published,
        thumbnailCount = thumbnails.size,
    )

private fun AudioFormat.toLine(): AudioFormatLine =
    AudioFormatLine(
        itag = itag,
        mimeType = mimeType,
        codec = codec,
        bitrate = bitrate,
        contentLength = contentLength,
        trackKind = track?.kind,
        trackLanguageTag = track?.languageTag,
    )

private fun VideoFormat.toLine(): VideoFormatLine =
    VideoFormatLine(
        itag = itag,
        mimeType = mimeType,
        codec = codec,
        width = width,
        height = height,
        frameRate = frameRate,
        bitrate = bitrate,
        contentLength = contentLength,
        includesAudio = includesAudio,
    )

/** Counts the videos of each kind, with every kind present, so a zero shows too. */
private fun List<VideoSummary>.kindCounts(): Map<VideoKind, Int> =
    VideoKind.entries.associateWith { kind -> count { it.kind == kind } }
