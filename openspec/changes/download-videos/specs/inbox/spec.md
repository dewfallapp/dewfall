# Spec Delta

## ADDED Requirements

### Requirement: Each inbox video shows its download
Each video row in the inbox SHALL end its meta line with the design system's download status, which follows the video's download as the downloads spec describes:

- Not downloaded: an outlined icon button with the `download` glyph, which a screen reader hears as "Download". A tap starts the download.
- Waiting: the `schedule` glyph and "Waiting to download", and no button.
- Downloading: "Downloading, 42%" and a 40dp progress ring with the `pause` glyph, which a screen reader hears as "Pause download". A tap pauses it. While the size is unknown, the words are "Downloading" and the ring turns without a percentage.
- Paused: "Paused, 42%" and the download button, heard as "Go on downloading". A tap goes on.
- Failed: the `error` glyph in the error color, "Download failed" or "Not enough space on this phone", and the download button, heard as "Try downloading again". A tap tries again.
- Downloaded: the 32dp round `check` mark, which is not a button and which a screen reader hears as "Downloaded".

Tapping the row anywhere else SHALL still open the video. The row's text SHALL read as one node, and the status control SHALL be a focus target of its own. When the download states can't be read, the rows SHALL show no status control, and the inbox SHALL work as before.

#### Scenario: Downloading from the inbox
- **WHEN** the user taps a row's download button
- **THEN** the video doesn't open, and the row says "Waiting to download" or "Downloading", then shows the check mark once the download is done

#### Scenario: Pausing from the inbox
- **WHEN** the user taps the progress ring of a row that downloads
- **THEN** the row says "Paused" with its percentage and shows the download button again

#### Scenario: A screen reader
- **WHEN** a screen reader moves through a downloaded row
- **THEN** it reads the row's title, length and time ago as one item, then "Downloaded" as the next

#### Scenario: A large font
- **WHEN** the font size is at its largest
- **THEN** the status words wrap, and the button stays at the end of the meta line, at least 48dp square to touch

## MODIFIED Requirements

### Requirement: The inbox keeps videos private
The inbox SHALL never log a video's ID or title, or a channel's ID or name, and SHALL send nothing to any server, apart from the requests of a check for new videos that the user started, and of a download that the user started, which go only to YouTube, through the YouTube layer. Showing the inbox SHALL send no request to YouTube, apart from going on with a download the user started that Android couldn't schedule while the app didn't show, as the downloads spec says.

#### Scenario: Opening the inbox offline
- **WHEN** the phone has no connection and the user opens the app
- **THEN** the inbox shows the new videos as the phone keeps them, and no request leaves the phone

#### Scenario: Checking for new videos
- **WHEN** the user taps "Check for new videos"
- **THEN** the only requests that leave the phone go to YouTube, for the subscribed channels' uploads, and the app's log holds no channel or video ID, name or title

#### Scenario: Downloading a video
- **WHEN** the user taps a row's download button
- **THEN** the only requests that leave the phone go to YouTube, for that video's stream links and files, and the app's log holds no video ID or title
