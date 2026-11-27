# inbox Specification

## Purpose
The inbox is Dewfall's home screen: a calm list of the new videos from the channels the user subscribes to, which the user can finish. It shows only what the user chose, with no suggestions and no Shorts.

## Requirements

### Requirement: The app opens on the inbox
The app SHALL open on the inbox. Its top bar SHALL show "Dewfall", "Inbox" under it, or "Checking for new videos" while a check runs, and a search button, which a screen reader SHALL hear as "Search YouTube". The inbox SHALL have no back arrow, because no screen comes before it, and the back gesture on it SHALL leave the app as Android does.

#### Scenario: Opening the app
- **WHEN** the user opens the app
- **THEN** the inbox shows "Dewfall", "Inbox" and the search button

#### Scenario: Opening Search YouTube
- **WHEN** the user taps the search button
- **THEN** Search YouTube opens over the inbox

### Requirement: The inbox shows only unwatched videos of subscribed channels
The inbox SHALL show only saved videos whose channel the user subscribes to, that can be watched from start to end, so no Short, live stream on air or premiere that hasn't started, and that aren't marked watched. Among those, it SHALL show the ones that are new, as the next two requirements say, and the ones the user started, as "A video started since subscribing stays" says.

#### Scenario: Live streams, premieres and Shorts
- **WHEN** a subscribed channel's saved uploads hold a live stream on air, a premiere that hasn't started and a video, all published after subscribing
- **THEN** the inbox shows only the video, and shows the stream or the premiere once a later save holds it as a video that can be watched from start to end

#### Scenario: Marked watched
- **WHEN** the inbox shows a video and the user marks it watched
- **THEN** the video leaves the inbox, and no older video of the channel takes its place

#### Scenario: Unsubscribing
- **WHEN** the user unsubscribes from a channel whose videos are in the inbox
- **THEN** its videos leave the inbox, and come back with the same rules if the user subscribes again

#### Scenario: A channel never subscribed to
- **WHEN** Dewfall keeps videos of a channel the user doesn't subscribe to
- **THEN** none of them is in the inbox

### Requirement: A video started since subscribing stays
The inbox SHALL also show every unwatched video of a subscribed channel that the user started, resumed or marked unwatched since subscribing, which has a saved position past its start, even when it no longer counts as new, until it is marked watched. A video the user started only before subscribing, such as from Search YouTube, SHALL be in the inbox only if it is new.

#### Scenario: Partly watched
- **WHEN** the user watched half of a video in the inbox and left it
- **THEN** the video is still in the inbox

#### Scenario: An old video started after subscribing
- **WHEN** after subscribing, the user opens an old video from the channel's page, watches a few minutes and leaves it
- **THEN** the video is in the inbox until it is marked watched

#### Scenario: Marked unwatched after subscribing
- **WHEN** after subscribing, the user marks unwatched an old video of the channel that they had half watched before
- **THEN** the video is in the inbox

#### Scenario: Started before subscribing
- **WHEN** the user half watched 5 videos of a channel from Search YouTube, then subscribed to it, and none of them is among its 2 latest
- **THEN** none of the 5 is in the inbox

#### Scenario: A started video that is no longer new
- **WHEN** after subscribing, the user watched half of one of the channel's 2 latest videos from before subscribing, and a later save brings 2 newer videos
- **THEN** the started video stays in the inbox with the 2 new ones, and the other older video leaves

### Requirement: A channel's 2 latest videos are new
A video SHALL count as new when it is one of its channel's 2 latest saved videos that can be watched from start to end, watched or not, in the order the subscriptions spec's "Saved videos keep YouTube's order" gives. So a new subscription starts with up to 2 videos, and a newer video pushes the older of them out.

#### Scenario: A new subscription
- **WHEN** the user subscribes to a channel, and its saved uploads hold 30 videos published before that
- **THEN** the inbox shows the channel's 2 latest videos, and none of the other 28

#### Scenario: An older video gives way
- **WHEN** the inbox shows a channel's 2 latest videos from before the user subscribed, and a later save holds 2 videos published after subscribing
- **THEN** the inbox shows the 2 new videos, and the older 2 no longer show, unless the user started one of them

### Requirement: A video that came out after subscribing is new
A video SHALL count as new when its publish time is at or after the time the user subscribed to its channel, or when a save of the channel's uploads first saved it later than the first video such a save saved for that channel since the user subscribed. So the videos of the first save since subscribing that brings any new to Dewfall are new only by the publish time or as the 2 latest, and a video that a later save of uploads brings for the first time is new whatever its publish time.

