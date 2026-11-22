plugins {
    id("dewfall.android.library")
    id("dewfall.android.compose")
    id("dewfall.android.screenshots")
    id("dewfall.android.hilt")
}

android {
    namespace = "io.github.dewfallapp.feature.channelpage.ui"
}

dependencies {
    implementation(project(":feature:channel-page-logic"))
    // The page reaches YouTube and the subscriptions only through repositories, never through the
    // YouTube layer or the database. See docs/architecture.md#reading-from-youtube.
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:mvi"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
