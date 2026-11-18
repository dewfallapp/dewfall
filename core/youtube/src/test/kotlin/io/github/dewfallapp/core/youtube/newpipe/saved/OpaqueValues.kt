package io.github.dewfallapp.core.youtube.newpipe.saved

/**
 * The shortest text that can count as an opaque value. 16 characters of base64 hold 12 bytes,
 * enough for an ID that could tie a file to a machine or a session. Shorter random values that
 * YouTube sends, such as ei, plid, of and vm, have masking rules of their own. Video IDs have 11
 * characters, and the t parameter of a player request has 12 and comes from the fixed seed.
 */
internal const val MIN_OPAQUE_LENGTH = 16

/**
 * The keys whose opaque values a saved set keeps wherever they are, with the reason for each. A key
 * belongs here only if NewPipeExtractor reads its value or a test checks it, and its value can't
 * identify the machine or the session. The recorder masks every other opaque value, and
 * SavedResponsesPrivacyTest fails on any that isn't masked. docs/testing.md#opaque-values explains
 * the check.
 */
internal val KEPT_KEYS: Map<String, String> =
    mapOf(
        "browseId" to
            "Channel and playlist IDs. NewPipeExtractor reads them and sends them, and the tests " +
                "check channel IDs.",
        "channelId" to "Channel IDs. NewPipeExtractor reads them, and the tests check them.",
    )

/**
 * The keys whose opaque values a saved set keeps in a request, when no earlier answer in the set
 * holds the value, with the reason for each, under the same rule as [KEPT_KEYS]. Such a value is
 * one that NewPipeExtractor makes itself, and the replay compares every request, so it can't be
 * masked in a saved request. A value that an earlier answer holds came from YouTube, so it is
 * masked with the same placeholder in the answer and in the request, see [keptValuesOf].
 */
internal val KEPT_REQUEST_KEYS: Map<String, String> =
    mapOf(
        "params" to
            "The parameters that pick a channel's tab, a search's filter or a playlist's view, " +
                "which NewPipeExtractor sends as constants. The replay compares them.",
        "continuation" to
            "The request that NewPipeExtractor builds from a playlist's ID to read its first " +
                "videos. It holds that ID and a constant, and the replay compares it.",
        "cpn" to
            "The playback nonce, which NewPipeExtractor makes from the fixed seed, so it is the " +
                "same on every run. The replay compares it.",
    )

/** NewPipeExtractor's source at the version that recorded the sets, for the reasons below. */
internal const val NEWPIPE_EXTRACTOR_SOURCE =
    "https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/" +
        "schabi/newpipe/extractor/services/youtube"

/**
 * Values that NewPipeExtractor's own code holds and sends under [KEPT_REQUEST_KEYS], with the
 * reason for each. A request keeps them even when an earlier answer holds them too, because YouTube
 * names the same tabs in its answer. Each is a constant in public code, so it can't identify
 * anyone, and the replay compares it.
 */
internal val NEWPIPE_EXTRACTOR_VALUES: Map<String, String> =
    mapOf(
        "EgZ2aWRlb3PyBgQKAjoA" to
            "The params of a channel's Videos tab, which NewPipeExtractor sends for a channel's " +
                "uploads ($NEWPIPE_EXTRACTOR_SOURCE/extractors/" +
                "YoutubeChannelTabExtractor.java#L61-L62).",
        "EgdzdHJlYW1z8gYECgJ6AA" to
            "The params of a channel's Live tab, without its URL-encoded padding, which " +
                "NewPipeExtractor sends for a channel's past live streams " +
                "($NEWPIPE_EXTRACTOR_SOURCE/extractors/YoutubeChannelTabExtractor.java#L65-L66).",
    )

/** The key of text outside any string of a body, such as a number or a comment in a script. */
internal const val OUTSIDE_STRINGS = "(outside strings)"

/** The key of an object member's name in a body. */
internal const val MEMBER_NAME = "(member name)"

/** The key of a query parameter's name, or of a name in a list of name=value pairs. */
private const val PARAMETER_NAME = "(parameter name)"

/** The key of a link's host. */
internal const val HOST = "(host)"

/** The key of a link's fragment, the part after #. */
internal const val FRAGMENT = "(fragment)"

