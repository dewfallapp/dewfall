package io.github.dewfallapp.core.logging

/**
 * The logger that every module logs through. Code receives it in its constructor, keeps
 * `logger.withTag("Area")`, and calls [debug], [info], [warn] or [error]. There is no global
 * logger. See ADR 0007.
 *
 * Messages, areas and field names must be constant text. Never log user data, such as search
 * queries, video or channel IDs, URLs, titles or subtitle text. ADR 0007 lists everything that
 * counts. Values go in [LogField]s, which hold only numbers, flags, durations and enum names. An
 * error is written as its class names and stack frames, never its messages, because libraries put
 * URLs and IDs in them.
 *
 * A line below the minimum level returns before any text is built. The class is final, so every
 * line, in the app and in tests, goes through the same formatting.
 */
class Logger
private constructor(
    // Null only for Silent, which builds nothing.
    private val writer: LogWriter?,
    private val minLevel: LogLevel,
    private val tag: String,
) {
    /** A logger that writes lines at [minLevel] and above to [writer], with the tag Dewfall. */
    constructor(writer: LogWriter, minLevel: LogLevel) : this(writer, minLevel, ROOT_TAG)

    /**
     * This logger with the tag Dewfall.<area>, such as Dewfall.YouTube. The area is constant text.
     * It replaces any area that this logger already has.
     */
    fun withTag(area: String): Logger = Logger(writer, minLevel, "$ROOT_TAG.$area")

    /** Logs detail that helps a developer follow what the code does. */
    fun debug(message: String, vararg fields: LogField) {
        log(LogLevel.DEBUG, message, fields, error = null)
    }

    /** Logs a normal event worth noting. */
    fun info(message: String, vararg fields: LogField) {
        log(LogLevel.INFO, message, fields, error = null)
    }

    /** Logs a problem that the app carried on from, with the [error] that caused it, if any. */
    fun warn(message: String, vararg fields: LogField, error: Throwable? = null) {
        log(LogLevel.WARN, message, fields, error)
    }

    /** Logs a problem that stopped the app from doing what it was asked to. */
    fun error(message: String, vararg fields: LogField, error: Throwable? = null) {
        log(LogLevel.ERROR, message, fields, error)
    }

    private fun log(
        level: LogLevel,
        message: String,
        fields: Array<out LogField>,
        error: Throwable?,
    ) {
        if (writer == null || level < minLevel) return
        val text = buildString {
            append(message)
            for (field in fields) append(' ').append(field.text)
            if (error != null) append('\n').append(errorSummary(error))
        }
        writer.write(level, tag, text)
    }

    companion object {
        private const val ROOT_TAG = "Dewfall"

        /** A logger that writes nothing and builds nothing, for tests that don't check logging. */
        val Silent: Logger = Logger(writer = null, minLevel = LogLevel.ERROR, tag = ROOT_TAG)
    }
}
