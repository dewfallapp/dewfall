# Spec Delta

## Purpose

Keeps the channels a user subscribes to, and those channels' videos, on the phone, so the inbox, the channels screen and the overnight downloads can read them without asking YouTube again.

## ADDED Requirements

### Requirement: Subscriptions are kept on the phone
Subscribing to a channel SHALL keep its ID, name, avatars, description and the time of subscribing on the phone, until the user unsubscribes. They SHALL still be there after the app closes, the phone restarts or the app is updated, until a schema change before the first public release asks for the app's data to be cleared.

#### Scenario: Subscribing
- **WHEN** the user subscribes to a channel
- **THEN** the list of subscribed channels holds it, reading that channel gives its name, avatars, description and the time of subscribing, and the channel counts as subscribed

#### Scenario: After the app closes
- **WHEN** the user subscribed to a channel and the app's process ends and starts again
- **THEN** the channel is still subscribed, with the same details

#### Scenario: A channel never subscribed to
- **WHEN** the user has not subscribed to a channel
- **THEN** the channel does not count as subscribed, reading it gives nothing, and the list of subscribed channels doesn't hold it

### Requirement: Subscribing twice keeps one subscription
Subscribing to a channel that is already subscribed SHALL change nothing. The channel SHALL appear once in the list, and its time of subscribing SHALL stay the first one.

#### Scenario: Subscribing again
- **WHEN** the user subscribed to a channel yesterday and subscribes to it again today
- **THEN** the list holds the channel once, with yesterday's time of subscribing and the details it already had

### Requirement: Unsubscribing removes only the subscription
Unsubscribing SHALL remove the channel from the subscribed channels. It SHALL keep the channel's saved videos, and what the user watched of them, on the phone. Unsubscribing from a channel that isn't subscribed SHALL change nothing.

#### Scenario: Unsubscribing
- **WHEN** the user unsubscribes from a subscribed channel
- **THEN** the channel no longer counts as subscribed, and the list of subscribed channels doesn't hold it

#### Scenario: Watched videos stay
- **WHEN** the user unsubscribes from a channel whose saved videos include one they half watched and one they marked watched
- **THEN** both videos are still saved, the first keeps its position and the second stays marked watched

#### Scenario: Subscribing again later
- **WHEN** the user unsubscribes from a channel and later subscribes to it again
- **THEN** the time of subscribing is the new one

### Requirement: Saving a channel's uploads keeps every video
Saving a channel's latest uploads SHALL keep each video on the phone with its ID, title, channel ID and name, length, publish time and whether it is approximate, thumbnails, kind, and the time Dewfall first saved it. A video that a newer save no longer holds SHALL stay saved.

#### Scenario: First save
- **WHEN** a channel's latest uploads are saved for the first time
- **THEN** every video in them is saved with its details, and the time Dewfall first saved it is the time of this save

#### Scenario: A video that left the list
- **WHEN** a channel's newer uploads no longer hold a video that an earlier save held
- **THEN** that video is still saved, with its details from the earlier save

### Requirement: Saved videos keep YouTube's order
Reading a channel's saved videos SHALL give them newest first by publish time, with videos that have no publish time last. Videos with the same publish time, and videos with none, SHALL keep the order of the latest save that held them, and videos that the latest save held SHALL come before those it no longer held.

#### Scenario: Equal times
- **WHEN** a channel's uploads hold three videos A, B and C, in that order, all with the approximate time "3 days ago", and they are saved
- **THEN** reading the channel's saved videos gives A, B and C in that order

#### Scenario: No times
- **WHEN** a save holds two videos with no publish time, D before E, and others with times
- **THEN** reading the channel's saved videos gives the videos with times first, newest first, then D, then E

#### Scenario: A video a later save no longer holds
- **WHEN** a video was saved with the same publish time as videos that a later save holds
- **THEN** reading the channel's saved videos gives it after them

#### Scenario: Saves at the same moment, or with a clock set back
- **WHEN** a later save runs at the same time on the phone's clock as an earlier one, or at an earlier time after the clock was set back, and holds fewer of the videos with the same publish time
- **THEN** reading the channel's saved videos still gives the later save's videos first

