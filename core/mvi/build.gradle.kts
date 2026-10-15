plugins {
    id("dewfall.jvm.library")
}

dependencies {
    // MviViewModel extends Lifecycle's ViewModel and exposes StateFlows, so the modules that use it
    // see both. This module compiles against Lifecycle's JVM build. Android modules get its Android
    // build, which has the same API.
    api(libs.androidx.lifecycle.viewmodel)
    api(libs.kotlinx.coroutines.core)
}
