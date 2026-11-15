package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.logging.LogField
import io.github.dewfallapp.core.logging.Logger
import org.schabi.newpipe.extractor.exceptions.ParsingException

/** The message of the line that a call logs when it could read YouTube's answer only in part. */
private const val READ_IN_PART = "YouTube answer read in part"

/**
 * Counts the parts of one call's answer that the layer couldn't read, so the call logs them in at
 * most one WARN line. A call creates one, uses it while it maps the answer, then calls [warnIfAny].
 * An optional part that is simply absent, such as a live stream's length, isn't counted.
 */
internal class SkippedParts(private val call: YouTubeCall) {
    private var dropped = 0
    private var parseErrors = 0

    /**
     * Adds [value], an item or a format, to [list]. A null [value] is one that misses a required
     * field, so it is left out and counted as dropped.
     */
    fun <T : Any> addOrDrop(list: MutableList<T>, value: T?) {
        if (value == null) {
            dropped++
        } else {
            list += value
        }
    }

    /** Counts parse errors that NewPipeExtractor reported, such as for an item's fields. */
    fun addParseErrors(count: Int) {
        parseErrors += count
    }

    /**
     * The optional part that [read] returns, or [default] if NewPipeExtractor couldn't parse it,
     * which counts as a parse error.
     */
    // The parse error is counted, and the call logs the count. Its message isn't kept, because it
    // can hold a link or a title, which ADR 0007 keeps out of logs.
    @Suppress("SwallowedException")
    fun <T> orDefault(default: T, read: () -> T): T =
        try {
            read()
        } catch (e: ParsingException) {
            parseErrors++
            default
        }

    /** Logs one WARN line with the counts, if anything was dropped or failed to parse. */
    fun warnIfAny(log: Logger) {
        if (dropped > 0 || parseErrors > 0) {
            log.warn(
                READ_IN_PART,
                LogField.state("call", call),
                LogField.count("dropped", dropped),
                LogField.count("parseErrors", parseErrors),
            )
        }
    }
}
