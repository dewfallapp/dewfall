# Spec Delta

## MODIFIED Requirements

### Requirement: Video details stay private
The app SHALL never log the video's ID, title, channel name or description. It SHALL send them nowhere but YouTube, except the title and channel name, which it SHALL give only to Android's media session, for the media notification, while the video's playback lasts. The only copies kept outside the screen SHALL be the video's ID and its paused position, also user data, in the state Android saves for a process that it ends in the background; the video's watch state, and the details of videos saved with a channel's uploads, in the app's own database on the phone, which no backup or phone transfer copies; and, in memory while its playback lasts, the playing video's ID, title, channel name and stream links in the playback service, which it SHALL drop when that playback is replaced or stops. The media session SHALL identify the video only by a random playback ID, and SHALL hold no video ID.

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
- **THEN** the video's ID, position and times are only in the app's database, and the app's log lines hold none of them

## ADDED Requirements

### Requirement: Dewfall remembers where each video was left
Saving a video's position SHALL keep it on the phone with the time it was saved, for that video's ID, whether or not the video's details or its channel are saved. The latest save SHALL replace the earlier one. The player starts saving positions with resume and mark as watched (#50). A video that never had a position saved and was never marked SHALL have no watch state.

#### Scenario: Saving a position
- **WHEN** the position 12 minutes 30 seconds is saved for a video
- **THEN** that video's watch state holds 12 minutes 30 seconds and the time of the save, and it isn't marked watched

#### Scenario: A later position
- **WHEN** a video's position was saved at 12 minutes, and later at 20 minutes
- **THEN** its watch state holds 20 minutes and the time of the later save

#### Scenario: A video from search
- **WHEN** a position is saved for a video whose channel isn't subscribed and whose details aren't saved
- **THEN** that video's watch state holds the position

#### Scenario: A video never played
- **WHEN** no position was saved and nothing was marked for a video
- **THEN** that video has no watch state

#### Scenario: After the app closes
- **WHEN** a position was saved and the app's process ends and starts again
- **THEN** the video's watch state still holds that position

### Requirement: Dewfall remembers when a video was marked watched
Marking a video watched SHALL keep the time of marking on the phone. Marking it watched again SHALL keep the first time, so a rule that counts from when a video was watched stays the same. Marking it unwatched SHALL clear that time. Neither SHALL change the saved position.

#### Scenario: Marking watched
- **WHEN** a video with a saved position of 40 minutes is marked watched
- **THEN** its watch state is marked watched with the time of marking, and still holds 40 minutes

#### Scenario: Marking watched with no position
- **WHEN** a video with no watch state is marked watched
- **THEN** its watch state is marked watched, with a position of zero

#### Scenario: Marking watched again
- **WHEN** a video was marked watched on Monday and is marked watched again on Wednesday
- **THEN** it stays marked watched since Monday

#### Scenario: Marking unwatched
- **WHEN** a video marked watched is marked unwatched
- **THEN** it is no longer marked watched, and its position stays as it was

#### Scenario: A new position after watching
- **WHEN** a video marked watched gets a new saved position
- **THEN** it stays marked watched since the first time, and holds the new position
