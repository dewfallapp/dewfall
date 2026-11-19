plugins {
    id("dewfall.android.library")
    id("dewfall.android.hilt")
}

android {
    namespace = "io.github.dewfallapp.core.playback"
}

dependencies {
    // The connection exposes VideoId and PlaybackFailure from :core:model.
    api(project(":core:model"))
    // Stream links and the YouTube layer's media requests, never :core:youtube itself.
    implementation(project(":core:data"))
    implementation(project(":core:logging"))
    // The player: ExoPlayer in a media session service, with HLS for live streams and OkHttp for
    // the YouTube layer's media requests. The connection exposes Media3's Player, so the session
    // library, which brings it, is an api dependency.
    api(libs.androidx.media3.session)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.datasource.okhttp)
    // For OkHttp's Call.Factory type, at the app's version rather than the 4.12.0 that
    // media3-datasource-okhttp asks for.
    implementation(libs.okhttp)
    constraints {
        // Media3 1.11.1 brings Guava 33.3.1-android, which has the advisory GHSA-xxph-c9ww-hj94.
        // Guava stays undeclared, because a direct declaration would bring back the annotation
        // artifacts that media3-common excludes.
        implementation(libs.guava) {
            because("GHSA-xxph-c9ww-hj94 affects the 33.3.1-android that Media3 1.11.1 brings")
        }
    }
}
