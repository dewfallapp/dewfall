# Spec Delta

## ADDED Requirements

### Requirement: A downloaded video plays from the phone
A video's files match its download when the download recorded their formats, and each is a regular file in the app's download storage, not empty, at the size recorded for it if any, and all together at the recorded total. When playback of a video starts, its download is done and its files match it, the player SHALL play those files instead of streaming. It SHALL ask for no stream links and fetch no bytes, so it plays without a connection. The rest of playback SHALL work as it does for a stream.

#### Scenario: Opening a downloaded video
- **WHEN** the user opens a video that the player doesn't hold, whose download says "Downloaded" and whose files match it
- **THEN** the video plays from the files on the phone, and the app asks YouTube for neither its stream links nor its bytes

#### Scenario: Opening a downloaded video offline
- **WHEN** the phone has no connection, and the user opens from the inbox a downloaded video that the player doesn't hold, whose files match its download
- **THEN** the video plays from its files, and the details area says that YouTube can't be reached, with "Try again"

#### Scenario: Paused for hours
- **WHEN** a downloaded video playing from its files is paused for a day, its files still match its download, and the user presses play
- **THEN** it plays on from where it paused, with no request for its stream links or its bytes

### Requirement: Any other video streams
Every video that can't play from finished downloaded files SHALL stream when its playback starts, as before: one whose download waits, runs, is paused or failed, because its files are partial; one whose downloaded files don't match its download; and any video while a read error, such as a damaged database, stops the check of its download. Files that match their download SHALL count as matching even if one of them can't be opened. Checking SHALL NOT change the download's status.

#### Scenario: A download that isn't finished
- **WHEN** the user opens a video that the player doesn't hold, whose download is at 40 percent, or paused or failed with part of its files
- **THEN** the video streams, as a video with no download does, and its download goes on as before

#### Scenario: A downloaded file that is missing
- **WHEN** a video's download says "Downloaded", but its files don't match it: one of them is gone from the phone, is empty or has another length than the download recorded for it, or all of them together miss the recorded total, and the user opens the video, which the player doesn't hold
- **THEN** the video streams, and its download still says "Downloaded"

#### Scenario: A downloaded file that can't be opened
- **WHEN** a video's download says "Downloaded", its files match it, one of them can't be opened, and the user opens the video, which the player doesn't hold
- **THEN** the video plays from its files and fails, as the failure requirement says, and doesn't stream

### Requirement: The player chooses between the files and the stream when playback starts
The player SHALL choose between the downloaded files and the stream only when playback starts: when the user opens a video that the player doesn't hold, taps "Try again" or plays after the session was gone, and when expired links are fetched again. A video that the player already holds, playing, paused or failed, SHALL go on as it is until one of those starts it again, a refresh of expired links included, as "Opening the video that already plays goes on with it" says.

#### Scenario: A download that finishes while the video streams
- **WHEN** a video streams while its download finishes, its files match it, its playback doesn't start again, then the user closes the mini player, and later opens the video again
- **THEN** it streams until the mini player closes, and the next opening plays it from its files

## MODIFIED Requirements

### Requirement: Expired links are fetched again
When YouTube refuses a stream link, the player SHALL fetch new links once and go on from the same position. If the new links fail too, it SHALL stop and say that playback failed. When the video's downloaded files can play by then, as the requirement on downloaded videos says, the player SHALL play them instead, from the same position, and fetch no links.

#### Scenario: An expired link
- **WHEN** a video that doesn't play from its files paused long enough for its links to expire, it still has no downloaded files that can play, and the user presses play
- **THEN** the player fetches new links and plays on from where it paused

#### Scenario: A download that finished before the links expired
- **WHEN** a video streams, its download finishes and its files match it, and later YouTube refuses one of its links
- **THEN** the player plays on from the downloaded files, from the same position, and fetches no new links

### Requirement: Quality follows the network
On a metered network the player SHALL stream a picture whose shorter side is at most 480 pixels, with the lowest audio bitrate. On an unmetered one, at most 1080 pixels, with the highest. Within the limit it SHALL prefer formats the phone decodes in hardware, then the largest. It SHALL take only formats the phone can decode, the smallest one when none is within the limit, and fail as not decodable when no video or no audio format decodes, and SHALL keep its choice until playback starts again. These limits apply only to a video that streams: a video that plays from its downloaded files plays them as they are, at the quality the download kept, on any network.

#### Scenario: Mobile data
- **WHEN** a video that doesn't play from its files, with formats up to 2160p, starts playing on mobile data
- **THEN** the player streams a format of 480p or less

#### Scenario: Wi-Fi
- **WHEN** a video that doesn't play from its files, with formats up to 2160p, starts playing on Wi-Fi, on a phone that decodes them all in hardware
- **THEN** the player streams a 1080p format

#### Scenario: A portrait video
- **WHEN** a portrait video that doesn't play from its files, with formats up to 2160x3840, starts playing on Wi-Fi
- **THEN** the player streams a 1080x1920 format

#### Scenario: Audio on mobile data
- **WHEN** a video that doesn't play from its files, with dubbed and original audio tracks, starts playing on mobile data
- **THEN** the player streams the original track at its lowest bitrate

#### Scenario: Audio on Wi-Fi
- **WHEN** the same video starts playing on Wi-Fi
- **THEN** the player streams the original track at its highest bitrate

