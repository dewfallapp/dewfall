package io.github.dewfallapp.lint

import com.android.tools.lint.detector.api.JavaContext
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.getParentOfType

/**
 * A class where log lines leave the Logger, so ADR 0007 lets it write them where the rest of the
 * code may not. It matches only the class named [qualifiedName], in test sources when
 * [inTestSources] and in main sources otherwise. Code anywhere inside it counts, its nested classes
 * and lambdas included. Each detector allows the class only the calls that it needs.
 *
 * Changing these exceptions updates ADR 0007 in the same pull request, with a dated note.
 */
internal class LogWriterClass(
    private val qualifiedName: String,
    private val inTestSources: Boolean,
) {
    /** Whether [element], in the file that [context] analyzes, is code of this class. */
    fun contains(context: JavaContext, element: UElement): Boolean =
        context.isTestSource == inTestSources &&
            generateSequence(element.getParentOfType<UClass>()) { it.getParentOfType<UClass>() }
                .any { it.qualifiedName == qualifiedName }
}

/** LogcatWriter in :app's main code, the one class that may call android.util.Log.println. */
internal val LOGCAT_WRITER =
    LogWriterClass("io.github.dewfallapp.logging.LogcatWriter", inTestSources = false)
