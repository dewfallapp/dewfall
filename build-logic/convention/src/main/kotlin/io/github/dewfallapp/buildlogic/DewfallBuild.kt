package io.github.dewfallapp.buildlogic

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure

/** Android and Java versions shared by every module. ADR 0006 explains the Android versions. */
internal object DewfallBuild {
    const val COMPILE_SDK = 37
    const val MIN_SDK = 29
    const val TARGET_SDK = 37

    /**
     * The JDK every module compiles with. Java and Kotlin bytecode targets follow it, so every
     * module produces Java 17 bytecode.
     */
    const val JAVA_VERSION = 17
}

/**
 * Fails with a clear message when a plugin that adds to an Android module, such as
 * dewfall.android.compose, is applied without an Android plugin, or before it.
 */
internal fun Project.requireAndroidPlugin(pluginId: String) {
    check(
        pluginManager.hasPlugin("com.android.application") ||
            pluginManager.hasPlugin("com.android.library")
    ) {
        "$pluginId needs an Android module. Apply dewfall.android.application or " +
            "dewfall.android.library before it in $path."
    }
}

internal fun Project.configureJavaToolchain() {
    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(DewfallBuild.JAVA_VERSION)
        }
    }
}