/** The key of the first path segment of a link with no host. */
private const val FIRST_SEGMENT = "(path)"

/**
 * How deep the check goes into values inside values: a link in a value, a parameter of the link,
 * URL-encoded text, and base64, each count as one step. Past this depth, every run counts as
 * opaque, so a value can't hide by being nested deeper.
 */
private const val MAX_DEPTH = 8

/** The share of printable characters above which decoded base64 is read as text. */
private const val PRINTABLE_SHARE = 0.9

/** The longest average run of one kind of character in a random mixed-case value, like base64. */
private const val MAX_RANDOM_RUN = 2.2

/** The longest average run in a random value that has a digit, which a word seldom has. */
private const val MAX_RANDOM_RUN_WITH_DIGIT = 3.0

/**
 * The shortest part of a single-case value, between hyphens and underscores, that can make it
 * opaque. Shorter parts are options such as c0x00ffffff in an image link.
 */
private const val MIN_SINGLE_CASE_PART = 12

/**
 * The shortest run that the check decodes as base64, to look inside: six characters hold the four
 * bytes of an IPv4 address.
 */
private const val MIN_BASE64_RUN = 6

/**
 * How many places, from a run's first character, base64 decoding starts at. Base64 decodes in
 * groups of four characters, so one of the first four is where a value that follows other text
 * starts its group.
 */
private const val BASE64_OFFSETS = 4

/** The longest key that a failure message prints. */
private const val MAX_PRINTED_KEY = 64

/** What a failure message prints in place of a key that may itself be opaque or real. */
internal const val OPAQUE_KEY = "(opaque key)"

/** The characters that separate values in a body. */
private val SEPARATORS = setOf('{', '}', '[', ']', ',', ':')

/** The first printable ASCII character. */
private const val FIRST_PRINTABLE = 0x20

/** The last printable ASCII character. */
private const val LAST_PRINTABLE = 0x7e

/** A run of characters that base64, base64url, base32, hex or a number can be made of. */
private val RUN = Regex("""[A-Za-z0-9_\-+/]{$MIN_OPAQUE_LENGTH,}""")

/** A run that may be base64, long enough to hold a value worth finding. */
private val BASE64_RUN = Regex("""[A-Za-z0-9_\-+/]{$MIN_BASE64_RUN,}""")

/** A key that a failure message may print: letters, digits and a few separators. */
private val PRINTABLE_KEY = Regex("""[A-Za-z0-9_.\-() ]+""")

/** The separators between the words of a printable key. */
private val KEY_SEPARATORS = Regex("""[_.\-() ]+""")

/** A link in text: with a scheme, or starting with // or /. */
private val LINK = Regex("""(?:https?:)?//[^\s"'<>]+|(?<![\w/])/[A-Za-z0-9_\-.]+/[^\s"'<>]*""")

/** The group of [LINK_PARTS] that holds a link's host. */
private const val HOST_GROUP = 1

/** The group of [LINK_PARTS] that holds a link's path. */
private const val PATH_GROUP = 2

/** The group of [LINK_PARTS] that holds a link's query. */
private const val QUERY_GROUP = 3

/** The group of [LINK_PARTS] that holds a link's fragment. */
private const val FRAGMENT_GROUP = 4

/** The parts of a link: its host, its path, its query and its fragment. */
private val LINK_PARTS =
    Regex("""^(?:[a-z][a-z0-9+.\-]*:)?(?://([^/?#]*))?([^?#]*)(?:\?([^#]*))?(?:#(.*))?""")

/** A query or a list of name=value pairs joined by &, such as an attestation challenge. */
private val PAIRS = Regex("""^[^\s=&]+=[^\s&]*(?:&[^\s=&]+=[^\s&]*)+$""")

/** A request's line in exchanges.txt, without its prefix: the method, then the link. */
private val REQUEST_LINE = Regex("""^[A-Z]+ (\S+)$""")

/**
 * A run of printable ASCII in decoded bytes that are mostly not text, long enough to be base64 of
 * its own, which the check decodes further.
 */
private val PRINTABLE_TEXT = Regex("""[\x20-\x7e]{$MIN_BASE64_RUN,}""")

