package io.github.dewfallapp.lint

import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression

/** The file facade of Kotlin's print and println. */
private const val KOTLIN_CONSOLE_CLASS = "kotlin.io.ConsoleKt"

private const val THROWABLE_CLASS = "java.lang.Throwable"

private const val THREAD_CLASS = "java.lang.Thread"

private const val SYSTEM_CLASS = "java.lang.System"

/** The field of System that holds standard output. Its other stream, err, is standard error. */
private const val STANDARD_OUTPUT_FIELD = "out"

private const val MESSAGE = "Write through the `Logger` from `:core:logging`, not to the console"

/**
 * Flags code that writes to standard output or standard error: Kotlin's print and println, any use
 * of System.out and System.err, every printStackTrace, and Thread.dumpStack. ConsoleLogWriter may
 * write to standard output, with print, println or System.out, and nothing else. See ADR 0007.
 */
internal class ConsoleOutputDetector : MethodUseDetector() {
    override val methodNames: List<String> =
        listOf("print", "println", "printStackTrace", "dumpStack")

    override fun visitMethodUse(context: JavaContext, use: UElement, method: PsiMethod) {
        val evaluator = context.evaluator
        when (method.name) {
            "print",
            "println" -> {
                if (evaluator.isMemberInClass(method, KOTLIN_CONSOLE_CLASS)) {
                    reportUnlessConsoleLogWriter(context, use)
                }
            }
            "printStackTrace" -> {
                if (evaluator.isMemberInSubClassOf(method, THROWABLE_CLASS)) report(context, use)
            }
            "dumpStack" -> if (evaluator.isMemberInClass(method, THREAD_CLASS)) report(context, use)
        }
    }

    // System.out and System.err themselves, so that passing them on or keeping them in a value
    // counts too, not only calling println on them.
    override fun getApplicableReferenceNames(): List<String> = listOf("out", "err")

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement,
    ) {
        val field = referenced as? PsiField ?: return
        if (!context.evaluator.isMemberInClass(field, SYSTEM_CLASS) || !visits.isFirst(reference)) {
            return
        }
        if (field.name == STANDARD_OUTPUT_FIELD) {
            reportUnlessConsoleLogWriter(context, reference)
        } else {
            report(context, reference)
        }
    }

    private fun reportUnlessConsoleLogWriter(context: JavaContext, use: UElement) {
        if (!CONSOLE_LOG_WRITER.contains(context, use)) report(context, use)
    }

    private fun report(context: JavaContext, use: UElement) {
        context.report(ISSUE, use, context.getLocation(use), MESSAGE)
    }

    companion object {
        val ISSUE: Issue =
            loggingIssue(
                id = "DewfallConsoleOutput",
                briefDescription = "Console output outside ConsoleLogWriter",
                explanation =
                    """
                    Only `ConsoleLogWriter`, in `:core:youtube`'s tests, may write to standard \
                    output, with `print`, `println` or `System.out`. It is where the lines of the \
                    recorder of saved YouTube responses and of the live YouTube check leave the \
                    `Logger`, and the daily workflow's log is public. Every other line goes \
                    through the `Logger` from `:core:logging`. Standard error, \
                    `printStackTrace` and `Thread.dumpStack` are never allowed. `printStackTrace` \
                    writes the messages of an error and its causes, which can hold URLs and IDs.
                    """,
                detector = ConsoleOutputDetector::class.java,
            )
    }
}
