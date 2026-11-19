@file:OptIn(UnstableApi::class)

package io.github.dewfallapp.core.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.dewfallapp.core.model.AudioFormat
import io.github.dewfallapp.core.model.Decoding
import io.github.dewfallapp.core.model.VideoFormat
import javax.inject.Inject

/**
 * Says how the phone decodes a format, with the same check that ExoPlayer's renderers make, so the
 * format choice and the renderer agree: the codec's sample MIME type, the decoders for it, and
 * whether one of them supports a Format with the codec, size and frame rate.
 */
internal class Decoders @Inject constructor(@ApplicationContext private val context: Context) {
    fun videoDecoding(format: VideoFormat): Decoding {
        val supporting =
            supporting(format.codec, MimeTypes::isVideo) { builder ->
                format.width?.let(builder::setWidth)
                format.height?.let(builder::setHeight)
                format.frameRate?.let { builder.setFrameRate(it.toFloat()) }
            }
        return when {
            supporting.any { it.hardwareAccelerated } -> Decoding.HARDWARE
            supporting.isNotEmpty() -> Decoding.SOFTWARE
            else -> Decoding.NONE
        }
    }

    fun audioDecodes(format: AudioFormat): Boolean =
        supporting(format.codec, MimeTypes::isAudio) {}.isNotEmpty()

    /** The decoders that support [codec], or none when its MIME type isn't of [kind]. */
    private fun supporting(
        codec: String?,
        kind: (String?) -> Boolean,
        describe: (Format.Builder) -> Unit,
    ): List<MediaCodecInfo> {
        val mimeType = codec?.let(MimeTypes::getMediaMimeType)?.takeIf(kind)
        if (codec == null || mimeType == null) return emptyList()
        val sample =
            Format.Builder().setSampleMimeType(mimeType).setCodecs(codec).also(describe).build()
        return decoders(mimeType).filter { it.isFormatSupported(context, sample) }
    }

    private fun decoders(mimeType: String): List<MediaCodecInfo> =
        try {
            MediaCodecUtil.getDecoderInfos(
                mimeType,
                /* secure= */ false,
                /* tunneling= */ false,
            )
        } catch (_: MediaCodecUtil.DecoderQueryException) {
            emptyList()
        }
}
