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
On a phone the page SHALL stay in portrait. In any wider window the page SHALL keep its content in one centered column of readable width, its background SHALL fill the window, and no text or button SHALL sit under the system bars, the display cutout, the mini player or the navigation bar.

#### Scenario: A tablet in landscape
- **WHEN** a channel page shows on a tablet in landscape
- **THEN** its content sits in one centered column no wider than the readable width, and nothing is cut off

#### Scenario: The navigation bar
- **WHEN** a channel page opened from the Channels screen shows and the user scrolls to its last upload
- **THEN** the last upload sits fully above the navigation bar, and above the mini player when one shows

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
- **THEN** the page saves the uploads under the ID YouTube answered for, and they show in the inbox once a check for new videos moves the subscription to that ID, as "A check follows a channel that moved" says

#### Scenario: A channel not subscribed to
- **WHEN** the user opens the page of a channel they don't subscribe to, and its uploads load
- **THEN** none of its videos is saved

### Requirement: The Channels screen lists the subscribed channels
The Channels screen SHALL list every subscribed channel in one card, ordered by name as the phone's language sorts it, ignoring case, with an accented letter next to its plain one, and channels with the same name in a fixed order. Above the card it SHALL say how many channels there are, such as "1 channel" or "4 channels". Each row SHALL show the channel's emblem, its name and a chevron.

#### Scenario: Four channels
- **WHEN** the user subscribes to "orbit Hour", "Kettle and Crumb", "Tidewater Woodshop" and "Larkspur Lectures" and opens the Channels screen
- **THEN** it says "4 channels" and lists Kettle and Crumb, Larkspur Lectures, orbit Hour and Tidewater Woodshop, in that order

#### Scenario: An accented name
- **WHEN** the phone's language is English and the user subscribes to "Zebra Hall", "Élodie Bakes" and "Delta Notes"
- **THEN** the Channels screen lists Delta Notes, Élodie Bakes and Zebra Hall, in that order

#### Scenario: One channel
- **WHEN** the user subscribes to one channel and opens the Channels screen
- **THEN** it says "1 channel" above that channel's row

### Requirement: The Channels screen's top bar
The Channels screen's top bar SHALL show "Dewfall", "Channels" under it, and a search button, which a screen reader SHALL hear as "Search YouTube". It SHALL have no back arrow and no menu button.

#### Scenario: The top bar
- **WHEN** the user opens the Channels screen
- **THEN** its top bar shows "Dewfall", "Channels" and the search button, and tapping the search button opens Search YouTube

### Requirement: A row says when the channel last uploaded
A row SHALL say when the channel last uploaded, as a time ago, such as "Last upload 7 hours ago" or "Last upload yesterday", counted as Search YouTube counts publish times. It SHALL take the newest saved video of the channel that has come out, Shorts and live streams included. A premiere or live stream that hasn't started SHALL NOT count. When no saved video of the channel counts, or its time lies ahead, the row SHALL show no such line.

#### Scenario: A video yesterday
- **WHEN** the newest saved video of a subscribed channel came out yesterday
- **THEN** its row says "Last upload yesterday"

#### Scenario: An upcoming premiere
- **WHEN** a channel's newest saved entry is a premiere that hasn't started, and its newest video before it came out 3 days ago
- **THEN** its row says "Last upload 3 days ago"

#### Scenario: Nothing saved yet
- **WHEN** the user subscribed to a channel and none of its videos is saved, such as right after an import
- **THEN** its row shows the emblem, the name and the chevron, and no last upload line

### Requirement: The Channels screen updates on its own
The Channels screen SHALL show a change to the subscriptions or the saved videos while it shows, without the user doing anything.

#### Scenario: Unsubscribing from a channel page
- **WHEN** the user opens a channel from the Channels screen, unsubscribes and goes back
- **THEN** the Channels screen no longer lists that channel, and its count is one lower

#### Scenario: A save
- **WHEN** the Channels screen shows a channel with no last upload line and that channel's uploads are saved
- **THEN** its row gains the last upload line

### Requirement: Tapping a channel opens its page
Tapping a row SHALL open that channel's page, with the Channels screen underneath, so going back shows the Channels screen as it was. Tapping a second time while the page opens SHALL NOT open it twice. A screen reader SHALL hear each row as one item, with the channel's name, its last upload line if it has one, and that tapping opens the channel.