/**
 * A word: an upper-case word, a lower-case word, or a capitalised or camel-case word that may end
 * with capitals and a number, such as YoutubeChannelV2.
 */
private val WORD = Regex("""[A-Z]+|[a-z]+|[0-9]+|[A-Z]?[a-z]+(?:[A-Z][a-z]+)*[A-Z]*[0-9]*""")

/**
 * A run of text in a saved set that may be opaque, and the key it sits under. [inRequest] says
 * whether it is in a request. [isCertain] says whether it is opaque on its own. One that isn't
 * still counts as opaque when its key holds a certain one in the same set, see [countsAsOpaque].
 */
internal data class OpaqueValue(
    val key: String,
    val value: String,
    val inRequest: Boolean = false,
    val isCertain: Boolean = true,
)

/** The keys of [found] that hold an opaque value that is certain on its own. */
internal fun certainKeysOf(found: List<OpaqueValue>): Set<String> =
    found.filter { it.isCertain }.map { it.key }.toSet()

/**
 * Whether [value] counts as opaque: on its own, or because it is no word and sits under a key that
 * holds a certain one in the same set, which [certainKeys] lists. So a random value that happens to
 * look like a word is still caught when others under its key aren't.
 */
internal fun countsAsOpaque(value: OpaqueValue, certainKeys: Set<String>): Boolean =
    value.isCertain || value.key in certainKeys

/**
 * The values in [exchanges], a set in order, that may stay wherever they are: every value under
 * [KEPT_KEYS], and every value under [KEPT_REQUEST_KEYS] in a request when no earlier answer holds
 * it, in any encoding that the masking knows, or when it is one of [NEWPIPE_EXTRACTOR_VALUES].
 */
internal fun keptValuesOf(exchanges: List<SavedExchange>): Set<String> {
    val kept = mutableSetOf<String>()
    val earlierAnswers = mutableListOf<String>()
    for (exchange in exchanges) {
        for (value in opaqueValuesIn(exchange)) {
            val isNew = earlierAnswers.none { answer ->
                encodedFormsOf(value.value).any { it in answer }
            }
            val isMadeByNewPipeExtractor =
                value.inRequest &&
                    value.key in KEPT_REQUEST_KEYS &&
                    (isNew || value.value in NEWPIPE_EXTRACTOR_VALUES)
            if (value.key in KEPT_KEYS || isMadeByNewPipeExtractor) kept += value.value
        }
        val answers = exchange.response.headers.map { it.value } + exchange.response.body
        earlierAnswers += answers + answers.map(::jsonUnescaped)
    }
    return kept
}

/**
 * Whether [value] may stay in a saved set: it is one of [keptValues], the set's values that
 * [keptValuesOf] keeps, or a part of one, such as a channel ID in a playlist's ID.
 */
internal fun isKept(value: OpaqueValue, keptValues: Set<String>): Boolean = keptValues.any {
    value.value in it
}

/**
 * [value] as a body or a link can carry it: as it is, and with its +, / and = URL-encoded once or
 * twice. Base64url and hex need no encoding.
 */
internal fun encodedFormsOf(value: String): List<String> {
    fun encoded(percent: String) =
        value.replace("+", "${percent}2B").replace("/", "${percent}2F").replace("=", "${percent}3D")
    return listOf(value, encoded("%"), encoded("%25")).distinct()
}

/**
 * What a failure message may print for [key]: the key, when it is made of words, or [OPAQUE_KEY],
 * when it may itself be opaque or real, such as a path segment or a host that names a CDN node.
 */
internal fun keyForMessage(key: String): String {
    val words = key.split(KEY_SEPARATORS).filter { it.isNotEmpty() }
    val isWords =
        PRINTABLE_KEY.matches(key) && words.all { WORD.matches(it) && !it.all(Char::isDigit) }
    return if (key.length <= MAX_PRINTED_KEY && isWords) key else OPAQUE_KEY
}

/**
 * The runs in [exchange] that may be opaque: in its link, its headers and its bodies, each with its
 * key. [opaqueValuesInFile] finds the same ones in the saved files.
 */
internal fun opaqueValuesIn(exchange: SavedExchange): List<OpaqueValue> =
    findersOf(exchange, records = false).flatMap { it.found }

