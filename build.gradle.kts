// Puts AGP and the Kotlin Gradle plugin on the build classpath once, at the versions in
// gradle/libs.versions.toml. The convention plugins in build-logic apply them to modules.
// Declaring the Kotlin plugin here also raises the Kotlin version that AGP's built-in
// Kotlin support uses.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
