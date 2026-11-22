## ADDED Requirements

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
