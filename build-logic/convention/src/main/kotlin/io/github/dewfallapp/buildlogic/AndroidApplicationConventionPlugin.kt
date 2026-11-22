package io.github.dewfallapp.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.tasks.ProcessApplicationManifest
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** Settings for the app module. Kotlin comes from AGP's built-in Kotlin support. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply(DetektConventionPlugin::class.java)

            extensions.configure<ApplicationExtension> {
                compileSdk = DewfallBuild.COMPILE_SDK
                defaultConfig {
                    minSdk = DewfallBuild.MIN_SDK
                    targetSdk = DewfallBuild.TARGET_SDK
                }
                lint { configureDewfallLint() }
            }
            addDewfallLintChecks()

            // AGP's built-in Kotlin adds the kotlin extension.
            extensions.configure<KotlinAndroidProjectExtension> {
                compilerOptions { configureDewfallKotlin() }
            }

            configureJavaToolchain()
            configureAndroidUnitTests()
            keepManifestMergeOutOfBuildCache(ProcessApplicationManifest::class.java)
        }
    }
}
