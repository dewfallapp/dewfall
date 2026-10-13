package io.github.dewfallapp.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * Checks a plain Kotlin/JVM module's classes against the Android API at the app's minSdk, with
 * Animal Sniffer and a Gummy Bears signature. Android Lint can't do this in these modules, because
 * its API check skips modules that aren't Android. See docs/architecture.md#build-settings.
 */
internal fun Project.configureAndroidApiCheck() {
    pluginManager.apply("ru.vyarus.animalsniffer")

    val gummyBears =
        extensions
            .getByType<VersionCatalogsExtension>()
            .named("libs")
            .findVersion("gummyBears")
            .get()
            .requiredVersion
    dependencies.add(
        "signature",
        "com.toasttab.android:gummy-bears-api-${DewfallBuild.MIN_SDK}:$gummyBears@signature",
    )

    // ./gradlew lint runs the check too, so one lint command covers every module.
    tasks.named { it == "lint" }.configureEach { dependsOn("animalsnifferMain") }
}
