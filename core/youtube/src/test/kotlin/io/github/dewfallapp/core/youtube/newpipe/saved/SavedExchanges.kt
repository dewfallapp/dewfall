package io.github.dewfallapp.core.youtube.newpipe.saved

import java.time.LocalDate
import java.util.Locale

/** The file in each saved set's folder that lists the set's requests and answers, in order. */
internal const val EXCHANGES_FILE = "exchanges.txt"

/** The folder on the test classpath, and in src/test/resources, that holds the saved sets. */
internal const val SAVED_SETS_FOLDER = "youtube"

/** The header that says what an answer's body holds. */
internal const val CONTENT_TYPE = "Content-Type"

/** The start of a comment line in exchanges.txt. */
private const val COMMENT = "#"

/** The start of each line of a request in exchanges.txt. */
private const val REQUEST = ">"

/** The start of each line of an answer in exchanges.txt. */
private const val RESPONSE = "<"

/** The start of the line that names the file of an answer's body. */
private const val BODY_FILE = "@"

/** The comment that gives the day the set was recorded. */
private const val RECORDED_ON = "$COMMENT recorded: "

/** The comment that gives the NewPipeExtractor version that recorded the set. */
private const val RECORDED_WITH = "$COMMENT newpipe-extractor: "

/** Between a header's name and its value. */
private const val HEADER_SEPARATOR = ": "

/** The blank line between two exchanges in exchanges.txt. */
private const val BLANK_LINE = "\n\n"

/** The extension of a body file whose link has none and whose content type is JSON. */
private const val JSON_EXTENSION = ".json"

/** The extension of a body file whose link has none and whose content type isn't JSON. */
private const val TEXT_EXTENSION = ".txt"

/** The digits of the number at the start of a body file's name, so the files list in order. */
private const val FILE_NUMBER_DIGITS = 2

/** A header of a saved request or answer. */
internal data class Header(val name: String, val value: String)

/**
 * A request as a saved set keeps it. Two requests match when their method, link, headers and body
 * are the same, with the headers in any order. NewPipeExtractor builds some headers with Map.of,
 * whose order changes from one JVM to the next.
 */
internal class SavedRequest(
    val method: String,
    val url: String,
    headers: List<Header>,
    val body: String?,
) {
    /**
     * The headers sorted by name, ignoring case, with the values of each name in the order sent.
     */
    val headers: List<Header> = headers.sortedBy { it.name.lowercase(Locale.ROOT) }

    /** Whether [other] is the same request, with its headers in any order. */
    fun matches(other: SavedRequest): Boolean =
        method == other.method &&
            url == other.url &&
            body == other.body &&
            comparable(headers) == comparable(other.headers)
}

/** YouTube's answer to a request, as a saved set keeps it. */
internal class SavedResponse(val code: Int, val headers: List<Header>, val body: String)

/** One request and YouTube's answer to it. */
internal class SavedExchange(val request: SavedRequest, val response: SavedResponse)

/**
 * A saved set: its requests and answers in the order that its call sent them, and when and with
 * which NewPipeExtractor version it was recorded.
 */
internal class SavedSetContent(
    val recordedOn: LocalDate,
    val newPipeExtractorVersion: String,
    val exchanges: List<SavedExchange>,
)

/** The saved set [set], read from the test classpath. */
internal fun loadSavedSet(set: SavedSet): SavedSetContent {
    val folder = "/$SAVED_SETS_FOLDER/${set.directory}/"
    fun read(file: String): String {
        val resource =
            checkNotNull(SavedSetContent::class.java.getResource(folder + file)) {
                "The saved set ${set.name} has no file $file. Record it with " +
                    "./gradlew :core:youtube:recordYouTubeResponses."
            }
        return resource.readText()
    }
    return parseSavedSet(read(EXCHANGES_FILE), ::read)
}

/**
 * The saved set that [index], the text of exchanges.txt, describes. [bodyOf] gives the text of a
 * body file by its name. docs/testing.md#saved-youtube-responses describes the format.
 */
internal fun parseSavedSet(index: String, bodyOf: (String) -> String): SavedSetContent {
    var recordedOn: LocalDate? = null
    var version: String? = null
    val exchanges = mutableListOf<SavedExchange>()
    for (block in index.trim().split(BLANK_LINE)) {
        val lines = block.lines()
        if (lines.all { it.startsWith(COMMENT) }) {
            for (line in lines) {
                if (line.startsWith(RECORDED_ON)) {
                    recordedOn = LocalDate.parse(line.removePrefix(RECORDED_ON))
                } else if (line.startsWith(RECORDED_WITH)) {
                    version = line.removePrefix(RECORDED_WITH)
                }
            }
        } else {
            exchanges += exchangeOf(lines, bodyOf)
        }
    }
    return SavedSetContent(
        checkNotNull(recordedOn) { "exchanges.txt doesn't say when the set was recorded" },
        checkNotNull(version) { "exchanges.txt doesn't name the NewPipeExtractor version" },
        exchanges,
    )
}

