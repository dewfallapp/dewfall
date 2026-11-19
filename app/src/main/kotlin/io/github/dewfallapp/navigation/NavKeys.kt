package io.github.dewfallapp.navigation

import androidx.navigation3.runtime.NavKey
import io.github.dewfallapp.core.model.VideoId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Search YouTube, the first screen on the back stack. */
@Serializable data object SearchKey : NavKey

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
