plugins {
    id("dewfall.android.library")
}

android {
    namespace = "io.github.dewfallapp.core.database"
}

dependencies {
    implementation(project(":core:model"))
}
