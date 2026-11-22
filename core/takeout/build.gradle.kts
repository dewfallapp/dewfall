plugins {
    id("dewfall.jvm.library")
}

dependencies {
    // The reader gives channels by their ChannelId, so its users see the model too.
    api(project(":core:model"))
}