#### Scenario: Opening a channel
- **WHEN** the user taps "Kettle and Crumb" on the Channels screen
- **THEN** the channel page of Kettle and Crumb opens, and one back gesture returns to the Channels screen at the same scroll position

#### Scenario: A double tap
- **WHEN** the user taps a row twice quickly
- **THEN** one channel page opens, and one back gesture returns to the Channels screen

### Requirement: The Channels screen says when there are no channels
When the user subscribes to no channel, the Channels screen SHALL show the channels glyph in a round disc, then say "No channels yet", then "Find channels with Search YouTube. Their new videos show in your inbox.", with a "Search YouTube" button that opens Search YouTube. It SHALL NOT suggest channels.

#### Scenario: No channels yet
- **WHEN** the user subscribes to no channel and opens the Channels screen
- **THEN** it says "No channels yet" and offers "Search YouTube", and tapping it opens Search YouTube

### Requirement: The Channels screen says when it can't be read
When the app can't read what it keeps on the phone for the Channels screen, the screen SHALL show the error glyph, "Your channels can't be shown" and "Dewfall couldn't read what it keeps on this phone. Restarting the app may help.", and SHALL NOT crash. Its search button and the navigation bar SHALL still work. When the screen shows again after its read stopped, it SHALL read again.

#### Scenario: A database that can't be read
- **WHEN** the user taps "Channels" and the screen's read fails
- **THEN** the Channels screen says "Your channels can't be shown", the app stays open, the search button opens Search YouTube, and tapping "Inbox" shows the inbox

### Requirement: The Channels screen fits any window
On a phone the Channels screen SHALL stay in portrait. In any wider window it SHALL keep its content in one centered column of readable width, and its background SHALL fill the window. Its background SHALL draw under the system bars, and no text or control SHALL sit under the system bars, the display cutout, the mini player or the navigation bar. Text SHALL wrap at large font sizes and never be cut off. It SHALL work in light, dark and dynamic color.

#### Scenario: A tablet
- **WHEN** the Channels screen shows on a tablet in landscape
- **THEN** the card sits in one centered column of readable width, and the background fills the window

#### Scenario: A large font
- **WHEN** the font size is at its largest
- **THEN** every channel name and last upload line wraps, and none is cut off

#### Scenario: The mini player
- **WHEN** a video plays in the mini player and the user scrolls the Channels screen to the end
- **THEN** the last row sits fully above the mini player

### Requirement: The Channels screen keeps its state
A configuration change SHALL keep the Channels screen's scroll position. After the system ends the app's process in the background, the Channels screen SHALL come back with what the phone keeps, and its scroll position. It SHALL keep no copy of the channels outside the screen beyond what "What Dewfall keeps stays on the phone" covers.

#### Scenario: A theme change
- **WHEN** the user scrolled down the Channels screen and the theme changes
- **THEN** it shows the same channels at the same scroll position

#### Scenario: Process death
- **WHEN** the system ends the app's process in the background while the Channels screen shows, and the user comes back
- **THEN** it shows the same channels at the same scroll position, and the state Android saved holds no channel name, and no channel ID or search text beyond the IDs of open channel pages and the text of open Search YouTube screens, in either destination

### Requirement: The Channels screen keeps channels private
The Channels screen SHALL never log a channel's ID or name, and SHALL send nothing to any server. Showing it SHALL send no request to YouTube.

#### Scenario: Opening the Channels screen offline
- **WHEN** the phone has no connection and the user opens the Channels screen
- **THEN** it lists the subscribed channels as the phone keeps them, and no request leaves the phone

### Requirement: The user picks the Takeout file
The import screen SHALL let the user pick one file with Android's file picker: either the .zip file that Google Takeout makes, or a subscriptions .csv file from it. Closing the picker without a file SHALL change nothing.

#### Scenario: A Takeout zip file
- **WHEN** the user picks a Takeout .zip file whose subscriptions file lists three channels
- **THEN** the same three channels are subscribed as when the user picks that subscriptions file itself

#### Scenario: Closing the picker
- **WHEN** the user opens the file picker and closes it without picking a file
- **THEN** no channel is subscribed, and the import screen looks as it did before

### Requirement: Importing subscribes to every channel in the file
Importing a file that holds subscriptions SHALL subscribe to every channel in it, with the channel's ID and the name the file gives, trimmed of white space at both ends, and with no avatars and no description. The time of subscribing SHALL be the time of the import. A channel that is already subscribed SHALL keep its details and its time of subscribing. A channel ID listed more than once SHALL count once, with the name from its first line.

