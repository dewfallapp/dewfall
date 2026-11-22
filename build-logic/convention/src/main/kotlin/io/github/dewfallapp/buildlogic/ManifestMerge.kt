package io.github.dewfallapp.buildlogic

import org.gradle.api.Project
import org.gradle.api.Task

/**
 * A changed value of this input makes every checkout merge its manifests once more. See
 * [keepManifestMergeOutOfBuildCache].
 */
private const val NOT_CACHED_INPUT = "dewfallManifestMergeNotCached"

/**
 * Keeps the manifest merge tasks of [type] out of Gradle's build cache. See
 * docs/architecture.md#build-settings and #230.
 *
 * AGP caches the manifest merge, with the manifests' paths relative to the project in its cache
 * key. But the merge report that it writes holds the absolute paths of the checkout that ran it.
 * Lint's report task reads that report and checks tools:ignore in the files it names. Checkouts on
 * one machine share the build cache, so a checkout that restored the task from it got lint results
 * from another checkout's manifest.
 *
 * The extra input changes the task's inputs, so a checkout that holds a restored report merges
 * again on its next build. It also does so after building a branch without this setting, which can
 * still restore a report from the cache. Keep it: it costs nothing.
 *
 * [type] is AGP's ProcessApplicationManifest or ProcessLibraryManifest. AGP's public API has no
 * handle on these tasks, and their names aren't public either. AGP's roadmap advises against its
 * internal classes, and tentatively hides them in AGP 10
 * (https://developer.android.com/build/releases/gradle-plugin-roadmap). Then build-logic stops
 * compiling in that AGP update. Check whether the new AGP still caches the manifest merge with
 * absolute paths in its report: if it doesn't, delete this function. If it does, match the tasks by
 * name, and fail the build when an Android module has none that match.
 */
internal fun <T : Task> Project.keepManifestMergeOutOfBuildCache(type: Class<T>) {
    tasks.withType(type).configureEach {
        outputs.doNotCacheIf("its merge report holds this checkout's absolute paths") { true }
        inputs.property(NOT_CACHED_INPUT, 1)
    }
}