### Requirement: Saving again updates YouTube's details
Saving a video that is already saved SHALL take the new title, channel ID, channel name and kind. It SHALL take the new length and thumbnails, unless the new save has none, and then keep the saved ones. It SHALL keep the time Dewfall first saved the video.

#### Scenario: A changed title
- **WHEN** a video was saved yesterday, its title changed on YouTube, and the channel's uploads are saved again today
- **THEN** the video has the new title, and the time Dewfall first saved it is still yesterday's

#### Scenario: A premiere that started
- **WHEN** a premiere was saved as upcoming with no length, and a later save holds it as a video with a length
- **THEN** the saved video is a video with that length

#### Scenario: Thumbnails missing from a later save
- **WHEN** a video was saved with thumbnails, and a later save holds it with none
- **THEN** the saved video keeps its thumbnails

### Requirement: A later save never makes a publish time vaguer
YouTube often gives a relative publish time, such as "3 days ago", which becomes vaguer as the video ages, such as "3 weeks ago". A save SHALL take the new publish time only when it is exact, when no time was saved, or when the video was upcoming and no longer is. Otherwise it SHALL keep the saved time and whether it is approximate. A save with no publish time SHALL keep the saved one.

#### Scenario: An approximate time after an exact one
- **WHEN** a video was saved with an exact publish time, and a later save gives an approximate one
- **THEN** the saved video keeps the exact time

#### Scenario: Two approximate times
- **WHEN** a video was saved with the approximate time "3 days ago", and a save two weeks later gives the approximate time "2 weeks ago"
- **THEN** the saved video keeps the time from the first save

#### Scenario: An exact time after an approximate one
- **WHEN** a video was saved with an approximate time, and a later save gives an exact one
- **THEN** the saved video takes the exact time, no longer marked approximate

#### Scenario: A time where none was saved
- **WHEN** a video was saved with no publish time, and a later save gives an approximate one
- **THEN** the saved video takes the approximate time

#### Scenario: No time in a later save
- **WHEN** a video was saved with a publish time, and a later save gives none
- **THEN** the saved video keeps its time

#### Scenario: An upcoming video that started
- **WHEN** a premiere was saved as upcoming with its scheduled start, and a later save holds it as a video with an approximate publish time
- **THEN** the saved video takes the new approximate time

### Requirement: Saving a channel's uploads refreshes its details
When a subscribed channel's uploads are saved, its name SHALL become the one in the uploads, and so SHALL its avatars and description, unless the uploads have none, and then the saved ones stay. Its time of subscribing SHALL stay the same. Saving the uploads of a channel that isn't subscribed SHALL NOT subscribe to it.

#### Scenario: A renamed channel
- **WHEN** a subscribed channel changed its name on YouTube and its uploads are saved
- **THEN** the subscribed channel has the new name, and the same time of subscribing

#### Scenario: A subscription from a file
- **WHEN** a channel was subscribed with only its ID and name, and its uploads are saved
- **THEN** the subscribed channel gets the avatars and description from the uploads

#### Scenario: A channel that isn't subscribed
- **WHEN** the uploads of a channel the user hasn't subscribed to are saved
- **THEN** the channel still doesn't count as subscribed

### Requirement: What Dewfall keeps stays on the phone
The subscriptions, the saved videos and the watch state SHALL stay in the app's own storage. The app SHALL NOT log them, SHALL send them nowhere but YouTube, in the requests the YouTube layer makes, and SHALL NOT let Android copy them off the phone, neither in a backup to Google Drive nor in a transfer to a new Android phone or an iPhone.

#### Scenario: Backup to Google Drive
- **WHEN** the phone backs up its apps to Google Drive
- **THEN** none of Dewfall's files, its database included, is part of the backup

#### Scenario: Moving to a new phone
- **WHEN** the user moves to a new Android phone with a cable or over Wi-Fi, and the old phone offers to copy apps and their data
- **THEN** none of Dewfall's files, its database included, is copied

#### Scenario: Moving to an iPhone
- **WHEN** the user moves from an Android phone to an iPhone, and Android offers to copy apps' data
- **THEN** none of Dewfall's files is copied, because Dewfall doesn't opt in to that transfer

#### Scenario: Saving and reading
- **WHEN** a subscription, a video or a watch state is saved or read
- **THEN** nothing about it is written to the app's log, and no request leaves the phone
