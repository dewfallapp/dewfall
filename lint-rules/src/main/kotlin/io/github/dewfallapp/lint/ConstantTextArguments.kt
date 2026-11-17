package io.github.dewfallapp.lint

import com.android.tools.lint.detector.api.JavaContext
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.skipParenthesizedExprDown

/** The annotation in :core:logging that marks a parameter whose argument must be constant text. */
internal const val CONSTANT_TEXT_ANNOTATION = "io.github.dewfallapp.core.logging.ConstantText"

/**
 * The argument that [element] stands for, as the code writes it. For some arguments, such as an
 * elvis expression, lint passes a part that UAST adds, which has no source of its own. The argument
 * is then its nearest parent that has source, short of the call itself.
 */
internal fun writtenArgument(element: UElement): UExpression? =
    generateSequence(element) { it.uastParent }
        .takeWhile { it !is UCallExpression }
        .firstOrNull { it.sourcePsi != null } as? UExpression

/**
 * Whether [expression] is constant text: a string literal without templates, a `const val`, a Java
 * string constant, a @ConstantText parameter, or `+` of those.
 */
internal fun isConstantText(context: JavaContext, expression: UExpression): Boolean {
    val text = expression.skipParenthesizedExprDown()
    val kotlinString = text.sourcePsi as? KtStringTemplateExpression
    return when {
        kotlinString != null -> !kotlinString.hasInterpolation()
        text is ULiteralExpression -> text.value is String
        text is UPolyadicExpression -> {
            text.operator == UastBinaryOperator.PLUS &&
                text.operands.all { isConstantText(context, it) }
        }
        text is UReferenceExpression -> isConstantReference(context, text.resolve())
        else -> false
    }
}

/** Whether [parameter] is marked @ConstantText. */
internal fun isMarkedConstantText(context: JavaContext, parameter: PsiParameter): Boolean =
    context.evaluator.getAnnotation(parameter, CONSTANT_TEXT_ANNOTATION) != null

/** Whether [referenced] is a `const val`, a Java string constant or a @ConstantText parameter. */
private fun isConstantReference(context: JavaContext, referenced: PsiElement?): Boolean =
    when (referenced) {
        is PsiParameter -> isMarkedConstantText(context, referenced)
        is PsiField -> context.evaluator.isConst(referenced) || isJavaStringConstant(referenced)
        else -> false
    }

/** A static final String field with a constant value, as Java and compiled classes have it. */
private fun isJavaStringConstant(field: PsiField): Boolean =
    field.hasModifierProperty(PsiModifier.STATIC) &&
        field.hasModifierProperty(PsiModifier.FINAL) &&
        field.computeConstantValue() is String
