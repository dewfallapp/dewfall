package io.github.dewfallapp.core.youtube.newpipe

import java.util.Locale

/** BCP 47's tag for an unknown language, which a track gets when YouTube's code isn't a tag. */
internal const val UNDETERMINED_LANGUAGE = "und"

/**
 * [code], a language code that YouTube gives a subtitle track, as a BCP 47 tag, or null if it isn't
 * one. NewPipeExtractor passes YouTube's code on unchanged. Most of YouTube's codes are BCP 47
 * already, such as en, pt-BR or zh-Hant, but YouTube writes Hebrew as iw, an old ISO 639 code that
 * BCP 47 replaced with he. Locale's BCP 47 functions write iw as he, and in and ji as id and yi, on
 * the JVM and on Android alike. See docs/architecture.md#subtitles.
 */
internal fun languageTagOf(code: String): String? {
    // forLanguageTag gives und for text that isn't a tag, such as en_US.
    val tag = Locale.forLanguageTag(code).toLanguageTag()
    return if (tag == UNDETERMINED_LANGUAGE && !code.equals(tag, ignoreCase = true)) null else tag
}
