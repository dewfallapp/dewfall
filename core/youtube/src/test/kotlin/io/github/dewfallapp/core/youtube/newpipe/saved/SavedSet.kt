package io.github.dewfallapp.core.youtube.newpipe.saved

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.SubtitleTrack
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** The search that [SavedSet.SEARCH_NASA] saves. */
internal const val NASA_QUERY = "nasa"

/**
 * Big Buck Bunny 60fps 4K, the Blender Foundation's short film, published on its channel in 2014.
 */
internal const val BIG_BUCK_BUNNY_ID = "aqz-KE-bpKQ"

/** Big Buck Bunny's title. */
internal const val BIG_BUCK_BUNNY_TITLE =
    "Big Buck Bunny 60fps 4K - Official Blender Foundation Short Film"

/** Big Buck Bunny's length. */
internal val BIG_BUCK_BUNNY_DURATION: Duration = 10.minutes + 35.seconds

/** When Big Buck Bunny was published. Its page gives the exact time, so it isn't an estimate. */
internal val BIG_BUCK_BUNNY_PUBLISHED =
    PublishedTime(Instant.parse("2014-11-10T14:05:55Z"), isApproximate = false)

/** The Blender Foundation's channel, which published Big Buck Bunny. */
internal const val BLENDER_CHANNEL_ID = "UCSMOQeBJ2RAnuFungnQOxLg"

/** NASA's channel, whose Live tab has streams on air. */
internal const val NASA_CHANNEL_ID = "UCLA_DiR1FfKNvjuUpBHmylQ"

/** jawed's channel, whose one video is "Me at the zoo", and which has no Live tab. */
internal const val JAWED_CHANNEL_ID = "UC4QobU6STFB0P71PMvOGN5A"

/** "Me at the zoo", the first video on YouTube and the only one on jawed's channel. */
internal const val ME_AT_THE_ZOO_ID = "jNQXAC9IVRw"

/** The title of "Me at the zoo". */
internal const val ME_AT_THE_ZOO_TITLE = "Me at the zoo"

/** The length of "Me at the zoo". */
internal val ME_AT_THE_ZOO_DURATION: Duration = 19.seconds

/**
 * Tears of Steel, the Blender Foundation's open film, published on its channel in 2012, with
 * speech. It has subtitles that people wrote in 28 languages, and one track that YouTube generated.
 */
internal const val TEARS_OF_STEEL_ID = "R6MlUcmOul8"

/** The length of Tears of Steel. */
internal val TEARS_OF_STEEL_DURATION: Duration = 12.minutes + 14.seconds

/**
 * "Who Can Be a NASA Astronaut? Myth or Fact", a NASA video with AI-dubbed audio tracks, for each
 * of which YouTube made an auto-generated subtitle track, a machine translation.
 */
internal const val AUTO_DUBBED_VIDEO_ID = "K8DG7LUTbaM"

/** An age-restricted channel, which NewPipeExtractor's own tests use too. */
internal const val AGE_RESTRICTED_CHANNEL_ID = "UCbfnHqxXs_K3kvaH-WlNlig"

/** A private video, which NewPipeExtractor's own tests use too. */
internal const val PRIVATE_VIDEO_ID = "8VajtrESJzA"

/** An age-restricted video, which NewPipeExtractor's own tests use too. */
internal const val AGE_RESTRICTED_VIDEO_ID = "rwcfPqbAx-0"

/** A video ID that no video ever had, which NewPipeExtractor's own tests use too. */
internal const val NEVER_EXISTED_VIDEO_ID = "don-t-exist"

/** A channel that YouTube closed, which NewPipeExtractor's own tests use too. */
internal const val TERMINATED_CHANNEL_ID = "UCTGjY2I-ZUGnwVoWAGRd7XQ"

/** A deleted channel, which NewPipeExtractor's own tests use too. */
internal const val DELETED_CHANNEL_ID = "UCAUc4iz6edWerIjlnL8OSSw"

/** YouTube's host for its web pages and its web client's API. */
private const val WEB_HOST = "www.youtube.com"

/** The host of YouTube's API for its mobile clients. */
private const val API_HOST = "youtubei.googleapis.com"

/**
 * A request that a saved set's call sends, named by its method, host and path. The recorder logs a
 * call's requests by these names, and the tests compare them.
 */