#### Scenario: A subscriptions file
- **WHEN** the user picks a subscriptions .csv file that lists three channels
- **THEN** the three channels are subscribed, each with its ID and name from the file, no avatars, no description, and the time of the import as the time of subscribing

#### Scenario: A channel the user already follows
- **WHEN** the user subscribed to a channel last week, and imports a file that lists it under another name
- **THEN** the channel keeps last week's time of subscribing and the name it had

#### Scenario: Importing the same file again
- **WHEN** the user imports a file, then imports the same file again
- **THEN** the second import subscribes to nothing new, and every channel keeps its first time of subscribing

#### Scenario: A channel listed twice
- **WHEN** a file lists the same channel ID on two lines, with different names
- **THEN** the channel is subscribed once, with the name from the first of those lines

### Requirement: The import doesn't rely on Takeout's names
Takeout translates the names of its folders, files and columns into the account's language, so the import SHALL NOT rely on them. A subscriptions table SHALL be a table of comma-separated values whose lines hold three fields: a channel ID, the channel's link and the channel's name.

#### Scenario: A file in another language
- **WHEN** the user picks a .zip file whose subscriptions file, its folder and its header have names in another language
- **THEN** its channels are subscribed as they would be with English names

### Requirement: In a zip file, the table with the most channels counts
In a .zip file, the import SHALL read every file whose name ends in .csv, and use the one that holds the most channels, the first of them in the .zip file's order on a tie. It SHALL NOT import a table whose lines hold more or fewer than three fields, such as the table of the user's own channel.

#### Scenario: Other tables before the subscriptions
- **WHEN** a .zip file holds, before its subscriptions file, a .csv file with the user's own channel ID in a table with more than three fields, and a .csv file with no channel IDs
- **THEN** only the channels in the subscriptions file are subscribed, and the lines of the other files aren't counted

#### Scenario: A smaller table with channel IDs
- **WHEN** a .zip file holds, before its subscriptions file with ten channels, another three-field table with two channel IDs
- **THEN** only the ten channels of the subscriptions file are subscribed

### Requirement: The import reads comma-separated values
The import SHALL read fields as comma-separated values do: a field in double quotes may hold commas, line breaks and doubled double quotes, which stand for one double quote. It SHALL accept lines that end in a line feed or in a carriage return and a line feed, and a file that starts with a UTF-8 byte order mark.

#### Scenario: A name with a comma and quotes
- **WHEN** a channel's name in the file is in double quotes and holds a comma and a doubled double quote
- **THEN** the channel's name holds the comma and one double quote, and no line is counted as unreadable

#### Scenario: Line endings and a byte order mark
- **WHEN** a file starts with a UTF-8 byte order mark and its lines end in a carriage return and a line feed
- **THEN** its channels are subscribed as they would be without them

### Requirement: The import skips lines it can't read
A line that doesn't hold exactly three fields, whose first field isn't a channel ID, or whose name is empty after trimming white space, SHALL be skipped and counted as a line the import couldn't read. A blank line SHALL be skipped and not counted. The first line that isn't blank SHALL count as the header, neither imported nor counted, unless its first field is a channel ID. Skipped lines SHALL NOT stop the import of the other lines.

#### Scenario: Blank lines at the end
- **WHEN** a file ends with several blank lines
- **THEN** the blank lines aren't counted as lines it couldn't read

#### Scenario: Lines that aren't channels
- **WHEN** a file holds ten channels, a line whose first field is a handle such as `@name`, a line with two fields, and a line whose name is empty
- **THEN** the ten channels are subscribed, and three lines are counted as lines it couldn't read

#### Scenario: A file with no header
- **WHEN** a subscriptions file's first line is already a channel
- **THEN** that channel is subscribed with the others

### Requirement: The import reports what it did
After an import, the import screen SHALL show how many channels the file holds, each counted once, and list them in the file's order with their names. It SHALL say how many of them were newly subscribed, how many were already subscribed, and how many lines it couldn't read, leaving out each count that is zero. While the file is being read, the screen SHALL show that it is reading and SHALL NOT offer to choose another file.

#### Scenario: A first import
- **WHEN** the user imports a file with 40 channels into a Dewfall with none
- **THEN** the screen says 40 channels were found, lists the 40, and says all 40 are new

