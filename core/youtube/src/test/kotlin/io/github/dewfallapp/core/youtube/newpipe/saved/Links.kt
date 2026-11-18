package io.github.dewfallapp.core.youtube.newpipe.saved

/** What the value of a link's parameter becomes when nothing reads it. */
internal const val MASKED_LINK_VALUE = "MASKED"

/**
 * The parameters of a stream link on googlevideo.com that a saved set keeps, with the reason for
 * each. NewPipeExtractor reads a format's length, type and quality from the answer's fields, not
 * from its link. Its code reads two other parameters, and a saved set masks both: c, only to fetch
 * the start of a format for a DASH manifest, which Dewfall never asks for, and n, only to decode it
 * with YouTube's player code, which no saved set fetches. docs/testing.md#links links to the code
 * that reads each one.
 */
internal val KEPT_STREAM_LINK_PARAMETERS: Map<String, String> =
    mapOf(
        "expire" to
            "When the link stops working. Dewfall's expiryOf in FormatMapping.kt reads it from " +
                "a format's link, and from the path of an HLS manifest's link, and " +
                "StreamLinksReplayTest checks it."
    )

/** The parameters of a link through YouTube's redirect that a saved set keeps, with the reason. */
internal val KEPT_REDIRECT_LINK_PARAMETERS: Map<String, String> =
    mapOf(
        "q" to
            "The page that a link in a description goes to. NewPipeExtractor reads it to write " +
                "the description " +
                "($NEWPIPE_EXTRACTOR_SOURCE/YoutubeParsingHelper.java#L692-L717), and " +
                "VideoDetailsReplayTest checks the description."
    )

/** The hosts of YouTube's own site. */
private val YOUTUBE_HOST = Regex("""(?:www\.|m\.)?youtube\.com""")

/**
 * A kind of link whose parameters say what happened in the session, or which machine or network it
 * was for: its host, its path, the parameters that a saved set keeps in it, and whether its path
 * goes on with names and values as segments, as a stream link's can. Every other parameter's value
 * becomes [MASKED_LINK_VALUE].
 */
private class TrackedLink(
    val host: Regex,
    val path: Regex,
    val kept: Set<String>,
    val hasPathParameters: Boolean = false,
)

/**
 * The links whose parameters a saved set masks: stream links on googlevideo.com, which also carry
 * them as path segments in an HLS manifest's link, links to ads and ad trackers, which also carry
 * them after semicolons in their path, the links that YouTube's player reports playback to, and
 * links through YouTube's redirect, which counts the clicks on a description's links.
 */
private val TRACKED_LINKS =
    listOf(
        TrackedLink(
            Regex("""(?:.+\.)?googlevideo\.com"""),
            Regex(".*"),
            KEPT_STREAM_LINK_PARAMETERS.keys,
            hasPathParameters = true,
        ),
        TrackedLink(
            Regex("""(?:.+\.)?(?:doubleclick\.net|googleadservices\.com|googlesyndication\.com)"""),
            Regex(".*"),
            emptySet(),
        ),
        TrackedLink(Regex("""s\.youtube\.com"""), Regex(".*"), emptySet()),
        TrackedLink(YOUTUBE_HOST, Regex("""/(?:ptracking|api/stats/|pagead/).*"""), emptySet()),
        TrackedLink(YOUTUBE_HOST, Regex("""/redirect"""), KEPT_REDIRECT_LINK_PARAMETERS.keys),
    )

/**
 * A link in a saved file, with or without its scheme. A body may write a slash as \/ and an
 * ampersand as \u0026, so the link goes on through them.
 */
private val LINK_IN_TEXT =
    Regex("""(?:https?:)?(?:\\?/){2}([A-Za-z0-9.\-]+)((?:[^\s"'<>\\]|\\/|\\u0026)*)""")

/** A slash in a link's path, as it is or as JSON may write it. */
private val PATH_SEPARATOR = Regex("""\\?/""")

/** An ampersand between a link's query parameters, as it is or as JSON may write it. */
private val QUERY_SEPARATOR = Regex("""&|\\u0026""")

/** A semicolon between parameters in a path segment, as an ad link has them. */
private val SEGMENT_SEPARATOR = Regex(""";""")

/** The path segments after which a stream link's path is made of names and values. */
private val STREAM_PATH_STARTS = listOf(listOf("api", "manifest"), listOf("videoplayback"))

/**
 * How many segments start a manifest link's path before its names and values: api, manifest and the
 * kind.
 */
private const val MANIFEST_PATH_START = 3

/**
 * [text] with the value of each parameter of a tracked link that isn't kept, and isn't a
 * placeholder already, replaced by [MASKED_LINK_VALUE], and how many it replaced.
 */
