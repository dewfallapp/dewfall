package io.github.dewfallapp.core.logging

/**
 * Where finished log lines go. The app writes them to logcat. Tests record them, or use
 * [Logger.Silent] instead. A writer gets text that [Logger] already built from constant text, typed
 * fields and an error summary, so it adds nothing of its own.
 */
fun interface LogWriter {
    /** Writes [text] at [level] with [tag]. The text can run to several lines. */
    fun write(level: LogLevel, tag: String, text: String)
}
