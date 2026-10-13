package io.github.dewfallapp.buildlogic

import com.android.build.api.dsl.Lint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Settings for plain Kotlin/JVM modules. They can't see Android classes. See ADR 0006. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("java-library")
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("com.android.lint")
            pluginManager.apply(DetektConventionPlugin::class.java)

            configureJavaToolchain()

            // Lint's general checks. Its API check doesn't run here, so configureAndroidApiCheck
            // checks these modules against the minSdk instead.
            extensions.configure<Lint> { configureDewfallLint() }
            configureAndroidApiCheck()
        }
    }
}
