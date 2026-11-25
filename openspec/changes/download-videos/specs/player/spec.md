# Spec Delta

## ADDED Requirements

### Requirement: The video screen downloads the video
Once the details show, unless the video is live or waiting to start, the video screen SHALL show a download chip in the chip row under the publish line, before the watched chip, following the video's download as the downloads spec describes:

- Not downloaded: "Download", with the `download` glyph. A tap starts the download.
- Waiting: "Waiting to download", with the `schedule` glyph. A tap pauses it.
- Downloading: "Downloading, 42%", with a small progress ring, or "Downloading" while the size is unknown. A tap pauses it.
- Paused: "Paused, 42%", with the `download` glyph. A tap goes on.
- Failed: "Download failed", or "Not enough space on this phone", with the `error` glyph in the error color. A tap tries again.
- Downloaded: "Downloaded", selected, with a check. A tap does nothing, because removing a download comes with the Downloads screen (#55).

A screen reader SHALL read the chip's words, and the glyph or ring SHALL add nothing. The chip SHALL show in the row even while the watch state can't be read. When the video's download state can't be read, no download chip SHALL show, and the rest of the screen SHALL work.

#### Scenario: Downloading from the video screen
- **WHEN** the user taps "Download" on a video's screen
- **THEN** the chip says "Waiting to download" or "Downloading, 0%", then counts up, and says "Downloaded", selected, with a check, when the download is done, even if the user left the screen and came back

#### Scenario: A live stream
- **WHEN** the video screen shows a live stream
- **THEN** no download chip shows

#### Scenario: Pausing from the video screen
- **WHEN** the user taps "Downloading, 42%"
- **THEN** the chip says "Paused, 42%", and a second tap goes on

#### Scenario: Fullscreen and the mini player
- **WHEN** the video shows in fullscreen or in the mini player
- **THEN** no download chip shows

#### Scenario: Large font
- **WHEN** the font size is at its largest
- **THEN** the chip's words show in full, wrapping if they need to, and the chips wrap onto a new line

## MODIFIED Requirements

### Requirement: Video details stay private
The app SHALL never log the video's ID, title, channel name or description. It SHALL send them nowhere but YouTube, except the title and channel name, which it SHALL give only to Android's media session, for the media notification, while the video's playback lasts. The only copies kept outside the screen SHALL be the video's ID and its paused position, also user data, in the state Android saves for a process that it ends in the background; the video's watch state, the details of videos saved with a channel's uploads, and the ID, title, channel and length of a video the user downloads, with its download's state, in the app's own database on the phone, which no backup or phone transfer copies; and, in memory while its playback lasts, the playing video's ID, title, channel name and stream links in the playback service, which it SHALL drop when that playback is replaced or stops, once that playback's last save of its position has run. The media session SHALL identify the video only by a random playback ID, and SHALL hold no video ID.

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
- **THEN** the app's database holds its ID, title, channel and length with the download's state, its files sit in the app's own storage, and the app's log lines hold none of them
