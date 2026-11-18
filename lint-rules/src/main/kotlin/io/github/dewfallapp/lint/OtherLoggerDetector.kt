package io.github.dewfallapp.lint

import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

/**
 * The classes whose every method counts as using another logger: java.util.logging's Logger and
 * LogManager, Java's System.Logger and System.LoggerFinder, and Android's event log. Subclasses
 * count too.
 */
private val loggerClasses =
    listOf(
        "java.util.logging.Logger",
        "java.util.logging.LogManager",
        "java.lang.System.Logger",
        "java.lang.System.LoggerFinder",
        "android.util.EventLog",
    )

private const val SYSTEM_CLASS = "java.lang.System"

/** System's one method that hands out a System.Logger. */
private const val SYSTEM_GET_LOGGER = "getLogger"

/**
 * Classes whose constructor alone makes a way to write log lines: android.util.LogPrinter, which
 * writes to logcat, and java.util.logging's handlers, which write a record without a Logger.
 */
private val loggerConstructorClasses =
    listOf(
        "android.util.LogPrinter",
        "java.util.logging.ConsoleHandler",
        "java.util.logging.FileHandler",
        "java.util.logging.MemoryHandler",
        "java.util.logging.SocketHandler",
        "java.util.logging.StreamHandler",
    )

private const val MESSAGE = "Log through the `Logger` from `:core:logging`, not another logger"

/**
 * Flags every use of another logging API: java.util.logging, Java's System.Logger,
 * android.util.LogPrinter and android.util.EventLog. They write lines that bypass the Logger from
 * :core:logging. Nothing may use them. See ADR 0007.
 */
internal class OtherLoggerDetector : MethodUseDetector() {
    // Every public method of the classes in loggerClasses, in JDK 17 and Android API 37, and
    // System.getLogger.
    override val methodNames: List<String> =
        listOf(
            "addConfigurationListener",
            "addHandler",
            "addLogger",
            "checkAccess",
            "config",
            "entering",
            "exiting",
            "fine",
            "finer",
            "finest",
            "getAnonymousLogger",
            "getFilter",
            "getGlobal",
            "getHandlers",
            "getLevel",
            "getLocalizedLogger",
            "getLogManager",
            "getLogger",
            "getLoggerFinder",
            "getLoggerNames",
            "getLoggingMXBean",
            "getName",
            "getParent",
            "getProperty",
            "getResourceBundle",
            "getResourceBundleName",
            "getTagCode",
            "getTagName",
            "getUseParentHandlers",
            "info",
            "isLoggable",
            "log",
            "logp",
            "logrb",
            "readConfiguration",
            "readEvents",
            "removeConfigurationListener",
            "removeHandler",
            "reset",
            "setFilter",
            "setLevel",
            "setParent",
            "setResourceBundle",
            "setUseParentHandlers",
            "severe",
            "throwing",
            "updateConfiguration",
            "warning",
            "writeEvent",
        )

    override fun visitMethodUse(context: JavaContext, use: UElement, method: PsiMethod) {
        val evaluator = context.evaluator
        val usesAnotherLogger =
            loggerClasses.any { evaluator.isMemberInSubClassOf(method, it) } ||
                (method.name == SYSTEM_GET_LOGGER &&
                    evaluator.isMemberInClass(method, SYSTEM_CLASS))
        if (usesAnotherLogger) context.report(ISSUE, use, context.getLocation(use), MESSAGE)
    }

    override fun getApplicableConstructorTypes(): List<String> = loggerConstructorClasses

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        if (visits.isFirst(node)) context.report(ISSUE, node, context.getLocation(node), MESSAGE)
    }

    companion object {
        val ISSUE: Issue =
            loggingIssue(
                id = "DewfallOtherLogger",
                briefDescription = "Another logging API used",
                explanation =
                    """
                    Every line goes through the `Logger` from `:core:logging`, which keeps user \
                    data out. Other logging APIs bypass it: `java.util.logging`, which Android \
                    sends to logcat, Java's `System.Logger`, `android.util.LogPrinter`, which \
                    writes to logcat, and `android.util.EventLog`. Nothing may use them.
                    """,
                detector = OtherLoggerDetector::class.java,
            )
    }
}
