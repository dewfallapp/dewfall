package io.github.dewfallapp.core.youtube.newpipe.saved

import io.github.dewfallapp.core.logging.LogLevel
import io.github.dewfallapp.core.logging.LogWriter

/**
 * Writes the recorder's log lines to standard output, where Gradle shows them. Every line comes
 * through the Logger from constant text and typed fields, so no real value from a recording reaches
 * it (ADR 0007).
 *
 * It is the only code in src/test that writes to standard output, as LogcatWriter is the only code
 * in the app that calls android.util.Log. AGENTS.md forbids println everywhere else, and the lint
 * check that #144 adds must allow it here.
 */
internal object ConsoleLogWriter : LogWriter {
    override fun write(level: LogLevel, tag: String, text: String) {
        println("$level $tag $text")
    }
}