#### Scenario: Some already subscribed and some lines unreadable
- **WHEN** the user imports a file with 40 channels, of which 5 are already subscribed, and 2 lines it couldn't read
- **THEN** the screen says 40 channels were found, 35 are new, 5 were already subscribed, and 2 lines couldn't be read

#### Scenario: While reading
- **WHEN** the user picked a file and the import hasn't finished
- **THEN** the screen shows that it is reading the file, and has no button to choose a file

### Requirement: A failed import changes nothing
The import SHALL subscribe to nothing, and the screen SHALL say why and offer to choose another file, when the file holds no subscriptions table with at least one channel, when it can't be opened or read, such as a damaged .zip file, when it is a .csv file larger than 8 MiB, far more than any list of subscriptions, or when it is a .zip file larger than 256 MiB. In a .zip file, a .csv file larger than 8 MiB SHALL be passed over as not the subscriptions table.

#### Scenario: A file with no channels
- **WHEN** the user picks a .zip file from Takeout that holds no subscriptions, or a file that isn't from Takeout
- **THEN** no channel is subscribed, and the screen says the file holds no channels and suggests the .zip file from Takeout or the subscriptions file in it

#### Scenario: A damaged file
- **WHEN** the user picks a .zip file that ends before its first file does
- **THEN** no channel is subscribed, and the screen says Dewfall couldn't read the file

#### Scenario: A file that is too large
- **WHEN** the user picks a .csv file larger than 8 MiB
- **THEN** no channel is subscribed, and the screen says the file is too large and to export only the subscriptions from Takeout

#### Scenario: A zip file that is too large
- **WHEN** the user picks a .zip file larger than 256 MiB, such as a Takeout export with videos
- **THEN** no channel is subscribed, and the screen says the file is too large and to export only the subscriptions from Takeout

#### Scenario: A zip file with a name that isn't UTF-8
- **WHEN** the user picks a .zip file in which a file's name isn't valid UTF-8
- **THEN** no channel is subscribed, and the screen says Dewfall couldn't read the file

#### Scenario: Choosing another file
- **WHEN** an import failed and the user chooses another file that holds channels
- **THEN** that file's channels are subscribed and reported

### Requirement: An import subscribes to all of its channels or none
An import SHALL subscribe to every channel it found in one step, so a list of subscriptions never holds only part of a file. Leaving the import screen while the file is still being read SHALL subscribe to nothing. Once subscribing has started, it SHALL finish, even if the user leaves the screen.

#### Scenario: Leaving while reading
- **WHEN** the user leaves the import screen before the file has been read
- **THEN** no channel from the file is subscribed

#### Scenario: Leaving while subscribing
- **WHEN** the user leaves the import screen after the file was read, while its channels are being subscribed
- **THEN** every channel the file holds is subscribed

### Requirement: The Takeout file stays on the phone
The import SHALL read the picked file once, when the user picks it, and SHALL keep no access to it afterwards and no copy of it. It SHALL keep only the channels' IDs and names, as subscriptions. It SHALL NOT log the file's name, link or contents, or the channels in it, and SHALL send nothing anywhere: an import makes no network request.

#### Scenario: Importing offline
- **WHEN** the phone has no internet and the user imports a file
- **THEN** the import works as it does online

#### Scenario: What an import logs
- **WHEN** an import succeeds or fails
- **THEN** the app's log holds no channel ID, channel name, file name or file link from it

### Requirement: The empty Channels screen offers the import
When the Channels screen says "No channels yet", it SHALL show an "Import from Google Takeout" button under "Search YouTube", which opens the Takeout import screen. The import's back button, the back gesture and Done SHALL return to the Channels screen.

#### Scenario: Opening the import from the Channels screen
- **WHEN** the user subscribes to no channel, opens the Channels screen and taps "Import from Google Takeout"
- **THEN** the Takeout import screen opens, without the navigation bar

#### Scenario: Back to the Channels screen
- **WHEN** the user opened the import from the Channels screen and taps the back button
- **THEN** the Channels screen shows again, with "Channels" selected in the navigation bar

### Requirement: A check for new videos saves every subscribed channel's uploads
A check SHALL ask YouTube for the latest uploads of each channel the user subscribed to when it started, one channel after another, and save each answer as "Saving a channel's uploads keeps every video" and "Saving a channel's uploads refreshes its details" say. It SHALL run apart from any screen, so leaving the inbox doesn't stop it, and only one SHALL run at a time.

