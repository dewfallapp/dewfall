package io.github.dewfallapp.core.logging

/**
 * Marks a String parameter whose argument must be constant text: a string literal, a `const val`,
 * another parameter marked this way, or `+` of those. A string template is never constant text,
 * even when every part of it is a `const val`. A value built at run time can carry user data into a
 * log line, so Dewfall's lint check DewfallLogTextNotConstant checks every call. See ADR 0007.
 *
 * [Logger] marks its messages and its tag's area, and [LogField] marks the names of its fields.
 * Mark a parameter of another function only when the function passes it on as one of those, such as
 * a function that builds a LogField. The check then covers that function's callers too.
 */
@MustBeDocumented
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class ConstantText