/**
 * Every text in [exchange], at every layer that the check of opaque values reads, and every byte
 * array that it decodes from base64: the texts that [opaqueValuesIn] looks through, each with its
 * key, so the recorder can look for real values in exactly what that check decodes.
 */
internal class DecodedTexts(val texts: List<Pair<String, String>>, val bytes: List<ByteArray>)

/** The texts and bytes that the check of opaque values reads in [exchange], see [DecodedTexts]. */
internal fun decodedTextsOf(exchange: SavedExchange): DecodedTexts {
    val finders = findersOf(exchange, records = true)
    return DecodedTexts(finders.flatMap { it.texts }, finders.flatMap { it.bytes })
}

/** The finders, of the request and of the answer, after they have read [exchange]. */
private fun findersOf(exchange: SavedExchange, records: Boolean): List<OpaqueValueFinder> {
    val request = OpaqueValueFinder(inRequest = true, records)
    request.analyzeLink(exchange.request.url, 0)
    for (header in exchange.request.headers) {
        request.analyze(header.name, header.value, 0)
    }
    exchange.request.body?.let(request::analyzeBody)
    val response = OpaqueValueFinder(inRequest = false, records)
    for (header in exchange.response.headers) {
        response.analyze(header.name, header.value, 0)
    }
    response.analyzeBody(exchange.response.body)
    return listOf(request, response)
}

/**
 * The runs in the saved file [name], whose text is [text], that may be opaque, each with its key:
 * in a body file, or in each line of exchanges.txt, where lines that start with > are a request's.
 */
internal fun opaqueValuesInFile(name: String, text: String): List<OpaqueValue> {
    if (name != EXCHANGES_FILE) {
        return OpaqueValueFinder(inRequest = false).apply { analyzeBody(text) }.found
    }
    val request = OpaqueValueFinder(inRequest = true)
    val response = OpaqueValueFinder(inRequest = false)
    for (line in text.lines()) {
        val finder = if (line.startsWith(">")) request else response
        val content = line.removePrefix(">").removePrefix("<").removePrefix(" ")
        val requestLine = REQUEST_LINE.find(content)
        when {
            line.startsWith("#") -> finder.analyze(OUTSIDE_STRINGS, line, 0)
            requestLine != null && finder == request -> {
                finder.analyzeLink(requestLine.groupValues[1], 0)
            }
            content.startsWith("{") || content.startsWith("[") -> finder.analyzeBody(content)
            ": " in content -> {
                val (headerName, value) = content.split(": ", limit = 2)
                finder.analyze(headerName, value, 0)
            }
            else -> finder.analyze(OUTSIDE_STRINGS, content, 0)
        }
    }
    return request.found + response.found
}

/**
 * Whether [run] is opaque on its own: a long number, hex with digits and letters, which may have
 * hyphens as a UUID does, a mix of upper and lower case that changes from one kind of character to
 * another about as often as base64 does, or a long single-case part that mixes letters and digits,
 * such as base32 or base36. Words, such as COMPACT_VIDEO_RENDERER, YoutubeChannelV2 or
 * GetPlayer_rid, and placeholders aren't.
 */
internal fun isOpaque(run: String): Boolean =
    when {
        run.length < MIN_OPAQUE_LENGTH -> false
        run.all { it in '0'..'9' } -> true
        isHex(run) -> run.any { it in '0'..'9' } && run.any { it.isLetter() }
        isWordsOnly(run) -> false
        hasMixedCase(run) -> {
            val averageRun = run.length.toDouble() / characterRuns(run)
            averageRun <= MAX_RANDOM_RUN ||
                (run.any { it in '0'..'9' } && averageRun <= MAX_RANDOM_RUN_WITH_DIGIT)
        }
        else -> run.split('-', '_').any(::isRandomSingleCasePart)
    }

/**
 * Whether [run] may be opaque even though it doesn't look random on its own: it isn't made of
 * words, and it has a digit or both cases.
 */
private fun isTokenLike(run: String): Boolean =
    run.length >= MIN_OPAQUE_LENGTH &&
        !isWordsOnly(run) &&
        (run.any { it in '0'..'9' } || hasMixedCase(run))

