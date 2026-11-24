package io.github.dewfallapp.navigation

import androidx.navigation3.runtime.NavKey
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.VideoId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** The inbox, the first screen of the Inbox destination and of the app, which always stays. */
@Serializable data object InboxKey : NavKey

/** The Channels screen, the first screen of the navigation bar's Channels destination. */
@Serializable data object ChannelsKey : NavKey

/**
 * Search YouTube, which opens from the inbox and the Channels screen. [entryId] is a random UUID's
 * text, made when the key is created, and the entry's content key, as PlayerKey's is, so a quick
 * reopen during the closing screen's exit animation gets a new entry, with a new ViewModel and an
 * empty field.
 */
@Serializable data class SearchKey(val entryId: String) : NavKey

/**
 * The Takeout import screen, which opens from the "No channels yet" state of the inbox or of the
 * Channels screen. [entryId] is a random UUID's text, the entry's content key, as SearchKey's is,
 * so a quick reopen during the closing screen's exit animation gets a new entry, with a new
 * ViewModel that hasn't imported yet.
 */
@Serializable data class TakeoutImportKey(val entryId: String) : NavKey

/**
 * The video screen for [videoId]. [entryId] is a random UUID's text, made when the key is created
 * and saved with it. It is the entry's content key, which keeps each entry's saved state and
 * ViewModels apart, even for two entries of the same video, and says nothing about the video.
 *
 * The video ID is user data, so toString hides it, as VideoId's does. See ADR 0007 and
 * docs/architecture.md#navigation.
 */
@Serializable
data class PlayerKey(
    @Serializable(with = VideoIdSerializer::class) val videoId: VideoId,
    val entryId: String,
) : NavKey {
    override fun toString(): String = "PlayerKey(hidden)"
}

/**
 * The channel page for [channelId], the ID it was opened with. [entryId] is a random UUID's text,
 * made when the key is created and saved with it, and the entry's content key, as PlayerKey's is.
 *
 * The channel ID is user data, so toString hides it, as ChannelId's does. See ADR 0007 and
 * docs/architecture.md#navigation.
 */
@Serializable
data class ChannelKey(
    @Serializable(with = ChannelIdSerializer::class) val channelId: ChannelId,
    val entryId: String,
) : NavKey {
    override fun toString(): String = "ChannelKey(hidden)"
}

/**
 * Saves a VideoId as its text, and restores it through VideoId's constructor, which checks it.
 * VideoId lives in :core:model, a plain Kotlin/JVM module without the serialization plugin.
 */
internal object VideoIdSerializer : KSerializer<VideoId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.github.dewfallapp.VideoId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: VideoId) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): VideoId = VideoId(decoder.decodeString())
}

/**
 * Saves a ChannelId as its text, and restores it through ChannelId's constructor, which checks it,
 * as VideoIdSerializer does for a VideoId.
 */
internal object ChannelIdSerializer : KSerializer<ChannelId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.github.dewfallapp.ChannelId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ChannelId) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): ChannelId = ChannelId(decoder.decodeString())
}
