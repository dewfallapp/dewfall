package io.github.dewfallapp.buildlogic

import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure

/**
 * detekt with the settings in config/detekt/detekt.yml. The three module plugins apply it, and the
 * root project applies it to check build-logic.
 */
class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("dev.detekt")

            extensions.configure<DetektExtension> {
                config.setFrom(
                    isolated.rootProject.projectDirectory.file("config/detekt/detekt.yml")
                )
                // The file lists only the changes to detekt's default rules.
                buildUponDefaultConfig = true
                // Any finding fails the build, whatever its severity.
                failOnSeverity = FailOnSeverity.Info
                parallel = true
            }
        }
    }
}
