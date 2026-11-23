plugins {
    id("dewfall.jvm.library")
}

dependencies {
    // The state, intents and reducer build on the MVI base types, so the modules that use them see
    // those types too.
    api(project(":core:mvi"))
    // The intents and the state carry the subscribed channels as ChannelOverview.
    api(project(":core:model"))
}