#### Scenario: A video that came out later
- **WHEN** the user subscribed to a channel last week, and a later save holds a video published yesterday
- **THEN** the inbox shows that video along with the channel's earlier ones that it shows

#### Scenario: A video kept another way
- **WHEN** after subscribing, Dewfall keeps an older video of the channel another way than a save of its uploads, after the first such save
- **THEN** only a save of uploads counts for the first-save test, so the video is in the inbox only if it is new by its publish time or as one of the 2 latest

#### Scenario: Subscribing again
- **WHEN** the user subscribes to a channel again, the first save after that brings no video Dewfall didn't already keep, and the next save brings 3 videos published before subscribing again
- **THEN** the inbox shows the channel's 2 latest videos, not the third of those 3

#### Scenario: An estimated time from before subscribing
- **WHEN** a video came out after the user subscribed, YouTube gave only an estimate such as "2 days ago", the estimate falls before the time of subscribing, and a save after the one that first brought the channel's videos since subscribing first saved it
- **THEN** the inbox shows it

### Requirement: The inbox updates on its own
The inbox SHALL show the change at once when a save of uploads, a subscription, an unsubscription or a watched mark changes what it holds, with no action from the user.

#### Scenario: A change while the inbox shows
- **WHEN** the inbox shows, and a video it shows gets marked watched, or a save brings a new video of a subscribed channel
- **THEN** the inbox drops the watched video, or shows the new one, without the user doing anything

### Requirement: Videos are grouped by channel
The inbox SHALL show one card for each channel that has videos in it. A card SHALL show the channel's emblem with its initial or its picture, its name, and "1 video" or the number of its videos, such as "3 videos", then its videos newest first, in the order the subscriptions spec's "Saved videos keep YouTube's order" gives. Each video row SHALL show what the channel page's rows show: a thumbnail box or its image, with the length, the title, and how long ago the video was published.

#### Scenario: A card
- **WHEN** a channel has new videos published 2 hours and 3 days ago
- **THEN** its card says "2 videos" and lists both, the newer first, without the channel's name on the rows

### Requirement: The newest card comes first
The card with the newest video SHALL come first, and the others SHALL follow in the order of their newest videos. Below the last card, the inbox SHALL say "That's everything for now."

#### Scenario: Two channels
- **WHEN** channel A has a new video published 2 hours ago, and channel B has one published yesterday
- **THEN** A's card comes first, then B's card, which says "1 video"

#### Scenario: The end of the list
- **WHEN** the inbox shows videos and the user scrolls to the end
- **THEN** "That's everything for now." shows below the last card

### Requirement: The inbox's taps open the video or the channel
Tapping a video row SHALL open that video's video screen, with the inbox underneath, so going back shows the inbox as it was. Tapping a card's channel row SHALL open that channel's page. Tapping a second time while a screen opens SHALL NOT open it twice.

#### Scenario: Opening a video
- **WHEN** the user taps a video in the inbox
- **THEN** its video screen opens, and one back gesture returns to the inbox at the same scroll position

#### Scenario: Opening a channel
- **WHEN** the user taps the channel row of a card
- **THEN** that channel's page opens

### Requirement: The inbox says when it is empty
When the user subscribes to no channel, the inbox SHALL say "No channels yet", then "Find channels with Search YouTube. Their new videos show here.", with a "Search YouTube" button that opens Search YouTube. When the user subscribes to at least one channel and no video is in the inbox, it SHALL say "You're all caught up", then "Nothing new from your channel." for one channel, or the number of channels, such as "Nothing new from your 4 channels." Neither state SHALL suggest videos or channels.

#### Scenario: No channels yet
- **WHEN** the user subscribes to no channel
- **THEN** the inbox says "No channels yet" and offers "Search YouTube", and tapping it opens Search YouTube

#### Scenario: All caught up
- **WHEN** the user subscribes to 4 channels and has marked every video in the inbox watched
- **THEN** the inbox says "You're all caught up" and "Nothing new from your 4 channels."

#### Scenario: Subscribed, nothing saved yet
- **WHEN** the user subscribes to channels whose uploads were never saved, such as after an import
- **THEN** the inbox says "You're all caught up" with the number of channels

### Requirement: The inbox says when it can't be read
When the app can't read what it keeps on the phone for the inbox, the inbox SHALL show the error glyph, "Your inbox can't be shown" and "Dewfall couldn't read what it keeps on this phone. Restarting the app may help.", and SHALL NOT crash. Its search button SHALL still work. When the inbox shows again after its reads stopped, it SHALL read again.

#### Scenario: A database that can't be read
- **WHEN** the user opens the app and the inbox's read fails
- **THEN** the inbox says "Your inbox can't be shown", the app stays open, and the search button opens Search YouTube

