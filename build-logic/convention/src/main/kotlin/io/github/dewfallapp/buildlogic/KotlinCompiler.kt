package io.github.dewfallapp.buildlogic

import org.gradle.kotlin.dsl.assign
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions

/**
 * Kotlin compiler settings shared by every module. Any warning fails the build, such as the one
 * that a call to a deprecated API causes. They go in the `kotlin.compilerOptions` block, so they
 * reach every Kotlin compilation in the module, tests included. See
 * docs/architecture.md#build-settings.
 */
internal fun KotlinJvmCompilerOptions.configureDewfallKotlin() {
    allWarningsAsErrors = true
}
