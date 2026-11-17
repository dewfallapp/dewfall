package io.github.dewfallapp.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UElement

/**
 * A detector that looks at each use of the methods it names: every call, and every callable
 * reference, such as `Log::println`. A reference hands the method to other code, which can call it
 * later, so it counts as a use too. Lint resolves only uses whose name matches, so the detector
 * stays cheap however much code there is.
 */
internal abstract class MethodUseDetector : Detector(), SourceCodeScanner {
    /** The names of the methods to look at. A call or reference with another name is skipped. */
    protected abstract val methodNames: List<String>

    /** The uses visited in the current file, so that each one is reported once. */
    protected val visits = VisitsInFile()

    /** Called once with each [use] of a method named in [methodNames], and its method. */
    protected abstract fun visitMethodUse(context: JavaContext, use: UElement, method: PsiMethod)

    final override fun beforeCheckFile(context: Context) {
        visits.clear()
    }

    final override fun getApplicableMethodNames(): List<String> = methodNames

    final override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (visits.isFirst(node)) visitMethodUse(context, node, method)
    }

    final override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallableReferenceExpression::class.java)

    final override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
                if (node.callableName !in methodNames) return
                val method = node.resolve() as? PsiMethod ?: return
                if (visits.isFirst(node)) visitMethodUse(context, node, method)
            }
        }
}
