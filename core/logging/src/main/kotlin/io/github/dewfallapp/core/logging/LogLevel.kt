package io.github.dewfallapp.core.logging

/**
 * How important a log line is, from least to most. A [Logger] drops lines below its minimum level.
 * Debug builds keep every level, and release builds keep only [WARN] and [ERROR]. See ADR 0007.
 */
enum class LogLevel {
    /** Detail that helps a developer follow what the code does. */
    DEBUG,

    /** A normal event worth noting, such as a job that finished. */
    INFO,

    /** Something went wrong, and the app carried on, such as a retry. */
    WARN,

    /** Something went wrong, and the app couldn't do what it was asked to. */
    ERROR,
}