### Requirement: The inbox fits any window
On a phone the inbox SHALL stay in portrait. In any window wider than a phone in portrait, such as a tablet, an unfolded foldable, landscape on a large screen or split screen, it SHALL keep its content in one column of readable width, centered, and its background SHALL fill the window. No text or control SHALL sit under the navigation bar.

#### Scenario: A tablet
- **WHEN** the inbox shows on a tablet in landscape
- **THEN** the cards sit in one centered column of readable width, and the background fills the window

#### Scenario: The navigation bar
- **WHEN** the user scrolls the inbox to the end
- **THEN** "That's everything for now." sits fully above the navigation bar

### Requirement: The inbox draws edge to edge and reads at any size
The inbox's background SHALL draw under the system bars, and no text or control SHALL sit under the system bars, the display cutout or the mini player. Text SHALL wrap at large font sizes and never be cut off. The inbox SHALL work in light, dark and dynamic color.

#### Scenario: A large font
- **WHEN** the font size is at its largest
- **THEN** every title, channel name and message wraps, and none is cut off

#### Scenario: The mini player
- **WHEN** a video plays in the mini player and the user scrolls the inbox to the end
- **THEN** "That's everything for now." sits fully above the mini player

### Requirement: The inbox keeps its state
A configuration change, such as a theme change, a fold or a resized window, SHALL keep the inbox's scroll position. After the system ends the app's process in the background, the inbox SHALL come back with what the phone keeps, and its scroll position. The inbox SHALL keep no copy of the videos or channels outside the screen beyond what the subscriptions spec's "What Dewfall keeps stays on the phone" covers.

#### Scenario: A theme change
- **WHEN** the user scrolled down the inbox and the theme changes
- **THEN** the inbox shows the same videos at the same scroll position

#### Scenario: Process death
- **WHEN** the system ends the app's process in the background while the inbox shows, and the user comes back
- **THEN** the inbox shows the same videos, and the state Android saved holds no video title or channel name, and no video ID, channel ID or search text beyond those of open video screens, open channel pages and open Search YouTube screens, in any destination, as the player, subscriptions and youtube-search specs allow

#### Scenario: Process death on a page opened from the inbox
- **WHEN** the system ends the app's process while a video screen or channel page opened from the inbox shows
- **THEN** the state Android saved holds that page's own video or channel ID, as the player spec and the subscriptions spec allow, nothing from the inbox, and from the Channels and Downloads destinations only the IDs of their open channel pages and the text of their open Search YouTube screens. Those destinations hold no video screen then, because the navigation bar hides on the video screen, so a video screen is only ever the last screen of the destination that shows

### Requirement: The inbox keeps videos private
The inbox SHALL never log a video's ID or title, or a channel's ID or name, and SHALL send nothing to any server, apart from the requests of a check for new videos that the user started, and of a download that the user started, which go only to YouTube, through the YouTube layer. Showing the inbox SHALL send no request to YouTube, apart from going on with a download the user started that Android couldn't schedule while the app didn't show, as the downloads spec says.

#### Scenario: Opening the inbox offline
- **WHEN** the phone has no connection and the user opens the app
- **THEN** the inbox shows the new videos as the phone keeps them, and no request leaves the phone

#### Scenario: Checking for new videos
- **WHEN** the user taps "Check for new videos"
- **THEN** the check's requests go to YouTube, for the subscribed channels' uploads, no request leaves the phone for anywhere but YouTube, and the app's log holds no channel or video ID, name or title

#### Scenario: Downloading a video
- **WHEN** the user taps a row's download button
- **THEN** the only requests that leave the phone go to YouTube, for that video's stream links and files, and the app's log holds no video ID or title

### Requirement: A navigation bar switches between the inbox, the channels and the downloads
The app SHALL show a navigation bar at the bottom with three destinations, "Inbox", "Channels" and "Downloads", in that order, each with its icon and label, and no count badges. The destination that shows SHALL be marked as selected, by a filled icon and an indicator behind it, and a screen reader SHALL hear it as selected. Tapping another destination SHALL show it.

#### Scenario: Opening Channels
- **WHEN** the inbox shows and the user taps "Channels" in the navigation bar
- **THEN** the Channels screen shows, and "Channels" is selected

#### Scenario: Opening Downloads
- **WHEN** the Channels screen shows and the user taps "Downloads" in the navigation bar
- **THEN** the Downloads screen shows, and "Downloads" is selected

#### Scenario: Opening the app
- **WHEN** the user opens the app
- **THEN** the inbox shows, and "Inbox" is selected

