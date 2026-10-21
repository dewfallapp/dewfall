package io.github.dewfallapp.core.logging

import java.util.Collections
import java.util.IdentityHashMap

/** The most stack frames written for each error in a summary. */
private const val MAX_FRAMES = 30

/** The most causes written after the error itself. */
private const val MAX_CAUSES = 10

/**
 * Describes [error] for a log line: the class name of the error and of each cause, each followed by
 * its stack frames. It never writes a message, a localized message or toString(), because libraries
 * put URLs, IDs and search text in them. Suppressed errors are left out. See ADR 0007.
 *
 * It writes at most [MAX_FRAMES] frames for each error and at most [MAX_CAUSES] causes. In a chain
 * that loops, the first repeated cause is named once more, and the summary ends there.
 */
internal fun errorSummary(error: Throwable): String {
    // Compared by identity, so an error class with its own equals can't hide a loop.
    val written = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    written += error
    val causes = mutableListOf<Throwable>()
    var next = error.cause
    while (next != null && next !in written && causes.size < MAX_CAUSES) {
        causes += next
        written += next
        next = next.cause
    }
    // Null at the end of the chain. Otherwise a repeated cause, or the first one past the limit.
    val stoppedAt = next
    val lines = buildList {
        addError(error)
        for (cause in causes) {
            addError(cause, prefix = "Caused by: ")
        }
        if (stoppedAt != null) {
            if (stoppedAt in written) {
                add("Caused by: ${stoppedAt.javaClass.name}, shown above")
            } else {
                add("More causes not shown")
            }
        }
    }
    return lines.joinToString("\n")
}

/** Adds the class name of [error] after [prefix], then its stack frames, one line each. */
private fun MutableList<String>.addError(error: Throwable, prefix: String = "") {
    add("$prefix${error.javaClass.name}")
    val frames = error.stackTrace
    for (frame in frames.take(MAX_FRAMES)) {
        add("  at ${frame.describe()}")
    }
    if (frames.size > MAX_FRAMES) add("  ... ${frames.size - MAX_FRAMES} more frames")
}

/**
 * The frame in the usual form, such as `a.b.C.run(C.kt:12)`, built from its parts. The JDK's own
 * toString can add a module and class loader name, which Android leaves out.
 */
private fun StackTraceElement.describe(): String {
    val place =
        when {
            isNativeMethod -> "Native Method"
            fileName == null -> "Unknown Source"
            // A negative line number means that the line is unknown.
            lineNumber >= 0 -> "$fileName:$lineNumber"
            else -> fileName
        }
    return "$className.$methodName($place)"
}
