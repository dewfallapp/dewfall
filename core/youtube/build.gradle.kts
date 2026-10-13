plugins {
    id("dewfall.jvm.library")
}

dependencies {
    // The YouTube layer returns Dewfall's own models, so its users see them too.
    api(project(":core:model"))
}
