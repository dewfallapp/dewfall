package io.github.dewfallapp.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UElement

/**
 * Flags every argument for a @ConstantText parameter that isn't constant text, and every callable
 * reference to a function with such a parameter, such as `log::info`, because the code that calls
 * it later can pass any text. Constant text is a string literal, a `const val`, a @ConstantText
 * parameter, or `+` of those. A string template never counts, even when all its parts are constant.
 * See ADR 0007.
 */
internal class ConstantLogTextDetector : Detector(), SourceCodeScanner {
    /** The arguments and references visited in the current file, so each is reported once. */
    private val visits = VisitsInFile()

    override fun beforeCheckFile(context: Context) {
        visits.clear()
    }

    override fun applicableAnnotations(): List<String> = listOf(CONSTANT_TEXT_ANNOTATION)

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
        type == AnnotationUsageType.METHOD_CALL_PARAMETER

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo,
    ) {
        val argument = writtenArgument(element) ?: return
        if (!visits.isFirst(argument) || isConstantText(context, argument)) return
        val parameterName = (annotationInfo.annotated as? PsiParameter)?.name ?: "text"
        context.report(
            ISSUE,
            argument,
            context.getLocation(argument),
            "`$parameterName` must be constant text: a string literal, a `const val`, a " +
                "`@ConstantText` parameter, or `+` of those, never a string template or a value " +
                "built at run time",
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallableReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
                val method = node.resolve() as? PsiMethod ?: return
                val takesConstantText =
                    method.parameterList.parameters.any { isMarkedConstantText(context, it) }
                if (!takesConstantText || !visits.isFirst(node)) return
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Call `${method.name}` directly, so its constant text arguments can be checked",
                )
            }
        }

    companion object {
        val ISSUE: Issue =
            loggingIssue(
                id = "DewfallLogTextNotConstant",
                briefDescription = "Log text that isn't constant",
                explanation =
                    """
                    Log messages, the area of a log tag, and the names of log fields are constant \
                    text, so no user data can reach a log line through them. Values go in typed \
                    `LogField`s instead. Constant text is a string literal, a `const val`, a \
                    parameter marked `@ConstantText`, or `+` of those. A string template is never \
                    constant text, even when every part of it is a `const val`.

                    A function that passes its own parameter on as constant text, such as one \
                    that builds a `LogField`, marks the parameter `@ConstantText`, and its callers \
                    are checked instead. A callable reference to such a function, such as \
                    `log::info`, is flagged, because the text that it is called with later can't \
                    be checked.
                    """,
                detector = ConstantLogTextDetector::class.java,
            )
    }
}
