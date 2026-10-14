package io.github.dewfallapp.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Jetpack Compose for an Android module. Apply it after dewfall.android.application or
 * dewfall.android.library. The module still declares the Compose libraries it uses, such as
 * Material 3. See docs/architecture.md#build-settings.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            requireAndroidPlugin("dewfall.android.compose")
            // The Compose compiler runs inside the Kotlin compiler of AGP's built-in Kotlin.
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            // CommonExtension covers both the app and Android libraries.
            extensions.getByType<CommonExtension>().buildFeatures.compose = true

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                add("implementation", platform(libs.findLibrary("androidx-compose-bom").get()))
                // Previews in Android Studio. The tooling that renders them goes only into debug
                // builds.
                add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
                add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
            }
        }
    }
}
