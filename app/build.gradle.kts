plugins {
    id("dewfall.android.application")
    id("dewfall.android.compose")
    id("dewfall.android.hilt")
    // The navigation keys are @Serializable, so the back stack survives process death.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.dewfallapp"

    defaultConfig {
        // Android treats a different application ID as a different app, so this never changes.
        applicationId = "io.github.dewfallapp"
    }

    buildFeatures {
        // LoggingModule reads BuildConfig.DEBUG to pick the lowest log level that a build keeps.
        // AGP 8.0 and later generate BuildConfig only when a module asks. See ADR 0007.
        buildConfig = true
    }

    compileOptions {
        // NewPipeExtractor needs core library desugaring below API 33. See ADR 0006.
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:logging"))
    implementation(project(":feature:player-ui"))
    implementation(project(":feature:youtube-search-ui"))

    implementation(libs.androidx.activity.compose)
    // The navigation between screens. See docs/architecture.md#navigation.
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.androidx.compose.material3)
    // The manifest names App Startup's InitializationProvider to remove emoji2's initializer.
    // Compose already brings App Startup in. Declaring it lets lint find the class.
    implementation(libs.androidx.startup.runtime)

    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    // Saves navigation keys as JSON in tests, because the Bundle that SavedState writes needs
    // Android.
    testImplementation(libs.kotlinx.serialization.json)

    // The YouTube layer's debug screen. Only debug builds contain it. See
    // docs/architecture.md#the-youtube-layers-debug-screen.
    debugImplementation(project(":feature:youtube-debug-ui"))
}

// LaunchThemeTest checks the static launch colors against docs/design/'s tokens.json and inspects
// :app's manifest and resource XMLs. Declaring them as test inputs makes Gradle run the tests
// again when they change, instead of reusing a cached result.
val designTokens =
    rootProject.layout.projectDirectory.file("docs/design/claude-design/design-system/tokens.json")
val appResources = layout.projectDirectory.dir("src/main/res")
val appManifest = layout.projectDirectory.file("src/main/AndroidManifest.xml")

tasks.withType<Test>().configureEach {
    inputs
        .file(designTokens)
        .withPropertyName("designTokens")
        .withPathSensitivity(PathSensitivity.NONE)
    inputs
        .dir(appResources)
        .withPropertyName("appResources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs
        .file(appManifest)
        .withPropertyName("appManifest")
        .withPathSensitivity(PathSensitivity.NONE)
    // Tests run in the module's folder. Relative paths keep the cache key the same on every
    // machine.
    systemProperty("dewfall.designTokens", designTokens.asFile.relativeTo(projectDir).path)
    systemProperty("dewfall.appResources", appResources.asFile.relativeTo(projectDir).path)
    systemProperty("dewfall.appManifest", appManifest.asFile.relativeTo(projectDir).path)
}
