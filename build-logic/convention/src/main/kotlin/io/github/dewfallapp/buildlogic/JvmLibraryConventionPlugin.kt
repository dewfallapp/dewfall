package io.github.dewfallapp.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/** Settings for plain Kotlin/JVM modules. They can't see Android classes. See ADR 0006. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("java-library")
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            configureJavaToolchain()
        }
    }
}