/** Whether [run] is hex in one case, in parts between hyphens or underscores. */
private fun isHex(run: String): Boolean {
    val digits = run.filter { it != '-' && it != '_' }
    return digits.all { it in '0'..'9' || it in 'a'..'f' } ||
        digits.all { it in '0'..'9' || it in 'A'..'F' }
}

private fun isWordsOnly(run: String): Boolean =
    run.split('_', '-').all { it.isEmpty() || WORD.matches(it) }

private fun hasMixedCase(run: String): Boolean =
    run.any { it in 'A'..'Z' } && run.any { it in 'a'..'z' }

/**
 * Whether [part], in one case, is long and mixes letters and digits, as base32 and base36 do. How
 * often it changes between them doesn't count: base32 can have long runs of letters.
 */
private fun isRandomSingleCasePart(part: String): Boolean =
    part.length >= MIN_SINGLE_CASE_PART && part.any { it in '0'..'9' } && part.any { it.isLetter() }

/** The number of runs of one kind of character in [text]: upper, lower, digit or other. */
private fun characterRuns(text: String): Int {
    fun kind(char: Char) =
        when (char) {
            in 'A'..'Z' -> 0
            in 'a'..'z' -> 1
            in '0'..'9' -> 2
            else -> 3
        }
    return 1 + text.zipWithNext().count { (previous, next) -> kind(previous) != kind(next) }
}

/**
 * Finds the runs that may be opaque in requests, when [inRequest], or in answers, with the key each
 * sits under. When it [records], it also keeps every text it reads, at every layer, and every byte
 * array it decodes.
 */
private class OpaqueValueFinder(
    private val inRequest: Boolean,
    private val records: Boolean = false,
) {
    val found = mutableListOf<OpaqueValue>()
    val texts = mutableListOf<Pair<String, String>>()
    val bytes = mutableListOf<ByteArray>()

    /**
     * Finds them in [body], a JSON answer, a request's JSON body or a script. A string sits under
     * its member's name, or under the name of the array it is in, and a member's name under
     * [MEMBER_NAME]. Text outside strings sits under [OUTSIDE_STRINGS].
     */
    fun analyzeBody(body: String) {
        val reader = BodyReader { key, value -> analyze(key, value, 0) }
        var index = 0
        while (index < body.length) {
            index =
                if (body[index] == '"') {
                    reader.readString(body, index)
                } else {
                    reader.read(body[index], index)
                }
        }
        analyze(OUTSIDE_STRINGS, reader.outside.toString(), 0)
    }

    /**
     * Finds them in [value], which sits under [key] at [depth]: in the links it holds, in its
     * name=value pairs, in its URL-encoded text once decoded, and in its own runs, with what they
     * decode to as base64. Past [MAX_DEPTH], every run counts.
     */
    fun analyze(key: String, value: String, depth: Int) {
        if (records) texts += key to value
        when {
            value.isEmpty() -> Unit
            depth > MAX_DEPTH -> {
                for (run in RUN.findAll(value)) {
                    found += OpaqueValue(key, run.value, inRequest, isCertain = true)
                }
            }
            else -> analyzeWithin(key, value, depth)
        }
    }

    /**
     * Finds them in [link]: its host sits under [HOST], each path segment under the segment before
     * it, or the host, each query parameter under its name, and the fragment under [FRAGMENT].
     */
    fun analyzeLink(link: String, depth: Int) {
        val parts = LINK_PARTS.find(link)?.groupValues ?: return
        val host = parts[HOST_GROUP]
        val path = parts[PATH_GROUP]
        val query = parts[QUERY_GROUP]
        val fragment = parts[FRAGMENT_GROUP]
        analyze(HOST, host, depth + 1)
        var previous = host.ifEmpty { FIRST_SEGMENT }
        for (segment in path.split('/').filter { it.isNotEmpty() }) {
            analyze(previous, urlDecoded(segment), depth + 1)
            previous = segment
        }
        if (query.isNotEmpty()) analyzePairs(query, depth + 1)
        analyze(FRAGMENT, fragment, depth + 1)
    }

    private fun analyzeWithin(key: String, value: String, depth: Int) {
        for (link in LINK.findAll(value)) {
            analyzeLink(link.value, depth + 1)
        }
        val rest = LINK.replace(value, " ")
        val decoded = urlDecoded(rest)
        when {
            PAIRS.matches(rest) -> analyzePairs(rest, depth + 1)
            decoded != rest -> analyze(key, decoded, depth + 1)
            else -> analyzeRuns(key, rest, depth)
        }
    }

    /** Finds them in [pairs], name=value pairs joined by &, each value under its name. */
    private fun analyzePairs(pairs: String, depth: Int) {
        for (pair in pairs.split('&')) {
            val name = pair.substringBefore('=')
            analyze(PARAMETER_NAME, name, depth)
            analyze(name, urlDecoded(pair.substringAfter('=', "")), depth)
        }
    }

    /**
     * Adds the runs in [text] under [key]: certain ones, and ones that may be opaque, but not the
     * digits after the point of a decimal number. Then reads what each run that may be base64
     * decodes to, short ones too, from each place that a base64 group may start.
     */
    private fun analyzeRuns(key: String, text: String, depth: Int) {
        for (run in RUN.findAll(text)) {
            val isFraction =
                text.getOrNull(run.range.first - 1) == '.' && run.value.all { it.isDigit() }
            val isCertain = isOpaque(run.value)
            if (!isFraction && (isCertain || isTokenLike(run.value))) {
                found += OpaqueValue(key, run.value, inRequest, isCertain)
            }
        }
        for (run in BASE64_RUN.findAll(text)) {
            for (offset in 0 until minOf(BASE64_OFFSETS, run.value.length - MIN_BASE64_RUN + 1)) {
                val decoded = base64Bytes(run.value.substring(offset)) ?: continue
                if (records) bytes += decoded
                for (decodedText in printableTextsOf(decoded)) {
                    analyze(key, decodedText, depth + 1)
                }
            }
        }
    }
}

