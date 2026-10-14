package io.github.dewfallapp.buildlogic

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Hilt for an Android module, with Hilt's compiler running through KSP. Apply it after
 * dewfall.android.application or dewfall.android.library. See docs/architecture.md#build-settings.
 */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            requireAndroidPlugin("dewfall.android.hilt")
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")

            // KotlinCompiler.kt fails the build on Kotlin compiler warnings. Warnings that
            // annotation processors report through KSP need KSP's own setting.
            extensions.configure<KspExtension> { allWarningsAsErrors = true }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                add("implementation", libs.findLibrary("hilt-android").get())
                // In an Android module, ksp covers the main source set only. Tests that use Hilt
                // add the compiler to kspTest or kspAndroidTest.
                // https://github.com/google/ksp/blob/2.3.12/README.md#single-platform-jvm--android
                add("ksp", libs.findLibrary("hilt-compiler").get())
            }
        }
    }
}
