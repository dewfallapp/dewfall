plugins {
    id("dewfall.android.library")
    id("dewfall.android.compose")
    id("dewfall.android.hilt")
}

android {
    namespace = "io.github.dewfallapp.feature.player.ui"
}

dependencies {
    implementation(project(":feature:player-logic"))
    // The screen reaches YouTube only through the video details repository, never through the
    // YouTube layer itself. See docs/architecture.md#reading-from-youtube.
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:mvi"))
    implementation(project(":core:playback"))

    // The video's picture and the state holders of its controls, and Player.listen for loading and
    // live. See docs/architecture.md#the-player.
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
