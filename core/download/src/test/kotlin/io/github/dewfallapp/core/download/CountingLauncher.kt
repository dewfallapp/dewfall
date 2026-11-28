package io.github.dewfallapp.core.download

/** Counts its launches, and answers each with [accepts]. The scheduler tests share it. */
internal class CountingLauncher(var accepts: Boolean = true) : RunLauncher {
    var launches = 0

    override suspend fun launch(): Boolean {
        launches++
        return accepts
    }
}
