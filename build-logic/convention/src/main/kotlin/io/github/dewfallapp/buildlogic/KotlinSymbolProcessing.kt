package io.github.dewfallapp.buildlogic

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Applies KSP, which runs the annotation processors for Kotlin code, such as Hilt's and Room's
 * compilers. KotlinCompiler.kt fails the build on Kotlin compiler warnings. Warnings that
 * annotation processors report through KSP need KSP's own setting. See
 * docs/architecture.md#build-settings.
 */
internal fun Project.configureDewfallKsp() {
    pluginManager.apply("com.google.devtools.ksp")
    extensions.configure<KspExtension> { allWarningsAsErrors = true }
}
