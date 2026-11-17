package io.github.dewfallapp.core.youtube.newpipe.live

import io.github.dewfallapp.core.logging.LogField

/**
 * The pattern of every line in the results file: a kind, a verdict, then for a skipped or failed
 * kind its reason and any fields, all enum names, numbers and camelCase field names. The Report job
 * in .github/workflows/daily-youtube-check.yml checks each line against the same pattern before it
 * uses the line, so change both together.
 */
internal val RESULT_LINE: Regex =
    Regex("[A-Z0-9_]+ (PASSED|(SKIPPED|FAILED) [A-Z0-9_]+( [a-z][A-Za-z]*=[A-Z0-9_]+)*)")

/** How a kind of check ended in one run of the live check. */
internal enum class Verdict {
    /** Everything that the kind checks held. */
    PASSED,

    /**
     * YouTube or the network didn't let the call through, and the run was told to skip such calls,
     * so it says nothing about the kind.
     */
    SKIPPED,

    /** Something that the kind checks didn't hold. */
    FAILED,
}

/**
 * Why YouTube or the network didn't let a call through, so that the check couldn't check it. By
 * default the kind fails for it. With skipping turned on, as the daily workflow runs the check, the
 * kind is skipped instead, and a skipped kind never opens or comments on an issue.
 */
internal enum class UncheckedBecause {
    /** YouTube blocked the call, as it blocks some requests from data-center addresses. */
    RUNNER_BLOCKED,

    /**
     * The call, or a request for a link's first bytes, failed with NETWORK, and again after a wait.
     */
    NETWORK,
}

/** Why a kind failed. Each failing kind gets one issue. */
internal enum class FailedBecause {
    /** The call succeeded or failed when its saved set expects the other, or failed another way. */
    UNEXPECTED_RESULT,

    /** The call sent other requests than its saved set lists, leaving out sw.js. */
    REQUESTS_CHANGED,

    /** The call succeeded, but the layer logged that it read YouTube's answer only in part. */
    READ_IN_PART,

    /** The call succeeded with content that the check doesn't expect. */
    WRONG_CONTENT,

    /** The stream links expire sooner than a player needs them. */
    LINKS_EXPIRE_SOON,

    /** A link's first bytes didn't come back as a player needs them. */
    LINK_NOT_PLAYABLE,

    /** The total length that a link's answer gives differs from the format's length. */
    LENGTH_MISMATCH,

    /** Every call was skipped, so the run checked nothing. */
    EVERY_CALL_SKIPPED,

    /** The run was asked to report this kind as failed, to test the workflow's issues. */
    SIMULATED,
}

/**
 * A value written after a result's reason, as `name=VALUE`. It holds only an enum's name or a
 * count, like a [LogField], so no text from YouTube can reach the results file or the issues.
 */
internal class ResultField
private constructor(
    /** The field as the results file writes it, such as `status=403`. */
    val text: String,
    /** The same field in a log line. */
    val logField: LogField,
) {
    companion object {
        /** An enum constant, written by its name. */
        fun state(name: String, value: Enum<*>): ResultField =
            ResultField("$name=${value.name}", LogField.state(name, value))

        /** A count, which is never negative. */
        fun count(name: String, value: Int): ResultField {
            require(value >= 0) { "A count is never negative" }
            return ResultField("$name=$value", LogField.count(name, value))
        }
    }
}

/**
 * How one [kind] ended: its [verdict], and for a skipped or failed kind its [reason] and [fields].
 * A kind is a SavedSet or a [LiveKind], so its name is constant text.
 */
internal class KindResult
private constructor(
    val kind: Enum<*>,
    val verdict: Verdict,
    /** An [UncheckedBecause] or a [FailedBecause], or null when the kind passed. */
    val reason: Enum<*>?,
    val fields: List<ResultField>,
) {
    /** The result's line in the results file, which matches [RESULT_LINE]. */
    val line: String
        get() = buildString {
            append(kind.name).append(' ').append(verdict.name)
            if (reason != null) append(' ').append(reason.name)
            for (field in fields) {
                append(' ').append(field.text)
            }
        }

    companion object {
        fun passed(kind: Enum<*>): KindResult = KindResult(kind, Verdict.PASSED, null, emptyList())

        fun skipped(kind: Enum<*>, because: UncheckedBecause): KindResult =
            KindResult(kind, Verdict.SKIPPED, because, emptyList())

        fun failed(kind: Enum<*>, because: UncheckedBecause): KindResult =
            KindResult(kind, Verdict.FAILED, because, emptyList())

        fun failed(kind: Enum<*>, because: FailedBecause, vararg fields: ResultField): KindResult =
            KindResult(kind, Verdict.FAILED, because, fields.toList())
    }
}

/** The text of the results file: one line for each of [results], in order. */
internal fun formatResults(results: List<KindResult>): String =
    results.joinToString(separator = "\n", postfix = "\n") { it.line }
