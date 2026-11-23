plugins {
    id("dewfall.android.library")
    id("dewfall.android.compose")
    id("dewfall.android.screenshots")
    id("dewfall.android.hilt")
}

android {
    namespace = "io.github.dewfallapp.feature.channels.ui"
}

dependencies {
    implementation(project(":feature:channels-logic"))
    // The screen reads the subscriptions only through a repository, never through the database.
    // See docs/architecture.md#the-database-repositories.
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:mvi"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
