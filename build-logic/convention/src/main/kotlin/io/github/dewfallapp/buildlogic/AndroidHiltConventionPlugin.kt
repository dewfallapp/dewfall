package io.github.dewfallapp.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
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
            configureDewfallKsp()
            pluginManager.apply("com.google.dagger.hilt.android")

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
