package io.github.dewfallapp.lint

import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement

private const val EXTRACTOR_LOGGER_CLASS = "org.schabi.newpipe.extractor.utils.ExtractorLogger"

/** NewPipeExtractor's logger interface, which only ExtractorLogger.setLogger takes. */
private const val NEW_PIPE_LOGGER_INTERFACE = "org.schabi.newpipe.extractor.utils.Logger"

/**
 * Flags every use of NewPipeExtractor's logger: any use of ExtractorLogger, whose setLogger turns
 * the logger on, and any class that implements its Logger interface. See ADR 0007.
 */
internal class ExtractorLoggerDetector : MethodUseDetector() {
    // Every public method of ExtractorLogger in NewPipeExtractor v0.26.5.
    override val methodNames: List<String> = listOf("setLogger", "d", "w", "e")

    override fun visitMethodUse(context: JavaContext, use: UElement, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, EXTRACTOR_LOGGER_CLASS)) return
        context.report(
            ISSUE,
            use,
            context.getLocation(use),
            "Never use `NewPipeExtractor`'s logger, whose lines hold URLs, titles and IDs",
        )
    }

    override fun applicableSuperClasses(): List<String> = listOf(NEW_PIPE_LOGGER_INTERFACE)

    override fun visitClass(context: JavaContext, declaration: UClass) {
        context.report(
            ISSUE,
            declaration,
            context.getNameLocation(declaration),
            "Don't implement `NewPipeExtractor`'s `Logger`, which only its `setLogger` takes",
        )
    }

    companion object {
        val ISSUE: Issue =
            loggingIssue(
                id = "DewfallExtractorLogger",
                briefDescription = "NewPipeExtractor's logger used",
                explanation =
                    """
                    NewPipeExtractor stays silent until an app calls \
                    `ExtractorLogger.setLogger`. Half of its log calls write user data, such as \
                    video URLs, titles and the search text, and a logger that receives its lines \
                    can't tell the constant text from the values in them. So Dewfall never sets \
                    it, in any build, never calls `ExtractorLogger` at all, and never implements \
                    its `Logger` interface. Changing that needs a new ADR.
                    """,
                detector = ExtractorLoggerDetector::class.java,
            )
    }
}
