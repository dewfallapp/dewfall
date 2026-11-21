package io.github.dewfallapp.buildlogic

import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * detekt with the settings in config/detekt/detekt.yml and the compose-rules plugin. The three
 * module plugins apply it, and the root project applies it to check build-logic.
 */
class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("dev.detekt")

            extensions.configure<DetektExtension> {
                config.setFrom(
                    isolated.rootProject.projectDirectory.file("config/detekt/detekt.yml"),
                )
                // detekt's defaults cover src/main and src/test. This adds the emulator tests in
                // src/androidTest, so they get the same checks.
                // https://github.com/detekt/detekt/blob/v2.0.0-alpha.6/detekt-gradle-plugin/src/main/kotlin/dev/detekt/gradle/plugin/DetektBasePlugin.kt
                source.from("src/androidTest/java", "src/androidTest/kotlin")
                // Every rule that the file doesn't list keeps its default.
                buildUponDefaultConfig = true
                // Any finding fails the build, whatever its severity.
                failOnSeverity = FailOnSeverity.Info
                parallel = true
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                // The Compose rules. The Compose section of detekt.yml lists every rule and whether
                // it is on.
                add("detektPlugins", libs.findLibrary("compose-rules-detekt").get())
            }
        }
    }
}