/**
 * Reads a body one character or string at a time, keeps track of the key of each string, and passes
 * each string with its key to [onString]. A member's name goes under [MEMBER_NAME].
 */
private class BodyReader(private val onString: (key: String, value: String) -> Unit) {
    /** The text outside strings, with a space in place of each separator. */
    val outside = StringBuilder()

    /** For each open object or array: whether it is an array, and the name it sits under. */
    private val containers = ArrayDeque<Pair<Boolean, String?>>()

    /** The name of the object member whose value comes next. */
    private var memberName: String? = null

    private val inArray: Boolean
        get() = containers.lastOrNull()?.first == true

    /** Reads the character [char] at [index], and returns the index after it. */
    fun read(char: Char, index: Int): Int {
        // A space in place of each separator, so numbers in a row don't join into one long run.
        outside.append(if (char in SEPARATORS) ' ' else char)
        when (char) {
            '{' -> containers.addLast(false to null)
            '[' -> containers.addLast(true to (memberName ?: containers.lastOrNull()?.second))
            '}',
            ']' -> {
                containers.removeLastOrNull()
                memberName = null
            }
            ',' -> if (!inArray) memberName = null
        }
        return index + 1
    }

    /**
     * Reads the string that starts at [start] in [body], as a member's name or as a value, and
     * returns the index after it.
     */
    fun readString(body: String, start: Int): Int {
        val (string, end) = stringAt(body, start)
        var next = end
        while (next < body.length && body[next].isWhitespace()) {
            next++
        }
        if (body.getOrNull(next) == ':' && !inArray) {
            memberName = string
            onString(MEMBER_NAME, string)
        } else {
            val key = (if (inArray) containers.last().second else memberName) ?: OUTSIDE_STRINGS
            onString(key, string)
            if (!inArray) memberName = null
        }
        return end
    }
}

/**
 * The text in [bytes], decoded from base64: the whole text when most of it is printable, or else
 * its printable runs that are long enough to be base64 of their own.
 */
private fun printableTextsOf(bytes: ByteArray): List<String> {
    val printable = bytes.count { it.toInt() in FIRST_PRINTABLE..LAST_PRINTABLE }
    val decoded = String(bytes, Charsets.ISO_8859_1)
    return if (bytes.isNotEmpty() && printable >= bytes.size * PRINTABLE_SHARE) {
        listOf(decoded)
    } else {
        PRINTABLE_TEXT.findAll(decoded).map { it.value }.toList()
    }
}