#### Scenario: Two channels
- **WHEN** the user subscribes to two channels and a check runs
- **THEN** both channels' latest uploads are saved, and the inbox shows their new videos as the inbox spec says

#### Scenario: Leaving the inbox
- **WHEN** a check runs and the user opens Search YouTube
- **THEN** the check goes on and saves every channel's uploads

#### Scenario: Asked twice
- **WHEN** a check runs and another is asked for
- **THEN** no second check starts, and each channel is asked for once

#### Scenario: What the log keeps
- **WHEN** a check runs
- **THEN** the app's log lines name the check, its outcome and its counts of channels, and hold no channel ID, name, video ID or title

### Requirement: A check stops when YouTube can't be reached
When YouTube can't be reached, or refuses to answer, a check SHALL stop at once and send no more requests. The uploads it saved before SHALL stay saved, and the inbox SHALL say why, as its spec says.

#### Scenario: The connection drops
- **WHEN** a check has saved 3 of 10 channels, the connection drops, and the request for the fourth fails
- **THEN** the check stops, the 3 channels' uploads stay saved, and no request is sent for the other 6

#### Scenario: YouTube refuses
- **WHEN** YouTube refuses a check's request, such as by asking for proof that the user isn't a bot
- **THEN** the check stops and sends no more requests

### Requirement: A check skips a channel that fails on its own
When YouTube closed a channel, can't show it, or sends an answer for it that the app can't read, a check SHALL skip that channel, keep what was saved of it, and go on with the next. After 3 answers in a row that the app can't read, or when no channel's answer could be read and at least one couldn't, the check SHALL stop and count as stopped because YouTube isn't loading.

#### Scenario: A closed channel
- **WHEN** YouTube closed one of 3 subscribed channels and a check runs
- **THEN** the other 2 channels' uploads are saved, and the closed channel stays subscribed with its saved videos

#### Scenario: No answer can be read
- **WHEN** YouTube changed its pages so that no channel's answer can be read, and the user subscribes to 10 channels
- **THEN** the check stops after the third channel, sends no request for the other 7, and ends as stopped because YouTube isn't loading

#### Scenario: One odd channel
- **WHEN** the app can't read the answer for one of 3 subscribed channels, and reads the other 2
- **THEN** the other 2 channels' uploads are saved, and the check ends with no failure

### Requirement: A check follows a channel that moved
When YouTube answers for a subscribed channel with another channel's ID, a check SHALL move the subscription to that ID, keeping the time the user subscribed, and save the uploads under it. If the user already subscribes under the new ID, the old subscription SHALL go and the new one SHALL keep its own time. Videos saved under the old ID SHALL move to the new ID with the subscription, and what the user watched of them SHALL stay.

#### Scenario: A moved channel
- **WHEN** the user subscribed last month under an ID that YouTube now sends on to another, and a check runs
- **THEN** the subscription is under the new ID with last month's time of subscribing, and the channel's new videos show in the inbox

#### Scenario: Subscribed under both IDs
- **WHEN** the user subscribes under a moved channel's old ID and under its new one, and a check runs
- **THEN** only the subscription under the new ID stays, with its own time of subscribing, and the old ID's videos are under the new ID, where the inbox judges them by that time

#### Scenario: A started video under the old ID
- **WHEN** the user half watched a video saved under a moved channel's old ID, isn't subscribed under the new ID, YouTube's latest uploads under the new ID no longer list it, and a check runs
- **THEN** the video is under the new ID and still in the inbox, at the same position

### Requirement: A check survives a failure on the phone
When the phone can't read the subscriptions or save what a check fetched, such as on a full disk, the check SHALL stop, send no more requests, and end without crashing the app. The inbox SHALL show no banner for it, and a new check SHALL be possible at once.

#### Scenario: A full disk
- **WHEN** a check runs and saving a channel's uploads fails because the disk is full
- **THEN** the app stays open, the check stops, the line reads "Inbox" with no banner, and "Check for new videos" can be tapped again

### Requirement: A check keeps the user's choices
A check SHALL NOT subscribe to a channel. A channel the user unsubscribes from while a check runs SHALL stay unsubscribed, also when YouTube answers for it under another ID.

#### Scenario: Unsubscribing during a check
- **WHEN** a check runs and the user unsubscribes from a moved channel that the check hasn't reached yet
- **THEN** after the check, neither the old ID nor the new one is subscribed
