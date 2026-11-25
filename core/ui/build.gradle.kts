plugins {
    id("dewfall.android.library")
    id("dewfall.android.compose")
}

android {
    namespace = "io.github.dewfallapp.core.ui"
}

dependencies {
    // The shared composables take the domain models' types, such as VideoKind and PublishedTime,
    // so the modules that call them see those types too.
    api(project(":core:model"))
    implementation(project(":core:designsystem"))
    // AsyncImage draws thumbnails and avatars. Only :app adds a network fetcher, so previews and
    // screenshot tests here can never reach the network. See ADR 0011.
    implementation(libs.coil.compose)
}
