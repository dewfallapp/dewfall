plugins {
    id("dewfall.android.application")
}

android {
    namespace = "io.github.dewfallapp"

    defaultConfig {
        // Android treats a different application ID as a different app, so this never changes.
        applicationId = "io.github.dewfallapp"
    }

    compileOptions {
        // NewPipeExtractor needs core library desugaring below API 33. See ADR 0006.
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))

    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
}
