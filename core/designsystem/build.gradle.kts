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
