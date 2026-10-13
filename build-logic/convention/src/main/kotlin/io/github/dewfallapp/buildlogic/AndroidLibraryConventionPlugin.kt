package io.github.dewfallapp.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** Settings for Android library modules. Kotlin comes from AGP's built-in Kotlin support. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply(DetektConventionPlugin::class.java)

            extensions.configure<LibraryExtension> {
                compileSdk = DewfallBuild.COMPILE_SDK
                defaultConfig {
                    minSdk = DewfallBuild.MIN_SDK
                }
                // Libraries have no target version of their own. Lint and tests use the app's.
                lint {
                    targetSdk = DewfallBuild.TARGET_SDK
                    configureDewfallLint()
                }
                testOptions {
                    targetSdk = DewfallBuild.TARGET_SDK
                }
            }

            // AGP's built-in Kotlin adds the kotlin extension.
            extensions.configure<KotlinAndroidProjectExtension> {
                compilerOptions { configureDewfallKotlin() }
            }

            configureJavaToolchain()
        }
    }
}
