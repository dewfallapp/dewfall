package io.github.dewfallapp.core.youtube.newpipe.saved

import java.net.URLDecoder
import java.util.Base64

/** The radix of hex digits: in URL encoding, in JSON's \u escapes and in IPv6 addresses. */
internal const val HEX_RADIX = 16

/** The number of characters in a group of base64 text. */
private const val BASE64_GROUP = 4

/**
 * The number of characters left over, after whole groups, that base64 can't decode: one character
 * holds only six bits, less than a byte, so it is dropped.
 */
private const val UNDECODABLE_REMAINDER = 1

/** A URL-encoded character. */
private val URL_ENCODED = Regex("""%[0-9A-Fa-f]{2}""")

/** [text] with each URL-encoded character decoded. A plus sign stays, as base64 needs it. */
internal fun urlDecoded(text: String): String =
    URL_ENCODED.replace(text) { it.value.substring(1).toInt(HEX_RADIX).toChar().toString() }

/**
 * [text] with its URL encoding decoded as UTF-8, as a link writes a handle with letters outside
 * ASCII, or [text] as it is when it isn't valid URL encoding. A plus sign stays.
 */
internal fun utf8UrlDecoded(text: String): String =
    try {
        URLDecoder.decode(text.replace("+", "%2B"), Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        text
    }

/**
 * The bytes that [text] decodes to as base64, in either alphabet, with or without padding, and with
 * its padding, plus signs and slashes URL-encoded or not, or null if it can't be base64. A last
 * character that can't make a byte is dropped, so a value that runs on into other characters still
 * decodes from its start.
 */
internal fun base64Bytes(text: String): ByteArray? {
    val urlSafe =
        text
            .replace("%3D", "")
            .replace("%3d", "")
            .replace("%2B", "-")
            .replace("%2b", "-")
            .replace("%2F", "_")
            .replace("%2f", "_")
            .replace('+', '-')
            .replace('/', '_')
            .trimEnd('=')
    val decodable = urlSafe.all {
        it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_'
    }
    val whole =
        if (urlSafe.length % BASE64_GROUP == UNDECODABLE_REMAINDER) urlSafe.dropLast(1) else urlSafe
    return if (decodable) Base64.getUrlDecoder().decode(whole) else null
}
