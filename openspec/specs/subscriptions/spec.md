# subscriptions Specification

## Purpose

Lets a user open a channel's page, see its latest uploads, and subscribe to it or unsubscribe from it there, or from Search YouTube's channel results. Keeps the channels a user subscribes to, and those channels' videos, on the phone, so the inbox, the channels screen and the overnight downloads can read them without asking YouTube again.

## Requirements

### Requirement: Subscriptions are kept on the phone
Subscribing to a channel SHALL keep its ID, name, avatars, its description when one is given, and the time of subscribing on the phone, until the user unsubscribes. They SHALL still be there after the app closes, the phone restarts or the app is updated, until a schema change before the first public release asks for the app's data to be cleared.

#### Scenario: Subscribing
- **WHEN** the user subscribes to a channel
- **THEN** the list of subscribed channels holds it, reading that channel gives its name, avatars, description and the time of subscribing, and the channel counts as subscribed

#### Scenario: After the app closes
- **WHEN** the user subscribed to a channel and the app's process ends and starts again
- **THEN** the channel is still subscribed, with the same details

#### Scenario: A channel never subscribed to
- **WHEN** the user has not subscribed to a channel
- **THEN** the channel does not count as subscribed, reading it gives nothing, and the list of subscribed channels doesn't hold it

#### Scenario: A subscription with no description
- **WHEN** the user subscribes from Search YouTube's channel results, which keep no description
- **THEN** reading that channel gives its name, avatars, no description and the time of subscribing, until a save of its uploads brings one, as "Saving a channel's uploads refreshes its details" says

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

### Requirement: The channel page loads the channel
When a channel page opens, it SHALL ask YouTube once for the channel and its latest uploads, and show that it is loading until the answer arrives. A configuration change SHALL NOT ask again.

#### Scenario: Opening a channel page
- **WHEN** the user opens a channel's page
- **THEN** the page says that the channel is loading, and the app asks YouTube for the channel and its latest uploads once

#### Scenario: Turning a tablet
- **WHEN** a channel page shows its uploads on a tablet and the user turns it
- **THEN** the same channel and uploads show, and nothing is sent to YouTube again

### Requirement: The channel page shows the channel
The page SHALL have a back arrow, named for screen readers, then a round emblem with the first user-perceived character of the channel's name, the name as a heading, and the channel's description. The emblem SHALL be hidden from screen readers, which read the name. The name SHALL wrap and never be cut off. A channel with no description SHALL show nothing in its place.

#### Scenario: A channel
- **WHEN** the channel is "Kettle and Crumb", with a two-line description
- **THEN** the page shows an emblem with "K", the name, and both lines

#### Scenario: No description
- **WHEN** the channel has no description
- **THEN** the page shows the name, then the subscription card

### Requirement: A long description folds
The description SHALL show at most three lines, with a "More" button that shows all of it and then becomes "Less", which folds it again. A description that fits in three lines SHALL have no button.

#### Scenario: A short description
- **WHEN** the description takes two lines
- **THEN** the page shows both lines and no button

#### Scenario: A long description
- **WHEN** the channel's description takes ten lines
- **THEN** the page shows its first three lines and "More", and tapping "More" shows all ten lines and "Less"

### Requirement: The page says whether the user is subscribed
Under the channel, the page SHALL show a card that says in words whether the user is subscribed, with a glyph as a second signal and never color alone, and the one action that changes it. The card SHALL show only once the page knows both the channel and whether the user is subscribed to it.

#### Scenario: A channel the user follows
- **WHEN** the user opens the page of a channel they subscribed to
- **THEN** the card shows a check glyph, "Subscribed", "New videos show in your inbox.", and a text button "Unsubscribe"

#### Scenario: A channel the user doesn't follow
- **WHEN** the user opens the page of a channel they didn't subscribe to
- **THEN** the card shows an add glyph, "Not subscribed", "Subscribe to see this channel's new videos in your inbox.", and a filled button "Subscribe" with the add glyph

#### Scenario: Before the page knows
- **WHEN** the page is still loading the channel, or hasn't yet read whether the user is subscribed
- **THEN** it shows no card

### Requirement: Subscribe takes effect at once
Tapping "Subscribe" SHALL subscribe the user to the channel at once, with no question, and keep the channel's ID, name, description and avatar links on the phone. The card SHALL then show the subscribed state, and screen readers SHALL hear "Subscribed to" and the channel's name, once: a configuration change SHALL NOT make them hear it again.

#### Scenario: Subscribing
- **WHEN** the user taps "Subscribe" on the page of "Kettle and Crumb"
- **THEN** the card says "Subscribed", and a screen reader says "Subscribed to Kettle and Crumb"

