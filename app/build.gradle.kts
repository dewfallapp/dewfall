plugins {
    id("dewfall.android.application")
    id("dewfall.android.compose")
    id("dewfall.android.hilt")
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
    implementation(project(":feature:youtube-search-ui"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    // The manifest names App Startup's InitializationProvider to remove emoji2's initializer.
    // Compose already brings App Startup in. Declaring it lets lint find the class.
    implementation(libs.androidx.startup.runtime)

    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    // The YouTube layer's debug screen. Only debug builds contain it. See
    // docs/architecture.md#the-youtube-layers-debug-screen.
    debugImplementation(project(":feature:youtube-debug-ui"))
}
