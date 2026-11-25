plugins {
    id("dewfall.android.library")
    id("dewfall.android.hilt")
}

android {
    namespace = "io.github.dewfallapp.core.download"
}

dependencies {
    // The queue, the stream links, the media requests and where the files live, never
    // :core:youtube itself.
    implementation(project(":core:data"))
    implementation(project(":core:logging"))
    implementation(project(":core:model"))
    // The player's own check of which formats the phone decodes, and the PendingIntent that opens
    // the app, which the media notification uses too.
    implementation(project(":core:playback"))
    // The download job on Android 10 to 13. :app sees WorkManager's Configuration type through
    // DewfallApplication, which provides it, so it declares WorkManager too. See ADR 0010.
    implementation(libs.androidx.work.runtime)
    // For OkHttp's Call.Factory type, which the media requests come as.
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
}
