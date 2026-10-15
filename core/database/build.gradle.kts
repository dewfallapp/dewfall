plugins {
    id("dewfall.android.library")
    id("dewfall.android.room")
}

android {
    namespace = "io.github.dewfallapp.core.database"
}

dependencies {
    implementation(project(":core:model"))
}
