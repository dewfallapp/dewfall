plugins {
    id("dewfall.android.library")
    id("dewfall.android.hilt")
}

android {
    namespace = "io.github.dewfallapp.core.data"
}

dependencies {
    // Repositories return Dewfall's own models, so their users see them too.
    api(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:logging"))
    // The Takeout import reads the picked file with the plain Kotlin/JVM reader.
    implementation(project(":core:takeout"))
    implementation(project(":core:youtube"))
    implementation(libs.androidx.core.ktx)
}