internal fun maskTrackedLinks(text: String): MaskedText {
    var count = 0
    val masked =
        LINK_IN_TEXT.replace(text) { link ->
            val tracked = trackedLinkOf(link) ?: return@replace link.value
            val prefix = link.value.substring(0, link.groups[2]!!.range.first - link.range.first)
            prefix +
                maskParameters(link.groupValues[2], tracked) { _, value ->
                    if (isPlaceholder(value)) {
                        value
                    } else {
                        count++
                        MASKED_LINK_VALUE
                    }
                }
        }
    return MaskedText(masked, count, emptySet())
}

/**
 * The names of the parameters of tracked links in [text] whose values aren't kept and aren't
 * placeholders. SavedResponsesPrivacyTest fails on any.
 */
internal fun unmaskedLinkParametersIn(text: String): List<String> {
    val unmasked = mutableListOf<String>()
    for (link in LINK_IN_TEXT.findAll(text)) {
        val tracked = trackedLinkOf(link) ?: continue
        maskParameters(link.groupValues[2], tracked) { name, value ->
            if (!isPlaceholder(value)) unmasked += name
            value
        }
    }
    return unmasked
}

/** The kind of tracked link that [link] is, or null if it isn't one. */
private fun trackedLinkOf(link: MatchResult): TrackedLink? {
    val host = link.groupValues[1]
    val path = link.groupValues[2].substringBefore('?').replace("\\/", "/")
    return TRACKED_LINKS.firstOrNull { it.host.matches(host) && it.path.matches(path) }
}

/**
 * [rest], the part of a tracked link after its host, with [mask] applied to the value of each
 * parameter that [tracked] doesn't keep, and every separator as it was. A parameter is a name=value
 * pair in the query or between semicolons in the path, or, in a stream link, a name and a value as
 * path segments. A query part without a name, and the fragment, count as values.
 */
private fun maskParameters(
    rest: String,
    tracked: TrackedLink,
    mask: (name: String, value: String) -> String,
): String {
    val fragmentStart = rest.indexOf('#').takeIf { it >= 0 } ?: rest.length
    val queryStart = rest.indexOf('?').takeIf { it in 0 until fragmentStart } ?: fragmentStart
    val path = maskPath(rest.substring(0, queryStart), tracked, mask)
    val query =
        if (queryStart < fragmentStart) {
            "?" +
                mapParts(rest.substring(queryStart + 1, fragmentStart), QUERY_SEPARATOR) {
                    maskPair(it, tracked, mask)
                }
        } else {
            ""
        }
    val fragment =
        if (fragmentStart < rest.length) "#" + mask("#", rest.substring(fragmentStart + 1)) else ""
    return path + query + fragment
}

/**
 * [path] with the values of its parameters masked: name=value pairs between semicolons in any
 * segment, and, in a stream link, the segments after [STREAM_PATH_STARTS] as names and values.
 */
private fun maskPath(
    path: String,
    tracked: TrackedLink,
    mask: (name: String, value: String) -> String,
): String {
    val segments = path.split(PATH_SEPARATOR)
    // The first segment is empty, before the path's first slash.
    val names = segments.drop(1)
    val pairsStart =
        when {
            !tracked.hasPathParameters -> null
            names.take(2) == STREAM_PATH_STARTS[0] -> MANIFEST_PATH_START
            names.take(1) == STREAM_PATH_STARTS[1] -> 1
            else -> null
        }
    var position = 0
    return mapParts(path, PATH_SEPARATOR) { segment ->
        val index = position - 1
        position++
        when {
            pairsStart != null && index >= pairsStart && (index - pairsStart) % 2 == 1 -> {
                val name = names[index - 1]
                if (name in tracked.kept) segment else mask(name, segment)
            }
            '=' in segment -> mapParts(segment, SEGMENT_SEPARATOR) { maskPair(it, tracked, mask) }
            else -> segment
        }
    }
}

/** [pair], name=value, with [mask] applied to its value unless [tracked] keeps the name. */
private fun maskPair(
    pair: String,
    tracked: TrackedLink,
    mask: (name: String, value: String) -> String,
): String {
    val name = pair.substringBefore('=', "")
    return when {
        pair.isEmpty() -> pair
        '=' !in pair -> mask(name, pair)
        name in tracked.kept -> pair
        else -> "$name=" + mask(name, pair.substringAfter('='))
    }
}

/** [text] with [transform] applied to each part between matches of [separator], which stay. */
private fun mapParts(text: String, separator: Regex, transform: (String) -> String): String {
    val result = StringBuilder()
    var index = 0
    for (match in separator.findAll(text)) {
        result.append(transform(text.substring(index, match.range.first))).append(match.value)
        index = match.range.last + 1
    }
    return result.append(transform(text.substring(index))).toString()
}
