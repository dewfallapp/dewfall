plugins {
    id("dewfall.android.library")
    id("dewfall.android.room")
    // The thumbnail and avatar lists are stored as JSON. See DatabaseConverters.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.dewfallapp.core.database"

    defaultConfig {
        // The DAO tests run on an emulator or a phone, against its own SQLite. See
        // docs/testing.md#database-tests.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // AndroidX Test's runner has a usage tracker that is off unless a test turns it on. This
        // keeps it off.
        testInstrumentationRunnerArguments["disableAnalytics"] = "true"
    }
}

dependencies {
    // The entities use the models' types, such as Thumbnail, VideoKind, Instant and Duration.
    api(project(":core:model"))
    // DewfallDatabase extends Room's RoomDatabase, so :core:data, which builds it, sees Room too.
    // :core:data depends on this module as an implementation dependency, so features never do.
    api(libs.androidx.room3.runtime)
    implementation(libs.kotlinx.serialization.json)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
