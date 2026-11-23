plugins {
    id("dewfall.jvm.library")
}

dependencies {
    // The state, intents and reducer build on the MVI base types, so the modules that use them see
    // those types too.
    api(project(":core:mvi"))
    // The intents and the groups carry the saved videos as VideoSummary.
    api(project(":core:model"))
}
