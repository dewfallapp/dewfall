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

// Records the saved YouTube responses that the tests replay. It sends requests to YouTube, so it
// runs only when asked for, never as part of test or check. -PyoutubeSets=SEARCH_NASA,UPLOADS_NASA
// records only the sets it names. See docs/testing.md#saved-youtube-responses.
tasks.register<JavaExec>("recordYouTubeResponses") {
    description = "Records the saved YouTube responses that the tests replay, from real YouTube."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "io.github.dewfallapp.core.youtube.newpipe.saved.RecordYouTubeResponsesKt"
    args(
        layout.projectDirectory.dir("src/test/resources/youtube").asFile.path,
        libs.versions.newpipeExtractor.get(),
    )
    args(providers.gradleProperty("youtubeSets").map { it.split(',') }.getOrElse(emptyList()))
}

// The live check of the YouTube layer, which the daily workflow runs, and a developer by hand. It
// sends requests to YouTube and googlevideo.com, so it runs only when asked for, never as part of
// test or check. It writes its results to build/live-youtube-check/results.txt. By default it skips
// nothing, so a call that YouTube blocks or the network fails fails the check.
// -PliveCheckSkipUnreachable=true skips those calls instead, as the daily workflow does.
// -PsimulateFailure=SEARCH_NASA reports that kind as failed, to test the workflow's issues. See
// docs/testing.md#the-daily-youtube-check.
tasks.register<JavaExec>("liveYouTubeCheck") {
    description = "Checks the YouTube layer against real YouTube. See docs/testing.md."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "io.github.dewfallapp.core.youtube.newpipe.live.RunLiveCheckKt"
    args(layout.buildDirectory.file("live-youtube-check/results.txt").get().asFile.path)
    args(providers.gradleProperty("liveCheckSkipUnreachable").getOrElse("false"))
    args(providers.gradleProperty("simulateFailure").map { listOf(it) }.getOrElse(emptyList()))
}
