// Puts AGP and the Kotlin Gradle plugin on the build classpath once, at the versions in
// gradle/libs.versions.toml. The convention plugins in build-logic apply them to modules.
// Declaring the Kotlin plugin here also raises the Kotlin version that AGP's built-in
// Kotlin support uses.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spotless)
}

// Formats every Kotlin file and Kotlin build script in the repository, build-logic included.
// The settings live here because build-logic and the root scripts belong to no module.
spotless {
    val ktfmtVersion = libs.versions.ktfmt.get()
    val buildOutput = arrayOf("**/build/**", "**/.gradle/**", "**/.kotlin/**")
    kotlin {
        target("**/*.kt")
        targetExclude(*buildOutput)
        ktfmt(ktfmtVersion).kotlinlangStyle()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude(*buildOutput)
        ktfmt(ktfmtVersion).kotlinlangStyle()
    }
}
