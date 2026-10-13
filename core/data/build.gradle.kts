plugins {
    id("dewfall.android.library")
}

android {
    namespace = "io.github.dewfallapp.core.data"
}

dependencies {
    // Repositories return Dewfall's own models, so their users see them too.
    api(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:youtube"))
}
