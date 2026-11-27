# Spec Delta

## ADDED Requirements

### Requirement: A downloaded video plays from the phone
When playback of a video starts and the video's download is done, with every file it kept on the phone at the size the download recorded, the player SHALL play those files instead of streaming. It SHALL ask YouTube for no stream links and fetch no bytes for that video, so it plays without a connection. Everything else about playback SHALL work as it does for a video that streams.

#### Scenario: Opening a downloaded video
- **WHEN** the user opens a video whose download says "Downloaded"
- **THEN** the video plays from the files on the phone, and the app sends no request for its stream links or its bytes

#### Scenario: Opening a downloaded video offline
- **WHEN** the phone has no connection and the user opens a downloaded video from the inbox
- **THEN** the video plays from its files, and the details area says that YouTube can't be reached, with "Try again"

#### Scenario: Paused for hours
- **WHEN** a downloaded video playing from its files is paused for a day, and the user presses play
- **THEN** it plays on from where it paused, with no request to YouTube

### Requirement: Any other video streams
Every video that can't play from finished downloaded files SHALL stream, as before: one whose download waits, runs, is paused or failed, because its files are partial; one whose downloaded files are missing or whose sizes don't match what the download recorded; and any video while the phone can't read its download. Checking the files SHALL NOT change the download's status.

#### Scenario: A download that isn't finished
- **WHEN** the user opens a video whose download is at 40 percent, or paused or failed with part of its files
- **THEN** the video streams, as a video with no download does, and its download goes on as before

#### Scenario: A downloaded file that is missing
- **WHEN** a video's download says "Downloaded", but one of its files is gone from the phone or is shorter than the download recorded, and the user opens the video
- **THEN** the video streams, and its download still says "Downloaded"

### Requirement: The player chooses between the files and the stream when playback starts
The player SHALL choose between the downloaded files and the stream each time playback starts: when the video opens, when the user taps "Try again" or plays after the session was gone, and when expired links are fetched again. A video that streams while its download finishes SHALL go on streaming until then.

#### Scenario: A download that finishes while the video streams
- **WHEN** a video streams while its download finishes, and later the user opens it again
- **THEN** it goes on streaming until the user opens it again, and then plays from its files

## MODIFIED Requirements

### Requirement: Quality follows the network
On a metered network the player SHALL stream a picture whose shorter side is at most 480 pixels, with the lowest audio bitrate. On an unmetered one, at most 1080 pixels, with the highest. Within the limit it SHALL prefer formats the phone decodes in hardware, then the largest. It SHALL take only formats the phone can decode, the smallest one when none is within the limit, and fail as not decodable when no video or no audio format decodes, and SHALL keep its choice until playback starts again. These limits apply only to a video that streams: a video that plays from its downloaded files plays them as they are, at the quality the download kept, on any network.

#### Scenario: Mobile data
- **WHEN** a video with formats up to 2160p starts playing on mobile data
- **THEN** the player streams a format of 480p or less

#### Scenario: Wi-Fi
- **WHEN** a video with formats up to 2160p starts playing on Wi-Fi, on a phone that decodes them all in hardware
- **THEN** the player streams a 1080p format

#### Scenario: A portrait video
- **WHEN** a portrait video with formats up to 2160x3840 starts playing on Wi-Fi
- **THEN** the player streams a 1080x1920 format

#### Scenario: Audio on mobile data
- **WHEN** a video with dubbed and original audio tracks starts playing on mobile data
- **THEN** the player streams the original track at its lowest bitrate

#### Scenario: Audio on Wi-Fi
- **WHEN** the same video starts playing on Wi-Fi
- **THEN** the player streams the original track at its highest bitrate

#### Scenario: Hardware before size
- **WHEN** on Wi-Fi a video's 1080p format decodes only in software and its 720p format in hardware
- **THEN** the player streams the 720p format

#### Scenario: Nothing within the limit
- **WHEN** a video's only decodable formats are 720p and 1080p, and it starts playing on mobile data
- **THEN** the player streams the 720p format, the smallest it can decode

#### Scenario: A format the phone can't decode
- **WHEN** the best format at the limit uses a codec the phone can't decode
- **THEN** the player streams the best format at or below the limit that it can decode

#### Scenario: A downloaded video on mobile data
- **WHEN** a video downloaded at 720p starts playing on mobile data
- **THEN** the player plays the 720p file from the phone

### Requirement: A playback failure says why
When a video can't play, the video area SHALL say why in words, and SHALL offer "Try again" when trying again can help: when YouTube can't be reached, refuses to answer, can't be read, the network failed while playing, or playback stopped for another reason. When the phone can decode none of the video's formats, or its decoder fails while decoding, it SHALL say so with no "Try again". The details below SHALL stay. When the player can no longer read the downloaded files that a video plays from, such as after the user removed its download, the video area SHALL say that the video stopped playing, with "Try again", which streams it once a file is gone or has the wrong size.

#### Scenario: No connection while playing
- **WHEN** the network goes away and the player runs out of data
- **THEN** the video area says that YouTube can't be reached, with "Try again", and the details stay

#### Scenario: Trying again
- **WHEN** the user taps "Try again" after a playback failure
- **THEN** the player fetches the links again, or plays the downloaded files when they can play, and plays from where it stopped

#### Scenario: No format the phone can decode
- **WHEN** the phone can decode none of the video's formats
- **THEN** the video area says that this video can't play on this phone, with no "Try again"

#### Scenario: A decoder that fails to start
- **WHEN** the phone's decoder for the chosen format fails to start, such as while another app holds it
- **THEN** the video area says that the video stopped playing, with "Try again"

#### Scenario: Details and playback both fail
- **WHEN** the network is off as the user opens a video that isn't downloaded
- **THEN** the screen says once that YouTube can't be reached, with one "Try again" that retries both the details and the video

#### Scenario: Downloaded files that go away while playing
- **WHEN** a video plays from its downloaded files and one of them is removed from the phone
- **THEN** the video area says that the video stopped playing, with "Try again", and "Try again" streams the video from where it stopped

### Requirement: Playback stays private
The player SHALL fetch video only through the YouTube layer, which SHALL send its requests only over HTTPS to googlevideo.com and its subdomains, and SHALL never follow a redirect to another host. A video that plays from its downloaded files SHALL send no request at all for its playback, and the player SHALL read only files in the app's own download storage. The app SHALL never log a stream link, a video's ID, its title or a downloaded file's path or name, and Media3 SHALL write nothing to the log. Android's media session SHALL hold no video ID, link or file path, and no text but the title, the channel name and a constant error. ExoPlayer SHALL report no playback session to Android's media metrics.

#### Scenario: Logs while playing
- **WHEN** the user plays a video, once to the end and once with the network turned off while it plays
- **THEN** the app's log lines hold no link, ID or title, and the failure's line names only its category and Media3's error code

#### Scenario: Logs while playing a downloaded video
- **WHEN** the user plays a downloaded video from its files, to the end
- **THEN** the app's log lines hold no ID, title, file path or file name

#### Scenario: A redirect to another host
- **WHEN** a request for a stream link is redirected to a host outside googlevideo.com
- **THEN** the redirected request isn't sent, and the video area says that YouTube isn't loading right now

#### Scenario: The media session
- **WHEN** a video plays and another app reads Android's media sessions
- **THEN** the session's item has a random ID, the video's title and channel name, and no video ID, link or file path

#### Scenario: The media session after a failure
- **WHEN** a video failed to play and another app reads Android's media sessions
- **THEN** the session's error message is the same constant text for every failure