### Requirement: The navigation bar shows only where it leads
The navigation bar SHALL show on the inbox, the Channels screen, the Downloads screen and a channel page. It SHALL hide on Search YouTube and the video screen. While a channel page shows, the destination it was opened from SHALL be selected.

#### Scenario: A channel page from the Channels screen
- **WHEN** the user opens a channel from the Channels screen
- **THEN** the navigation bar shows under the channel page, with "Channels" selected

#### Scenario: A channel page from the Downloads screen
- **WHEN** the user opens a video from the Downloads screen, then its channel
- **THEN** the navigation bar shows under the channel page, with "Downloads" selected

#### Scenario: Search YouTube
- **WHEN** the user opens Search YouTube from the inbox
- **THEN** no navigation bar shows

### Requirement: Each destination keeps its own history
Each destination SHALL keep the screens opened from it and their state, such as a channel page opened from the Channels screen, while another destination shows, and across configuration changes and process death. Showing a destination again SHALL show its last screen as it was.

#### Scenario: Switching and coming back
- **WHEN** the user opens a channel from the Channels screen, taps "Inbox", then taps "Channels"
- **THEN** the same channel page shows, and one back gesture returns to the Channels screen

#### Scenario: Search YouTube in both destinations
- **WHEN** the user searches "bread" from the inbox and opens a video, then its channel, taps "Channels", searches "tea" there and opens a video, then its channel, taps "Inbox" and makes the back gesture
- **THEN** Search YouTube shows "bread" and its results; after one more back gesture to the inbox, tapping "Channels" and making the back gesture, Search YouTube shows "tea" and its results

#### Scenario: Three destinations
- **WHEN** the user opens a channel from the Channels screen, taps "Downloads", opens a video's channel from there, then taps "Channels"
- **THEN** the channel page opened from the Channels screen shows; tapping "Downloads" then shows the channel page opened from the Downloads screen

#### Scenario: Process death
- **WHEN** the system ends the app's process in the background while a channel page opened from the Channels screen shows, and the user comes back
- **THEN** that channel page shows with "Channels" selected, and one back gesture returns to the Channels screen

### Requirement: The user leaves the app through the inbox
The back gesture on the Channels screen or the Downloads screen SHALL show the Inbox destination as it was, its last screen included, with "Inbox" selected. The back gesture on the inbox SHALL leave the app as Android does.

#### Scenario: Back from the Channels screen
- **WHEN** the Channels screen shows and the user makes the back gesture
- **THEN** the Inbox destination shows as it was, and when that is the inbox itself, a second back gesture leaves the app

#### Scenario: Back from the Downloads screen
- **WHEN** the user taps "Channels", then "Downloads", and makes the back gesture on the Downloads screen
- **THEN** the Inbox destination shows as it was, with "Inbox" selected

### Requirement: Tapping the selected destination returns to its first screen
Tapping the destination that is already selected SHALL close the screens opened from it and show its first screen, the inbox, the Channels screen or the Downloads screen. On its first screen, tapping it SHALL change nothing.

#### Scenario: Reselecting Channels
- **WHEN** a channel page opened from the Channels screen shows and the user taps "Channels"
- **THEN** the Channels screen shows

#### Scenario: Reselecting Downloads
- **WHEN** a channel page opened from the Downloads screen shows and the user taps "Downloads"
- **THEN** the Downloads screen shows

### Requirement: The mini player sits above the navigation bar
While both show, the mini player SHALL sit directly above the navigation bar, and no screen's text or control SHALL sit under either. Tapping a destination SHALL leave playback as it is.

#### Scenario: Switching while a video plays
- **WHEN** a video plays in the mini player and the user taps "Channels"
- **THEN** the video plays on in the mini player, above the navigation bar, over the Channels screen

### Requirement: The empty inbox offers the import
When the inbox says "No channels yet", it SHALL show an "Import from Google Takeout" button under "Search YouTube", which opens the Takeout import screen. The "You're all caught up" state SHALL NOT show it.

#### Scenario: Opening the import
- **WHEN** the user subscribes to no channel and taps "Import from Google Takeout" on the inbox
- **THEN** the Takeout import screen opens

#### Scenario: Subscribed to a channel
- **WHEN** the user subscribes to one channel and the inbox says "You're all caught up"
- **THEN** the inbox shows no "Import from Google Takeout" button

### Requirement: The import returns to where it opened from
The Takeout import screen SHALL have a back button that returns to where it opened from, and after an import, a Done button that does the same. The system back gesture SHALL do the same as the back button.

