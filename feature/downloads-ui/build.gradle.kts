plugins {
    id("dewfall.android.library")
    id("dewfall.android.compose")
    id("dewfall.android.screenshots")
    id("dewfall.android.hilt")
}

android {
    namespace = "io.github.dewfallapp.feature.downloads.ui"
}

dependencies {
    implementation(project(":feature:downloads-logic"))
    // The screen reads and changes the downloads only through a repository, never through the
    // database or the download job. See docs/architecture.md#downloads-and-cleanup.
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:mvi"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
