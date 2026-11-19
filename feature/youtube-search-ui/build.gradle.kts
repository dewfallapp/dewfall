plugins {
    id("dewfall.android.library")
    id("dewfall.android.compose")
    id("dewfall.android.hilt")
}

android {
    namespace = "io.github.dewfallapp.feature.youtubesearch.ui"
}

dependencies {
    implementation(project(":feature:youtube-search-logic"))
    // The screen reaches YouTube only through the search repository, never through the YouTube
    // layer itself. See docs/architecture.md#reading-from-youtube.
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:mvi"))

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