internal enum class Endpoint(val method: String, val host: String, val path: String) {
    /**
     * The answer that NewPipeExtractor reads the web client's version from. The first call in a
     * process that uses the web client fetches it, and every saved set's call is a first call,
     * because resetNewPipeExtractorState makes NewPipeExtractor forget the version that it read.
     */
    SW_JS_DATA("GET", WEB_HOST, "/sw.js_data"),
    SEARCH("POST", WEB_HOST, "/youtubei/v1/search"),
    BROWSE("POST", WEB_HOST, "/youtubei/v1/browse"),
    /** The visionOS and web clients' visitor IDs. */
    VISITOR_ID_ON_WEB_HOST("POST", WEB_HOST, "/youtubei/v1/visitor_id"),
    VISIONOS_PLAYER("POST", API_HOST, "/youtubei/v1/player"),
    WEB_PLAYER("POST", WEB_HOST, "/youtubei/v1/player"),
    /** The rest of a video's page. */
    NEXT("POST", WEB_HOST, "/youtubei/v1/next"),
    /** A subtitle track's lines. */
    TIMEDTEXT("GET", WEB_HOST, "/api/timedtext"),
    /** Any request that isn't one of the above. No saved set expects one. */
    OTHER("", "", "");

    companion object {
        /** The endpoint of [request], or [OTHER]. */
        fun of(request: SavedRequest): Endpoint = of(request.method, request.url.toHttpUrl())

        /** The endpoint of a request with [method] to [url], or [OTHER]. */
        fun of(method: String, url: HttpUrl): Endpoint =
            entries.firstOrNull {
                it.method == method && it.host == url.host && it.path == url.encodedPath
            } ?: OTHER
    }
}

/**
 * A video's page: a visitor ID and a player request for each of YouTube's visionOS and web clients,
 * and the page's other data. Both clients ask for their visitor ID on www.youtube.com, and the
 * visionOS client sends its player request to YouTube's API host. sw.js_data comes before the web
 * client's first request.
 */
private val VIDEO_PAGE =
    listOf(
        Endpoint.VISITOR_ID_ON_WEB_HOST,
        Endpoint.VISIONOS_PLAYER,
        Endpoint.SW_JS_DATA,
        Endpoint.VISITOR_ID_ON_WEB_HOST,
        Endpoint.WEB_PLAYER,
        Endpoint.NEXT,
    )

/**
 * The requests for an age-restricted channel: its page, then two for each of its system playlists
 * of videos and of live streams.
 */
private const val SYSTEM_PLAYLIST_PATH_REQUESTS = 5

/**
 * A video that YouTube says can't be played. NewPipeExtractor stops at the visionOS client's
 * answer.
 */
private val UNPLAYABLE_VIDEO = listOf(Endpoint.VISITOR_ID_ON_WEB_HOST, Endpoint.VISIONOS_PLAYER)

/**
 * The saved sets: each one is a call through the YouTube layer, with YouTube's answers saved in
 * src/test/resources/youtube, in a folder named after the set. The recorder records them, and the
 * tests replay them. docs/testing.md#saved-youtube-responses explains both. The live check makes
 * every set's call against YouTube, as docs/testing.md#the-daily-youtube-check describes.
 *
 * Each set says how its call ends, [expectedError], and which [requests] it sends. Apart from
 * sw.js_data, the requests are the ones that docs/architecture.md#what-each-function-sends counts.
 * The recorder keeps a set's old files when either differs, and the tests check both.
 */