/**
 * The files of the saved set [set] with [content], by name: exchanges.txt, and one file for each
 * answer's body. A body keeps a file of its own, so it stays exactly as the recorder wrote it.
 */
internal fun formatSavedSet(set: SavedSet, content: SavedSetContent): Map<String, String> {
    val files = linkedMapOf<String, String>()
    val index = StringBuilder()
    index.appendLine("$COMMENT Saved YouTube responses for the set ${set.name}.")
    index.appendLine(
        "$COMMENT Written by ./gradlew :core:youtube:recordYouTubeResponses. Don't edit it by hand."
    )
    index.appendLine("$COMMENT Signed out, asking for the country GB and the language en-GB.")
    index.appendLine("$RECORDED_ON${content.recordedOn}")
    index.appendLine("$RECORDED_WITH${content.newPipeExtractorVersion}")
    for ((position, exchange) in content.exchanges.withIndex()) {
        val request = exchange.request
        val response = exchange.response
        val bodyFile = bodyFileName(position + 1, exchange)
        index.appendLine()
        index.appendLine("$REQUEST ${request.method} ${request.url}")
        for (header in request.headers) {
            index.appendLine("$REQUEST ${header.name}$HEADER_SEPARATOR${header.value}")
        }
        if (request.body != null) {
            require('\n' !in request.body) { "A request body spans lines" }
            index.appendLine(REQUEST)
            index.appendLine("$REQUEST ${request.body}")
        }
        index.appendLine("$RESPONSE ${response.code}")
        for (header in response.headers) {
            index.appendLine("$RESPONSE ${header.name}$HEADER_SEPARATOR${header.value}")
        }
        index.appendLine(RESPONSE)
        index.appendLine("$RESPONSE $BODY_FILE$bodyFile")
        files[bodyFile] = response.body
    }
    files[EXCHANGES_FILE] = index.toString()
    return files
}

/**
 * The name of the file that holds the body of the answer at [number], such as 01-visitor_id.json:
 * its place, then the last segment of its link, with an extension if the segment has none.
 */
private fun bodyFileName(number: Int, exchange: SavedExchange): String {
    val segment = exchange.request.url.substringBefore('?').substringAfterLast('/')
    val contentType =
        exchange.response.headers.firstOrNull { it.name.equals(CONTENT_TYPE, ignoreCase = true) }
    val extension =
        when {
            '.' in segment -> ""
            contentType?.value?.contains("json") == true -> JSON_EXTENSION
            else -> TEXT_EXTENSION
        }
    return "${number.toString().padStart(FILE_NUMBER_DIGITS, '0')}-$segment$extension"
}

/** The exchange in [lines], one block of exchanges.txt. */
private fun exchangeOf(lines: List<String>, bodyOf: (String) -> String): SavedExchange {
    val requestLines = lines.filter { it.startsWith(REQUEST) }.map { contentOf(it, REQUEST) }
    val responseLines = lines.filter { it.startsWith(RESPONSE) }.map { contentOf(it, RESPONSE) }
    require(requestLines.size + responseLines.size == lines.size) {
        "A line of exchanges.txt is neither a request's nor an answer's"
    }
    return SavedExchange(requestOf(requestLines), responseOf(responseLines, bodyOf))
}

/**
 * The request in [lines]: the method and link, the headers, then, after an empty line, the body.
 */
private fun requestOf(lines: List<String>): SavedRequest {
    val (method, url) = lines.first().split(' ', limit = 2)
    val separator = lines.indexOf("")
    val headerLines = if (separator < 0) lines.drop(1) else lines.subList(1, separator)
    val body = if (separator < 0) null else lines[separator + 1]
    return SavedRequest(method, url, headerLines.map(::headerOf), body)
}

/**
 * The answer in [lines]: the status code, the headers, then, after an empty line, the name of the
 * body's file.
 */
private fun responseOf(lines: List<String>, bodyOf: (String) -> String): SavedResponse {
    val separator = lines.indexOf("")
    require(separator > 0) { "An answer in exchanges.txt names no file for its body" }
    val headers = lines.subList(1, separator).map(::headerOf)
    val bodyFile = lines[separator + 1].removePrefix(BODY_FILE)
    return SavedResponse(lines.first().toInt(), headers, bodyOf(bodyFile))
}

private fun headerOf(line: String): Header {
    val (name, value) = line.split(HEADER_SEPARATOR, limit = 2)
    return Header(name, value)
}

/** The text of [line] after its [prefix] and the space that follows it. */
private fun contentOf(line: String, prefix: String): String =
    line.removePrefix(prefix).removePrefix(" ")

/** [headers] as names in lower case and values, for a comparison that ignores the names' case. */
private fun comparable(headers: List<Header>): List<Pair<String, String>> = headers.map {
    it.name.lowercase(Locale.ROOT) to it.value
}
