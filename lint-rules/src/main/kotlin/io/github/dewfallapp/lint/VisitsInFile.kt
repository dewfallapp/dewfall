package io.github.dewfallapp.lint

import com.intellij.psi.PsiElement
import org.jetbrains.uast.UElement

/**
 * The code that a detector has visited in the current file, so that it reports each place once.
 * When every parameter of a class's constructor has a default value, Kotlin gives the class a
 * second constructor without parameters, and lint visits the default values once for each.
 */
internal class VisitsInFile {
    private val visitedSources = mutableSetOf<PsiElement>()

    /** Forgets every visit, for a new file. */
    fun clear() {
        visitedSources.clear()
    }

    /** Whether [element] is visited for the first time in this file, which this records. */
    fun isFirst(element: UElement): Boolean {
        val source = element.sourcePsi ?: return false
        return visitedSources.add(source)
    }
}
