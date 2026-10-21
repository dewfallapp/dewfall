plugins {
    id("dewfall.jvm.library")
}

dependencies {
    // The YouTube layer returns Dewfall's own models, so its users see them too.
    api(project(":core:model"))
    implementation(project(":core:logging"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.newpipe.extractor)
    implementation(libs.okhttp)
}