internal enum class SavedSet(
    /** The category that the call fails with, or null when it succeeds. */
    val expectedError: YouTubeError?,
    /** The requests that the call sends, in order. */
    val requests: List<Endpoint>,
    /** The call through the layer. */
    val call: suspend YouTubeLayer.() -> YouTubeResult<*>,
) {
    SEARCH_NASA(null, listOf(Endpoint.SW_JS_DATA, Endpoint.SEARCH), { search(NASA_QUERY) }),
    /** Video details and stream links send the same requests, so their tests share this set. */
    VIDEO_BIG_BUCK_BUNNY(null, VIDEO_PAGE, { videoDetails(VideoId(BIG_BUCK_BUNNY_ID)) }),
    /** The channel's page, which holds its Videos tab, then its Live tab. */
    UPLOADS_NASA(
        null,
        listOf(Endpoint.SW_JS_DATA, Endpoint.BROWSE, Endpoint.BROWSE),
        { latestUploads(ChannelId(NASA_CHANNEL_ID)) },
    ),
    /** The channel's page alone, because the channel has no Live tab. */
    UPLOADS_JAWED(
        null,
        listOf(Endpoint.SW_JS_DATA, Endpoint.BROWSE),
        { latestUploads(ChannelId(JAWED_CHANNEL_ID)) },
    ),
    /**
     * The channel's page, then two requests for each of its system playlists of videos and of live
     * streams, which NewPipeExtractor reads instead of the tabs.
     */
    UPLOADS_AGE_RESTRICTED(
        null,
        listOf(Endpoint.SW_JS_DATA) + List(SYSTEM_PLAYLIST_PATH_REQUESTS) { Endpoint.BROWSE },
        { latestUploads(ChannelId(AGE_RESTRICTED_CHANNEL_ID)) },
    ),
    VIDEO_PRIVATE(
        YouTubeError.PRIVATE,
        UNPLAYABLE_VIDEO,
        { videoDetails(VideoId(PRIVATE_VIDEO_ID)) },
    ),
    /**
     * UNAVAILABLE, not AGE_RESTRICTED. The visionOS client's answer asks the viewer to sign in to
     * confirm their age, and NewPipeExtractor v0.27.1 recognises an age-restricted video only by
     * the Android client's wording, so it reports the generic ContentNotAvailableException.
     */
    VIDEO_AGE_RESTRICTED(
        YouTubeError.UNAVAILABLE,
        UNPLAYABLE_VIDEO,
        { videoDetails(VideoId(AGE_RESTRICTED_VIDEO_ID)) },
    ),
    /**
     * YouTube answers the visionOS client's player request with an error and no reason that
     * NewPipeExtractor tells apart, so it is UNAVAILABLE. Video details and stream links send the
     * same requests, so their tests share this set.
     */
    VIDEO_NEVER_EXISTED(
        YouTubeError.UNAVAILABLE,
        UNPLAYABLE_VIDEO,
        { videoDetails(VideoId(NEVER_EXISTED_VIDEO_ID)) },
    ),
    CHANNEL_TERMINATED(
        YouTubeError.CHANNEL_CLOSED,
        listOf(Endpoint.SW_JS_DATA, Endpoint.BROWSE),
        { latestUploads(ChannelId(TERMINATED_CHANNEL_ID)) },
    ),
    CHANNEL_DELETED(
        YouTubeError.UNAVAILABLE,
        listOf(Endpoint.SW_JS_DATA, Endpoint.BROWSE),
        { latestUploads(ChannelId(DELETED_CHANNEL_ID)) },
    ),
    /**
     * The tracks of "Me at the zoo", two written ones, and the lines of its first written English
     * and German tracks. Listing the tracks sends the requests of a video's page.
     */
    SUBTITLES_ME_AT_THE_ZOO(
        null,
        VIDEO_PAGE + Endpoint.TIMEDTEXT + Endpoint.TIMEDTEXT,
        {
            subtitlesOf(VideoId(ME_AT_THE_ZOO_ID)) { tracks ->
                listOfNotNull(tracks.firstWritten("en"), tracks.firstWritten("de"))
            }
        },
    ),
    /**
     * The tracks of Tears of Steel, and the lines of its first auto-generated English track. It has
     * many written tracks, one of them in Hebrew, which YouTube's code calls iw.
     */
    SUBTITLES_TEARS_OF_STEEL(
        null,
        VIDEO_PAGE + Endpoint.TIMEDTEXT,
        {
            subtitlesOf(VideoId(TEARS_OF_STEEL_ID)) { tracks ->
                listOfNotNull(tracks.firstOrNull { it.isAutoGenerated && it.isInLanguage("en") })
            }
        },
    ),
    /**
     * The tracks of a video with AI-dubbed audio, whose machine-translated tracks the layer leaves
     * out. It reads no lines.
     */
    SUBTITLES_AUTO_DUBBED(
        null,
        VIDEO_PAGE,
        { subtitlesOf(VideoId(AUTO_DUBBED_VIDEO_ID)) { emptyList() } },
    );

    /** The set's folder in src/test/resources/youtube, such as video-big-buck-bunny. */
    val directory: String
        get() = name.lowercase(Locale.ROOT).replace('_', '-')
}

/** The first written track in [language], or null if there is none. */
private fun List<SubtitleTrack>.firstWritten(language: String): SubtitleTrack? = firstOrNull {
    !it.isAutoGenerated && it.isInLanguage(language)
}

/**
 * Whether this track is in [language], a language subtag such as en, whatever region or script its
 * tag adds. YouTube's answers vary: in one of 15 fetches on November 19, 2026, a video listed its
 * auto-generated English track twice, as en and as en-US.
 */
internal fun SubtitleTrack.isInLanguage(language: String): Boolean =
    Locale.forLanguageTag(languageTag).language == language

/**
 * The locale that the recorder and the tests give NewPipeCalls as the device's. Its region, GB, is
 * the country that every saved request asks for, and NewPipeCalls always asks for British English.
 */
internal val SAVED_SET_LOCALE: Locale = Locale.UK
