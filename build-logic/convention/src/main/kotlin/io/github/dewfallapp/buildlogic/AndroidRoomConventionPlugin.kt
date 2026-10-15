package io.github.dewfallapp.buildlogic

import androidx.room3.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Room for an Android module, with Room's compiler running through KSP. Apply it after
 * dewfall.android.library. Only :core:database applies it. See docs/architecture.md#build-settings.
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            requireAndroidPlugin("dewfall.android.room")
            configureDewfallKsp()
            pluginManager.apply("androidx.room3")

            // Room writes each database version's schema to the module's schemas/ folder, where
            // it's committed. The pull request workflow fails when the build leaves changes there.
            extensions.configure<RoomExtension> {
                schemaDirectory(layout.projectDirectory.dir("schemas"))
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                add("implementation", libs.findLibrary("androidx-room3-runtime").get())
                add("ksp", libs.findLibrary("androidx-room3-compiler").get())
            }
        }
    }
}
