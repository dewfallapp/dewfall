plugins {
    id("dewfall.android.library")
    id("dewfall.android.compose")
    id("dewfall.android.hilt")
}

android {
    namespace = "io.github.dewfallapp.feature.youtubedebug.ui"
}

dependencies {
    implementation(project(":feature:youtube-debug-logic"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:mvi"))
    // The one feature module that sees the YouTube layer, because its job is to call the layer's
    // functions themselves. Real features reach YouTube only through repositories in :core:data.
    // NewPipeExtractor stays hidden, because :core:youtube doesn't expose it. See
    // docs/architecture.md#the-youtube-layers-debug-screen.
    implementation(project(":core:youtube"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
