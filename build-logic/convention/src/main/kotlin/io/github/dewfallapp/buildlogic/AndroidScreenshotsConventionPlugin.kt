package io.github.dewfallapp.buildlogic

import com.android.build.api.dsl.CommonExtension
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

/**
 * Screenshot tests of a module's previews, with Compose Preview Screenshot Testing in an AGP test
 * suite. Apply it after dewfall.android.compose. The suite renders the @PreviewTest wrappers in
 * src/screenshotTest/kotlin on the JVM, with Layoutlib, and compares them with the images in
 * src/screenshotTestDefaultDebug/reference. See docs/testing.md#screenshot-tests.
 */
class AndroidScreenshotsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            check(pluginManager.hasPlugin("org.jetbrains.kotlin.plugin.compose")) {
                "dewfall.android.screenshots needs Compose. Apply dewfall.android.compose " +
                    "before it in $path."
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            extensions.getByType<CommonExtension>().testOptions.screenshotTests.create(SUITE) {
                engineVersion = libs.findVersion("screenshot").get().requiredVersion
                targetVariants.add("debug")
                dependencies {
                    implementation.add(
                        project.dependencies.platform(
                            libs.findLibrary("androidx-compose-bom").get(),
                        ),
                    )
                    implementation.add(libs.findLibrary("androidx-compose-ui-tooling").get())
                    implementation.add(libs.findLibrary("screenshot-validation-api").get())
                }
            }

            // The images must not depend on the machine that renders them. ICU's ListFormatter
            // and other formatting follow the JVM's default locale, so the suite's JVM gets the
            // British English that the YouTube layer asks for (ADR 0009), and UTC. This covers the
            // check task and the task that records the images.
            tasks.withType<Test>().configureEach {
                if (name.endsWith(TASK_SUFFIX)) {
                    systemProperty("user.language", "en")
                    systemProperty("user.country", "GB")
                    systemProperty("user.timezone", "UTC")
                }
            }

            // detekt reads only src/main and src/test by itself. Lint can't read this folder at
            // all, so scripts/scan_screenshot_tests.py checks that it holds only one-call
            // wrappers. See ADR 0007's note of November 21, 2026.
            extensions.configure<DetektExtension> { source.from("src/$SUITE/kotlin") }
        }
    }

    private companion object {
        const val SUITE = "screenshotTest"

        /**
         * AGP 9.5.1 names the suite's tasks testDebugScreenshotTestDefaultTestSuite and
         * updateDebugScreenshotTestDefaultTestSuite.
         */
        const val TASK_SUFFIX = "ScreenshotTestDefaultTestSuite"
    }
}
