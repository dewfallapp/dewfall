plugins {
    id("dewfall.jvm.library")
}

dependencies {
    // The state, intents and reducer build on the MVI base types, so the modules that use them see
    // those types too.
    api(project(":core:mvi"))
    // The intents carry the YouTube layer's results, and the rows hold its IDs, kinds and times.
    api(project(":core:model"))
}
