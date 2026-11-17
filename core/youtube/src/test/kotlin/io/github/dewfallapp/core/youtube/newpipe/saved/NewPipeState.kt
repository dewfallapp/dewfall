package io.github.dewfallapp.core.youtube.newpipe.saved

import java.util.Random
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/**
 * The seed of the random generator that makes the playback nonce in YouTube's player requests and
 * the `t` parameter of their links, the same seed that NewPipeExtractor's own tests use. Changing
 * it changes every saved player request.
 */
private const val NONCE_SEED = 1L

/**
 * Resets the static state that NewPipeExtractor keeps between calls, so that a call sends the same
 * requests as when its saved set was recorded. NewPipeExtractor's own tests do the same in
 * [YoutubeTestsUtils.ensureStateless](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubeTestsUtils.java#L27-L32).
 * - The version of YouTube's web client, so that the call fetches sw.js again and takes the version
 *   saved with its set, not one that an earlier test read from another set.
 * - The random generator of the playback nonce and the `t` parameter, seeded, so that both come out
 *   the same on every run.
 * - The cookie consent flag, which Dewfall never sets, back to its default.
 * - YouTube's player code and what NewPipeExtractor decoded with it. No saved set needs the player
 *   code yet, and resetting it keeps one test from depending on another.
 *
 * Call it before a call starts, never while one runs. NewPipeCalls' Mutex doesn't cover it, so it
 * relies on this module's tests running one at a time, as they do by default.
 */
internal fun resetNewPipeExtractorState() {
    YoutubeParsingHelper.resetClientVersion()
    YoutubeParsingHelper.setNumberGenerator(Random(NONCE_SEED))
    YoutubeParsingHelper.setConsentAccepted(false)
    YoutubeJavaScriptPlayerManager.clearAllCaches()
}
