# Spec Delta

## Purpose

The inbox is Dewfall's home screen: a calm list of the new videos from the channels the user subscribes to, which the user can finish. It shows only what the user chose, with no suggestions and no Shorts.

## ADDED Requirements

### Requirement: The app opens on the inbox
The app SHALL open on the inbox. Its top bar SHALL show "Dewfall", "Inbox" under it, and a search button, which a screen reader SHALL hear as "Search YouTube". The inbox SHALL have no back arrow, because no screen comes before it, and the back gesture on it SHALL leave the app as Android does.

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
- **THEN** the first save brought no video Dewfall didn't already keep, so those 3 are the first videos saved since subscribing again, and the inbox shows the 2 latest of the channel's videos, not the third of those 3

#### Scenario: An estimated time from before subscribing
- **WHEN** a video came out after the user subscribed, YouTube gave only an estimate such as "2 days ago", the estimate falls before the time of subscribing, and a save after the one that first brought the channel's videos since subscribing first saved it
- **THEN** the inbox shows it

### Requirement: The inbox updates on its own
The inbox SHALL show the change at once when a save of uploads, a subscription, an unsubscription or a watched mark changes what it holds, with no action from the user.

#### Scenario: A change while the inbox shows
- **WHEN** the inbox shows, and a video it shows gets marked watched, or a save brings a new video of a subscribed channel
- **THEN** the inbox drops the watched video, or shows the new one, without the user doing anything

### Requirement: Videos are grouped by channel
The inbox SHALL show one card for each channel that has videos in it. A card SHALL show the channel's emblem with its initial, its name, and "1 video" or the number of its videos, such as "3 videos", then its videos newest first, in the order the subscriptions spec's "Saved videos keep YouTube's order" gives. Each video row SHALL show what the channel page's rows show: a thumbnail box with the length, the title, and how long ago the video was published.

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
On a phone the inbox SHALL stay in portrait. In any window wider than a phone in portrait, such as a tablet, an unfolded foldable, landscape on a large screen or split screen, it SHALL keep its content in one column of readable width, centered, and its background SHALL fill the window.

#### Scenario: A tablet
- **WHEN** the inbox shows on a tablet in landscape
- **THEN** the cards sit in one centered column of readable width, and the background fills the window

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
- **THEN** the inbox shows the same videos, and the state Android saved holds no video title, channel name or ID

#### Scenario: Process death on a page opened from the inbox
- **WHEN** the system ends the app's process while a video screen or channel page opened from the inbox shows
- **THEN** the state Android saved holds that page's own video or channel ID, as the player spec and the subscriptions spec allow, and nothing from the inbox

### Requirement: The inbox keeps videos private
The inbox SHALL never log a video's ID or title, or a channel's ID or name, and SHALL send nothing to any server. Showing the inbox SHALL send no request to YouTube.

#### Scenario: Opening the inbox offline
- **WHEN** the phone has no connection and the user opens the app
- **THEN** the inbox shows the new videos as the phone keeps them, and no request leaves the phone
