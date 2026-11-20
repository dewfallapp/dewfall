plugins {
    id("dewfall.android.library")
    id("dewfall.android.compose")
}

android {
    namespace = "io.github.dewfallapp.core.designsystem"
}

dependencies {
    // Screens use Material 3 components and read the theme through MaterialTheme, so Material 3's
    // types are part of this module's API.
    api(libs.androidx.compose.material3)

    constraints {
        // Compose brings Lifecycle 2.9.4, whose lint checks call a Kotlin API that AGP 9.5's lint
        // no longer has, so lint fails with ObsoleteLintCustomCheck. This raises Lifecycle to the
        // catalog's version, which the app ships anyway. Lifecycle keeps all its artifacts on one
        // version, so one constraint moves them all. Remove it once Compose brings Lifecycle 2.10.0
        // or later on its own. See docs/architecture.md#formatting-and-lint.
        implementation(libs.androidx.lifecycle.runtime.compose)
    }

    testImplementation(libs.kotlinx.serialization.json)
}

// DesignTokensTest checks the theme against the design tokens in docs/design/, and ManropeTest
// reads the font XMLs. Declaring both as test inputs makes Gradle run the tests again when they
// change, instead of reusing a cached result.
val designTokens =
    rootProject.layout.projectDirectory.file("docs/design/claude-design/design-system/tokens.json")
val fontResources = layout.projectDirectory.dir("src/main/res/font")

tasks.withType<Test>().configureEach {
    inputs
        .file(designTokens)
        .withPropertyName("designTokens")
        .withPathSensitivity(PathSensitivity.NONE)
    inputs
        .dir(fontResources)
        .withPropertyName("fontResources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // Tests run in the module's folder. Relative paths keep the cache key the same on every
    // machine.
    systemProperty("dewfall.designTokens", designTokens.asFile.relativeTo(projectDir).path)
    systemProperty("dewfall.fontResources", fontResources.asFile.relativeTo(projectDir).path)
}