#### Scenario: Going back without importing
- **WHEN** the user opened the import from the inbox and taps the back button before choosing a file
- **THEN** the inbox shows again, still saying "No channels yet"

#### Scenario: After the import
- **WHEN** an import of 12 channels finished, and the user taps Done
- **THEN** the inbox shows again, and says "You're all caught up" and "Nothing new from your 12 channels."

### Requirement: The inbox has a button that checks for new videos
While the user subscribes to at least one channel and the inbox could be read, its top bar SHALL show a button after the search button, which a screen reader SHALL hear as "Check for new videos". A tap SHALL start a check for new videos, as the subscriptions spec's "A check for new videos saves every subscribed channel's uploads" says. Nothing else SHALL start one: not opening the app, and not pulling the list.

#### Scenario: Checking from the inbox
- **WHEN** the user subscribes to a channel whose uploads were never saved, and taps "Check for new videos"
- **THEN** the inbox shows that channel's new videos once the check has saved them, without the user doing anything else

#### Scenario: No channels yet
- **WHEN** the user subscribes to no channel
- **THEN** the top bar has no "Check for new videos" button

#### Scenario: An inbox that can't be read
- **WHEN** the inbox says "Your inbox can't be shown"
- **THEN** the top bar has no "Check for new videos" button, and the search button still works

#### Scenario: Opening the app sends nothing
- **WHEN** the user opens the app and doesn't tap "Check for new videos"
- **THEN** no request leaves the phone

### Requirement: The inbox shows a running check
While a check runs, the line under "Dewfall" SHALL read "Checking for new videos", the button's glyph SHALL turn slowly, and the button SHALL NOT start another check. When the phone's animations are off, the glyph SHALL stay still and the words SHALL still change. The inbox SHALL show a check that is still running whenever it shows again, such as after the user opened a video.

#### Scenario: A tap while checking
- **WHEN** a check runs and the user taps the button again
- **THEN** no second check starts, and the line still reads "Checking for new videos"

#### Scenario: Coming back during a check
- **WHEN** the user starts a check, opens a video while it runs, and goes back to the inbox
- **THEN** the inbox reads "Checking for new videos" until the check ends, then "Inbox"

#### Scenario: Animations off
- **WHEN** the phone's animations are off and a check runs
- **THEN** the glyph doesn't turn, and the line reads "Checking for new videos"

### Requirement: The inbox says when a check can't reach YouTube
When the last check stopped because YouTube couldn't be reached, the inbox SHALL show a banner above its cards with the wifi_off glyph and "Couldn't check for new videos. YouTube can't be reached. Check your connection." When it stopped because YouTube isn't loading, the banner SHALL show the error glyph and "Couldn't check for new videos because YouTube isn't loading." Both SHALL offer "Try again", which starts a check. The banner SHALL go when the next check starts.

#### Scenario: No connection
- **WHEN** the phone has no connection and the user taps "Check for new videos"
- **THEN** the inbox still shows the videos it had, with the banner "Couldn't check for new videos. YouTube can't be reached. Check your connection." and "Try again"

#### Scenario: YouTube isn't loading
- **WHEN** YouTube refuses the check's requests
- **THEN** the inbox shows the banner "Couldn't check for new videos because YouTube isn't loading." and "Try again"

#### Scenario: Trying again
- **WHEN** the banner shows and the user taps "Try again"
- **THEN** the banner goes, and the line reads "Checking for new videos"

#### Scenario: All caught up
- **WHEN** the inbox says "You're all caught up" and a check can't reach YouTube
- **THEN** the banner shows above "You're all caught up"

### Requirement: The inbox says nothing about a channel that fails on its own
When a check skips a channel that YouTube closed or can't show, as the subscriptions spec says, and the check goes on, the inbox SHALL show no banner or message about it. When a check ends with no new video, the inbox SHALL say nothing more than it did before the check.

#### Scenario: One closed channel
- **WHEN** the user subscribes to 3 channels, YouTube closed one of them, and the user taps "Check for new videos"
- **THEN** the check ends with no banner, and the other two channels' new videos show

#### Scenario: Nothing new
- **WHEN** a check finds no new video
- **THEN** the line reads "Inbox" again and the inbox shows what it showed before

### Requirement: A check leaves no trace after the app's process ends
A check SHALL keep its state only while the app's process lives. After the system ends the process, the inbox SHALL show no running check and no banner, and SHALL keep no copy of the check's state in what Android saves for the screen.

#### Scenario: Process death during a check
- **WHEN** a check runs, and the system ends the app's process in the background
- **THEN** the user comes back to an inbox that reads "Inbox", with no banner, and the videos the check saved before the process ended

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
