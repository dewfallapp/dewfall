package io.github.dewfallapp.core.youtube.newpipe.saved

import io.github.dewfallapp.core.logging.LogLevel
import io.github.dewfallapp.core.logging.LogWriter

/**
 * Writes the log lines of the recorder and of the live check to standard output, where Gradle shows
 * them. Every line comes through the Logger from constant text and typed fields, so no real value
 * from a recording or from YouTube's answers reaches it (ADR 0007). The daily workflow's log is
 * public, so that matters there most.
 *
 * It is the only code in src/test that writes to standard output, as LogcatWriter is the only code
 * in the app that calls android.util.Log. Dewfall's lint check DewfallConsoleOutput allows standard
 * output here, in this class in :core:youtube's tests, flags console output anywhere else, and
 * flags standard error everywhere.
 */
internal object ConsoleLogWriter : LogWriter {
    override fun write(level: LogLevel, tag: String, text: String) {
        println("$level $tag $text")
    }
}