#### Scenario: Hardware before size
- **WHEN** on Wi-Fi a video that doesn't play from its files has a 1080p format that decodes only in software and a 720p format that decodes in hardware
- **THEN** the player streams the 720p format

#### Scenario: Nothing within the limit
- **WHEN** a video that doesn't play from its files has only 720p and 1080p decodable formats, and it starts playing on mobile data
- **THEN** the player streams the 720p format, the smallest it can decode

#### Scenario: A format the phone can't decode
- **WHEN** the best format at the limit of a video that doesn't play from its files uses a codec the phone can't decode
- **THEN** the player streams the best format at or below the limit that it can decode

#### Scenario: A downloaded video on mobile data
- **WHEN** a video downloaded at 720p, whose files match its download, starts playing on mobile data
- **THEN** the player plays the 720p file from the phone

### Requirement: A playback failure says why
When a video can't play, the video area SHALL say why in words, and SHALL offer "Try again" when trying again can help: when YouTube can't be reached, refuses to answer, can't be read, the network failed while playing, or playback stopped for another reason. When the phone can decode none of the video's formats, or its decoder fails while decoding, it SHALL say so with no "Try again". The details below SHALL stay. When the player has to open a downloaded file again, such as for a seek, and can't, because the user removed the download or the file is gone, the video area SHALL say that the video stopped playing, with "Try again", which streams it once its files don't match its download. While a file stays open, the video MAY play on from it after the file is deleted.

#### Scenario: No connection while playing
- **WHEN** a video that streams plays, the network goes away, and the player runs out of data
- **THEN** the video area says that YouTube can't be reached, with "Try again", and the details stay

#### Scenario: Trying again
- **WHEN** the user taps "Try again" after a playback failure
- **THEN** the player fetches the links again, or plays the downloaded files when they can play, and plays from where it stopped, unless the cause is still there, such as a downloaded file that can't be opened, which fails again

#### Scenario: No format the phone can decode
- **WHEN** the phone can decode none of the video's formats
- **THEN** the video area says that this video can't play on this phone, with no "Try again"

#### Scenario: A decoder that fails to start
- **WHEN** the phone's decoder for the chosen format fails to start, such as while another app holds it
- **THEN** the video area says that the video stopped playing, with "Try again"

#### Scenario: Details and playback both fail
- **WHEN** the network is off as the user opens a video that the player doesn't hold and that doesn't play from its files
- **THEN** the screen says once that YouTube can't be reached, with one "Try again" that retries both the details and the video

#### Scenario: Downloaded files that go away while playing
- **WHEN** a video plays from its downloaded files, one of them is removed from the phone, and the user seeks back to a point the player no longer holds, so it opens the file again
- **THEN** the video area says that the video stopped playing, with "Try again", and "Try again" streams the video from where it stopped

### Requirement: Playback stays private
The player SHALL fetch video only through the YouTube layer, which SHALL send its requests only over HTTPS to googlevideo.com and its subdomains, and SHALL never follow a redirect to another host. A video that plays from its downloaded files SHALL send no request at all for its playback, and the player SHALL read only files in the app's download storage. The app SHALL never log a stream link, a video's ID, its title or a downloaded file's path or name, and Media3 SHALL write nothing to the log. Android's media session SHALL hold no video ID, link or file path, and no text but the title, the channel name and a constant error. ExoPlayer SHALL report no playback session to Android's media metrics.

#### Scenario: Logs while playing
- **WHEN** the user plays a video that doesn't play from its files, once to the end and once with the network turned off while it plays
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

### Requirement: Video details stay private
The app SHALL never log the video's ID, title, channel name or description. It SHALL send them nowhere but YouTube, except the title and channel name, which it SHALL give only to Android's media session, for the media notification, while the video's playback lasts. The only copies kept outside the screen SHALL be the video's ID and its paused position, also user data, in the state Android saves for a process that it ends in the background; the video's watch state, the details of videos saved with a channel's uploads, and, for a video the user downloads, its summary (ID, title, channel ID and name, length, publish time, kind and thumbnail links) and its download's record (state, chosen formats, sizes and progress), in the app's own database on the phone, which no backup or phone transfer copies; the downloaded video's files, in the app's own storage, which no backup or phone transfer copies either; and, in memory while its playback lasts, the playing video's ID, title, channel name and stream links, or the paths of its downloaded files, in the playback service, which it SHALL drop when that playback is replaced or stops, once that playback's last save of its position has run. The media session SHALL identify the video only by a random playback ID, and SHALL hold no video ID.

#### Scenario: Logs after opening a video
- **WHEN** the user opens a video, once with details showing and once with the network off
- **THEN** the app's log lines hold no ID, title, channel name or description, and the failure's line names only the call and its category

#### Scenario: After closing the video screen
- **WHEN** the user minimizes the video screen, then closes the mini player
- **THEN** Android's media session no longer holds the video's title or channel name

#### Scenario: After minimizing a video that couldn't start
- **WHEN** the user minimizes a video whose stream links failed to load
- **THEN** the playback service holds nothing about that video

#### Scenario: A saved watch state
- **WHEN** a video's position is saved or it is marked watched
- **THEN** the app's database holds the video's ID, position and times, beside the copies this requirement already allows, and the app's log lines hold none of them

#### Scenario: A downloaded video
- **WHEN** the user downloads a video
- **THEN** the app's database holds its summary and its download's record, its files sit in the app's own storage, and the app's log lines hold none of them