### Requirement: A channel that moved is followed where it went
When YouTube answers for a channel under a different ID than the one opened, because it sends that channel on to another, the page SHALL show the channel that YouTube answered for, and its card SHALL cover both IDs. It SHALL say "Subscribed" when the user is subscribed under either ID, and SHALL show no card until it knows both. Subscribe SHALL subscribe to the channel that YouTube answered for. Unsubscribe SHALL remove the subscription under every ID the page knows.

#### Scenario: A channel that moved
- **WHEN** the user opens a channel whose ID YouTube sends on to another channel, and taps "Subscribe"
- **THEN** the user is subscribed to the channel that YouTube answered for

#### Scenario: A subscription under the old ID
- **WHEN** the user subscribed to a channel under an ID that YouTube now sends on to another, and opens its page
- **THEN** the card says "Subscribed", and "Unsubscribe" removes that subscription, so the card says "Not subscribed"

### Requirement: Unsubscribe asks first
Tapping "Unsubscribe" SHALL ask in a dialog: "Unsubscribe from" and the channel's name, then "Its new videos stop showing in your inbox.", with "Cancel" and "Unsubscribe". "Unsubscribe" SHALL forget the subscription, show the card's not subscribed state, and screen readers SHALL hear "Not subscribed to" and the channel's name. "Cancel", the back gesture and a tap outside the dialog SHALL close it and change nothing. The dialog SHALL stay open across a configuration change.

#### Scenario: Unsubscribing
- **WHEN** the user taps "Unsubscribe", then "Unsubscribe" in the dialog
- **THEN** the card says "Not subscribed", a screen reader says "Not subscribed to Kettle and Crumb", and the subscription is no longer kept on the phone

#### Scenario: Changing their mind
- **WHEN** the user taps "Unsubscribe", then "Cancel"
- **THEN** the dialog closes, and the card still says "Subscribed"

#### Scenario: A configuration change with the dialog open
- **WHEN** the dialog is open and the theme changes
- **THEN** the dialog is still open

### Requirement: A followed channel's page works offline
The page SHALL read whether the user is subscribed from the subscriptions kept on the phone, so its card is right after the app starts again. Subscribing and unsubscribing SHALL work without internet, once the page shows the channel. When the user follows a channel and YouTube can't answer, the page SHALL show the channel as the phone keeps it, the card, and the failure in place of the uploads.

#### Scenario: Opening the page again
- **WHEN** the user subscribed to a channel, closed the app, and opens that channel's page again
- **THEN** the card says "Subscribed"

#### Scenario: A followed channel offline
- **WHEN** the user subscribed to a channel and opens its page with no connection
- **THEN** the page shows the channel's name and the card with "Unsubscribe", and says that YouTube can't be reached in place of the uploads

#### Scenario: Unsubscribing and subscribing again offline
- **WHEN** the page of a followed channel shows with no connection, and the user unsubscribes, then taps "Subscribe"
- **THEN** the channel's name and the card stay on the page throughout, and the card says "Subscribed" again

#### Scenario: A channel not followed, offline
- **WHEN** the user opens the page of a channel they didn't subscribe to, with no connection
- **THEN** the page says that YouTube can't be reached, offers "Try again", and shows no card

### Requirement: The page lists the latest uploads
Under the card, the page SHALL list the channel's latest long-form videos and live streams that YouTube gave, never Shorts, newest first, with videos that haven't started first. Each row SHALL show what Search YouTube's rows show, without the channel's name: a thumbnail box with the length, "Live" or "Upcoming", the title, and how long ago the video was published.

#### Scenario: Uploads
- **WHEN** a channel has three videos and a live stream
- **THEN** the page lists the four rows, newest first, the live stream marked "Live", and no row shows the channel's name

#### Scenario: An upcoming premiere
- **WHEN** a channel has a premiere that starts tomorrow, and older videos
- **THEN** the premiere's row comes first, marked "Upcoming", with no time

### Requirement: Tapping an upload opens it
Tapping a row SHALL open that video's video screen, for a video, a live stream and an upcoming video alike. A second tap while it opens SHALL NOT open it twice.

#### Scenario: Opening an upload
- **WHEN** the user taps a row on a channel page
- **THEN** the video screen opens for that video, and going back shows the channel page as it was, with nothing sent to YouTube again

#### Scenario: A double tap on a row
- **WHEN** the user taps the same row twice quickly
- **THEN** one video screen opens, and one back gesture returns to the channel page

### Requirement: A channel with no videos says so
When YouTube answers with no long-form videos or live streams for the channel, the page SHALL say that the channel has no videos to show, under the card.

#### Scenario: Only Shorts
- **WHEN** a channel has only Shorts
- **THEN** the page shows the channel and the card, and says that the channel has no videos to show

