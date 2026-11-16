package io.github.dewfallapp.core.youtube.newpipe.saved

/** What each string in data about a viewer becomes. */
internal const val MASKED_VIEWER_DATA = "VIEWER_DATA"

/** What each number in data about a viewer, such as a comment's likes, becomes. */
private const val MASKED_VIEWER_NUMBER = "0"

/** The member that names an object's schema.org type. Its value stays, as it names no one. */
private const val TYPE_MEMBER = "@type"

/** The end of the schema.org type of a comment, such as https://schema.org/Comment. */
private const val COMMENT_TYPE = "Comment"

/**
 * The start of the name of a member whose value is about the people who watch, rather than the
 * channels that upload: comments and their replies, the teaser of a video's comments, and live chat
 * messages. A member that only counts comments is masked too, which costs nothing, as no call reads
 * it.
 */
private val VIEWER_MEMBER = Regex("""^(?:comment|teaser|liveChat)""", RegexOption.IGNORE_CASE)

/**
 * The start of the objects whose values are the experiments that YouTube put the visitor in:
 * sw.js's EXPERIMENT_FLAGS, and the experiments and experimentFlags of the Android client's player
 * answer.
 */
private val EXPERIMENT_FLAGS =
    Regex(
        """"(?:EXPERIMENT_FLAGS|EXPERIMENTS_FORCED_FLAGS|experiments|experimentFlags)"\s*:\s*\{"""
    )

/**
 * What a flag's value becomes, by what it is, so a script stays valid: false, 0, an empty string,
 * or an empty list or object. null stays.
 */
private val MASKED_FLAG_VALUES = setOf("false", "0", "\"\"", "[]", "{}", "null")

/** A handle written on its own, such as a comment author's alternateName. */
private val HANDLE = Regex("""@[^\s@/?#"]+""")

/** A link to a person's channel page: by handle, or by channel ID. */
private val PROFILE_LINK = Regex("""youtube\.com/((?:@[^/?#"\s]+)|(?:channel/[A-Za-z0-9_\-]+))""")

/**
 * [text] after masking, how many values it replaced, and the real values it replaced. [profiles]
 * are the handles and channel IDs in links to the pages of the viewers it masked.
 */
internal class MaskedText(
    val text: String,
    val count: Int,
    val realValues: Set<String>,
    val profiles: Set<String> = emptySet(),
)

/**
 * [text], a body, with every string and number in data about a viewer replaced: in an object of the
 * schema.org type Comment, such as the comments in a video's microformat, and under the members
 * that [VIEWER_MEMBER] names. Member names and @type values stay. Text that isn't JSON comes back
 * as it is. The real values come back too, with every handle and channel ID apart: a string that is
 * a handle, and the handle or channel ID in a link to a viewer's page, also decoded from its URL
 * encoding, so the recorder can look for them anywhere else, whatever their length.
 */
internal fun maskViewerData(text: String): MaskedText {
    val root = parseJsonBody(text) ?: return MaskedText(text, 0, emptySet())
    val toMask = viewerValuesIn(root).filter { !isMaskedViewerValue(it) }
    val strings = toMask.filterIsInstance<JsonString>().map { it.value }
    val linked = strings.flatMap { string ->
        PROFILE_LINK.findAll(string).map { it.groupValues[1].removePrefix("channel/") }
    }
    val profiles = linked + linked.map(::utf8UrlDecoded) + strings.filter { HANDLE.matches(it) }
    val replacements = toMask.map { value ->
        value to if (value is JsonString) "\"$MASKED_VIEWER_DATA\"" else MASKED_VIEWER_NUMBER
    }
    return MaskedText(
        replaceValues(text, replacements),
        toMask.size,
        strings.toSet(),
        profiles.toSet(),
    )
}

/**
 * Whether [text], a saved file, holds data about a viewer that isn't masked, or looks like a JSON
 * body but can't be read, so the check can't tell.
 */
internal fun hasUnmaskedViewerData(text: String): Boolean {
    val root = parseJsonBody(text)
    return if (root == null) {
        looksLikeJsonBody(text)
    } else {
        viewerValuesIn(root).any { !isMaskedViewerValue(it) }
    }
}

/** The strings and numbers in [value] that are data about a viewer, see [maskViewerData]. */
private fun viewerValuesIn(value: JsonValue, inViewerData: Boolean = false): List<JsonValue> =
    when (value) {
        is JsonObject -> {
            val isComment =
                value.members.any { (name, member) ->
                    name == TYPE_MEMBER &&
                        (member as? JsonString)?.value?.endsWith(COMMENT_TYPE) == true
                }
            value.members
                .filter { (name, member) -> name != TYPE_MEMBER || member !is JsonString }
                .flatMap { (name, member) ->
                    viewerValuesIn(
                        member,
                        inViewerData || isComment || VIEWER_MEMBER.containsMatchIn(name),
                    )
                }
        }
        is JsonArray -> value.items.flatMap { viewerValuesIn(it, inViewerData) }
        is JsonString -> if (inViewerData) listOf(value) else emptyList()
        is JsonLiteral ->
            if (inViewerData && value.text.toDoubleOrNull() != null) listOf(value) else emptyList()
    }

/**
 * Whether [value] is masked already: a placeholder, which another kind of masking may have written
 * first, such as an event ID's, or 0.
 */
private fun isMaskedViewerValue(value: JsonValue): Boolean =
    when (value) {
        is JsonString -> isPlaceholder(value.value)
        is JsonLiteral -> value.text == MASKED_VIEWER_NUMBER
        else -> false
    }

/**
 * [text] with the value of every flag in the objects that [EXPERIMENT_FLAGS] names replaced by
 * false, 0, an empty string, or an empty list or object. Which flags are on, and their numbers, say
 * which experiments YouTube put the visitor in, and some change from one visitor to the next. The
 * flags' names stay.
 */
internal fun maskExperimentFlags(text: String): MaskedText {
    val replacements =
        flagValuesIn(text).mapNotNull { (flag, source) ->
            val masked =
                when (flag) {
                    is JsonString -> "\"\""
                    is JsonArray -> "[]"
                    is JsonObject -> "{}"
                    is JsonLiteral ->
                        if (flag.text.toDoubleOrNull() != null) "0" else maskedLiteral(flag)
                }
            if (source == masked) null else flag to masked
        }
    return MaskedText(replaceValues(text, replacements), replacements.size, emptySet())
}

/**
 * Whether [text] has a flag whose value isn't masked, or an object of flags that can't be read, so
 * the check can't tell.
 */
internal fun hasUnmaskedExperimentFlags(text: String): Boolean {
    val objects = EXPERIMENT_FLAGS.findAll(text).map { parseJsonAt(text, it.range.last) }.toList()
    return objects.any { it !is JsonObject } ||
        flagValuesIn(text).any { (_, source) -> source !in MASKED_FLAG_VALUES }
}

/** Each flag's value in the objects of [text] that [EXPERIMENT_FLAGS] names, with its text. */
private fun flagValuesIn(text: String): List<Pair<JsonValue, String>> =
    EXPERIMENT_FLAGS.findAll(text)
        .mapNotNull { parseJsonAt(text, it.range.last) as? JsonObject }
        .flatMap { flags -> flags.members.map { (_, value) -> value } }
        .map { it to text.substring(it.start, it.end) }
        .toList()

/** What a flag that is true, false or null becomes: false for true and false, null for null. */
private fun maskedLiteral(flag: JsonLiteral): String = if (flag.text == "null") "null" else "false"
