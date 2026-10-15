package io.github.dewfallapp.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

/**
 * Unit tests in plain Kotlin/JVM modules run on JUnit 6, through the JUnit Platform. Tests use
 * kotlin.test's annotations and assertions, so they read the same as in Android modules. See
 * docs/testing.md.
 */
internal fun Project.configureJvmUnitTests() {
    tasks.withType<Test>().configureEach { useJUnitPlatform() }

    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    dependencies {
        // The BOM also lifts the JUnit 5 artifacts that kotlin-test-junit5 asks for to JUnit 6.
        add("testImplementation", platform(libs.findLibrary("junit-bom").get()))
        add("testImplementation", libs.findLibrary("kotlin-test-junit5").get())
        add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
        // Gradle 9 no longer puts the JUnit Platform launcher on the test runtime classpath.
        add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
    }
}

/**
 * Local unit tests in Android modules run on JUnit 4, AGP's default, because Robolectric and
 * Compose's test rules need it. Tests use kotlin.test's annotations and assertions, as in plain
 * Kotlin/JVM modules. See docs/testing.md.
 */
internal fun Project.configureAndroidUnitTests() {
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    dependencies {
        add("testImplementation", libs.findLibrary("junit4").get())
        add("testImplementation", libs.findLibrary("kotlin-test-junit").get())
        add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
    }
}
