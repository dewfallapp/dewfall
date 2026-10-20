package io.github.dewfallapp.core.logging

import kotlin.time.Duration

/**
 * A value logged after a message, written as `name=value`. It holds only numbers, flags, durations
 * and enum names, which carry no user data, so it has no version that takes a String. The name is
 * constant text, like the message. See ADR 0007.
 *
 * It keeps the name and the value, and [Logger] writes them only for a line it keeps, so a dropped
 * line builds no text.
 */
class LogField private constructor(private val name: String, private val value: Any) {
    /** The field as it appears in a log line. */
    internal val text: String
        get() {
            val written =
                when (value) {
                    is Duration -> value.toIsoString()
                    is Enum<*> -> value.name
                    // Int, Long or Boolean.
                    else -> value.toString()
                }
            return "$name=$written"
        }

    companion object {
        /** A count or a size, such as the number of videos or of bytes. */
        fun count(name: String, value: Int): LogField = LogField(name, value)

        /** A count or a size, such as the number of videos or of bytes. */
        fun count(name: String, value: Long): LogField = LogField(name, value)

        /** A yes or no value, written as true or false. */
        fun flag(name: String, value: Boolean): LogField = LogField(name, value)

        /** A duration, written in ISO 8601 form, such as PT2S or PT1.500S. */
        fun duration(name: String, value: Duration): LogField = LogField(name, value)

        /**
         * An enum constant, written by its name. The name is constant text in the code, so it
         * carries no user data, even when the enum's toString would.
         */
        fun state(name: String, value: Enum<*>): LogField = LogField(name, value)
    }
}
