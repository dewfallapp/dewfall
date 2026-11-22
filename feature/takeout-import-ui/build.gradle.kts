plugins {
    id("dewfall.android.library")
    id("dewfall.android.compose")
    id("dewfall.android.screenshots")
    id("dewfall.android.hilt")
}

android {
    namespace = "io.github.dewfallapp.feature.takeoutimport.ui"
}

dependencies {
    implementation(project(":feature:takeout-import-logic"))
    // The screen reaches the import only through its repository. See
    // docs/architecture.md#the-takeout-import.
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:mvi"))
    // rowGroupShape, the shape of a group of rows, as the video rows use.
    implementation(project(":core:ui"))

    // rememberLauncherForActivityResult, for Android's file picker.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
