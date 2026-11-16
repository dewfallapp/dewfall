plugins {
    id("dewfall.jvm.library")
}

dependencies {
    // The YouTube layer returns Dewfall's own models, so its users see them too.
    api(project(":core:model"))
    // NewPipeYouTube's constructor takes a Logger and a CoroutineDispatcher, so whoever builds it
    // sees both types.
    api(project(":core:logging"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.newpipe.extractor)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
}

// The tests replay saved YouTube responses. When a request has no saved answer, a test names this
// version next to the one that recorded the set. See docs/testing.md#saved-youtube-responses.
tasks.test {
    systemProperty("dewfall.newpipeExtractorVersion", libs.versions.newpipeExtractor.get())
}
