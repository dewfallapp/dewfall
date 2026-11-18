package io.github.dewfallapp.lint

import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UElement

private const val ANDROID_LOG_CLASS = "android.util.Log"

/** The one method of Log that LogcatWriter may use. It writes each entry with it. */
private const val LOGCAT_WRITER_METHOD = "println"

/**
 * Flags every use of android.util.Log's methods, except LogcatWriter's use of Log.println. Log's
 * priority constants, such as Log.WARN, are plain numbers that log nothing, so they stay allowed.
 * See ADR 0007.
 */
internal class AndroidLogDetector : MethodUseDetector() {
    // Every public method of android.util.Log in API 37.
    override val methodNames: List<String> =
        listOf("d", "e", "i", "v", "w", "wtf", "println", "isLoggable", "getStackTraceString")

    override fun visitMethodUse(context: JavaContext, use: UElement, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, ANDROID_LOG_CLASS)) return
        if (method.name == LOGCAT_WRITER_METHOD && LOGCAT_WRITER.contains(context, use)) return
        context.report(
            ISSUE,
            use,
            context.getLocation(use),
            "Log through the `Logger` from `:core:logging`, not `android.util.Log`",
        )
    }

    companion object {
        val ISSUE: Issue =
            loggingIssue(
                id = "DewfallAndroidLog",
                briefDescription = "android.util.Log used outside LogcatWriter",
                explanation =
                    """
                    Only `LogcatWriter` in `:app` may call `android.util.Log`, and only its \
                    `println`. Every other line goes through the `Logger` from `:core:logging`, \
                    which writes constant text, \
                    typed fields and errors without their messages, so no user data reaches \
                    logcat. A callable reference such as `Log::println` counts as a call.
                    """,
                detector = AndroidLogDetector::class.java,
            )
    }
}