### Requirement: A failure says why
When loading fails, the page SHALL show a message for its category in place of the uploads: for NETWORK that YouTube can't be reached and to check the connection, for CHANNEL_CLOSED that the channel was closed, for BLOCKED and BROKEN that YouTube isn't loading right now, and for any other category that the channel isn't available. It SHALL offer "Try again" for NETWORK, BLOCKED and BROKEN, which asks YouTube again and shows that it is loading, and no button for the others.

#### Scenario: No connection
- **WHEN** loading fails with NETWORK
- **THEN** the page says that YouTube can't be reached and to check the connection, and offers "Try again"

#### Scenario: A closed channel
- **WHEN** loading fails with CHANNEL_CLOSED
- **THEN** the page says that the channel was closed, with no "Try again"

#### Scenario: Trying again
- **WHEN** the user taps "Try again" after a NETWORK failure
- **THEN** the page shows that it is loading, and asks YouTube again

### Requirement: Going back
The back arrow and the system's back gesture SHALL return to the screen under the channel page, as that screen was.

#### Scenario: Back from a channel opened from a video
- **WHEN** the user opened a video from search results, opened its channel page, and taps the back arrow
- **THEN** Search YouTube shows the same text and results, and the video plays on in the mini player

#### Scenario: Back from a channel opened from search
- **WHEN** the user opened a channel from Search YouTube's channel results, subscribed on its page, and taps the back arrow
- **THEN** Search YouTube shows the same text and results, and that channel's row says "Subscribed"

### Requirement: The channel page keeps its state
A configuration change SHALL keep the channel, the uploads, a running load, the description's folding and an open dialog. After the system ends the app's process in the background, the page SHALL come back for the same channel and load it again.

#### Scenario: Coming back after process death
- **WHEN** the system ended the app's process while a channel page showed, and the user returns to it
- **THEN** the same channel's page shows that it is loading, then the channel

### Requirement: The channel page fits any window
On a phone the page SHALL stay in portrait. In any wider window the page SHALL keep its content in one centered column of readable width, its background SHALL fill the window, and no text or button SHALL sit under the system bars, the display cutout or the mini player.

#### Scenario: A tablet in landscape
- **WHEN** a channel page shows on a tablet in landscape
- **THEN** its content sits in one centered column no wider than the readable width, and nothing is cut off

### Requirement: The channel page keeps channels private
The channel page SHALL never log a channel's ID, name or description, or its videos' IDs or titles. It SHALL send them nowhere but YouTube, except as the player spec's "Video details stay private" allows for a video opened from the page, whose title and channel name go to Android's media session while it plays. Subscribing and unsubscribing SHALL send nothing to YouTube, because the app has no YouTube account.

#### Scenario: Logs after subscribing
- **WHEN** the user opens a channel page and subscribes
- **THEN** the app's log lines name the call, its outcome category and counts, and hold no channel ID, name, description or title

### Requirement: An open channel page saves only its channel's ID
Besides the subscriptions that "What Dewfall keeps stays on the phone" covers, the only copy of a channel that the page keeps outside the screen SHALL be the ID of an open channel page's channel, in the state Android saves for a process that it ends in the background.

#### Scenario: Process death on a channel page
- **WHEN** the system ends the app's process while a channel page is open
- **THEN** the state Android saved holds that channel's ID, and no channel name, description, avatar link or video title

### Requirement: The channel page saves the uploads of a subscribed channel
The channel page SHALL save the uploads it loaded, whole and as YouTube gave them, as "Saving a channel's uploads keeps every video" describes, while its card says "Subscribed": after the user taps "Subscribe" and the subscription is kept, and whenever the page finishes loading them. It SHALL send no request to YouTube for this beyond the page's own load. A page whose card doesn't say "Subscribed" SHALL save nothing.

#### Scenario: Subscribing from a channel page
- **WHEN** the user subscribes on the page of a channel whose uploads show, and goes back to the inbox
- **THEN** the inbox shows those of that channel's 2 latest videos that aren't marked watched, with no new request to YouTube

#### Scenario: Opening a subscribed channel's page
- **WHEN** the user subscribed to a channel last week, under the ID YouTube answers for, and opens its page today, which loads a video published yesterday
- **THEN** that video shows in the inbox

#### Scenario: A channel that moved, subscribed under its old ID
- **WHEN** the user subscribed to a channel under an ID that YouTube now sends on to another, and opens its page, which loads
- **THEN** the page saves the uploads under the ID YouTube answered for, and they show in the inbox only once the subscription is under that ID, which the manual refresh (#52) decides how to do

#### Scenario: A channel not subscribed to
- **WHEN** the user opens the page of a channel they don't subscribe to, and its uploads load
- **THEN** none of its videos is saved
