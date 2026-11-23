# Spec Delta

## ADDED Requirements

### Requirement: Results list the matching channels first
A successful search SHALL list the channels that YouTube returned under a "Channels" heading, above the videos under a "Videos" heading, in YouTube's order, showing a channel that YouTube lists twice only once, at its first place. Each row SHALL show a round emblem with the first character of the channel's name, hidden from screen readers, the name, and the excerpt of its description that YouTube gave, if any. A heading SHALL show only above a list that has rows.

#### Scenario: Channels and videos found
- **WHEN** a search for "sourdough" returns the channels "Kettle and Crumb" and "The Starter Jar", and three videos
- **THEN** the screen shows "Channels" with both channels in that order, then "Videos" with the three videos

#### Scenario: Only channels found
- **WHEN** a search returns two channels and no videos, or only Shorts
- **THEN** the screen shows "Channels" with both channels, and no "Videos" heading

#### Scenario: Only videos found
- **WHEN** a search returns videos and no channels
- **THEN** the screen shows "Videos" with the videos, and no "Channels" heading

#### Scenario: A channel listed twice
- **WHEN** a search returns the same channel twice
- **THEN** the screen lists it once, where it first appears

#### Scenario: A long name or a large font
- **WHEN** a channel's name is longer than one line, or the font size is large
- **THEN** the name wraps onto more lines and is never cut off, and the row's button shows whole

#### Scenario: A long excerpt
- **WHEN** the excerpt of a channel's description takes more than two lines
- **THEN** the row shows its first two lines, ending with an ellipsis

#### Scenario: No excerpt
- **WHEN** YouTube gave no description for a channel
- **THEN** its row shows the emblem, the name and the button

### Requirement: A channel row says whether the user is subscribed
Each channel row SHALL end with a button that says whether the user is subscribed: a tonal "Subscribe" with an add glyph, or an outlined "Subscribed" with a check glyph, so the word and glyph carry the state, never color alone. A screen reader SHALL hear "Subscribe to" or "Subscribed to" and the channel's name. The button SHALL show once the app has read the subscriptions, and follow them, whichever screen changed them.

#### Scenario: A channel the user follows
- **WHEN** the results hold a channel the user subscribed to
- **THEN** its row shows an outlined "Subscribed" button with a check glyph, which a screen reader hears as "Subscribed to" and the channel's name

#### Scenario: A channel the user doesn't follow
- **WHEN** the results hold a channel the user didn't subscribe to
- **THEN** its row shows a tonal "Subscribe" button with an add glyph, which a screen reader hears as "Subscribe to" and the channel's name

#### Scenario: A channel that moved
- **WHEN** the user is subscribed under an ID that YouTube now sends on to another channel, and search lists that channel under its new ID
- **THEN** its row shows "Subscribe", because no subscription is kept under the new ID

#### Scenario: Subscribed on the channel page
- **WHEN** the user opens a channel from the results, subscribes on its page, and goes back
- **THEN** that channel's row shows "Subscribed"

### Requirement: Subscribing from a channel row takes effect at once
Tapping "Subscribe" in a channel row SHALL subscribe the user to that channel at once, with no question, keeping its ID, name and avatar links on the phone, and no description, because the excerpt isn't the whole description. The button SHALL then say "Subscribed", and a screen reader SHALL hear "Subscribed to" and the channel's name, once. Subscribing SHALL send nothing to YouTube, and SHALL save none of the channel's videos.

#### Scenario: Subscribing
- **WHEN** the user taps "Subscribe" in the row of "Kettle and Crumb"
- **THEN** the button says "Subscribed", a screen reader says "Subscribed to Kettle and Crumb", and the channel is kept on the phone as subscribed, with its name, avatar links and no description

#### Scenario: The channel's page afterwards
- **WHEN** the user subscribed from a channel row, then opens that channel's page while YouTube can't be reached
- **THEN** the page shows the channel's name, no description, and "Subscribed"

#### Scenario: The inbox afterwards
- **WHEN** the user subscribed from a channel row, and has not opened the channel's page since
- **THEN** none of the channel's videos are saved on the phone, so the inbox shows none of them yet

### Requirement: Unsubscribing from a channel row asks first
Tapping "Subscribed" in a channel row SHALL open the channel page's Unsubscribe dialog, with the same words. "Unsubscribe" SHALL forget the subscription, the button SHALL say "Subscribe", and a screen reader SHALL hear "Subscribe to" and the channel's name. "Cancel", the back gesture and a tap outside the dialog SHALL close it and change nothing. The dialog SHALL stay open across a configuration change.

#### Scenario: Unsubscribing
- **WHEN** the user taps "Subscribed" in the row of "Kettle and Crumb", then "Unsubscribe" in the dialog
- **THEN** the dialog asked "Unsubscribe from Kettle and Crumb?" and "Its new videos stop showing in your inbox.", the button now says "Subscribe", and the subscription is no longer kept on the phone

#### Scenario: Changing their mind
- **WHEN** the user taps "Subscribed", then "Cancel"
- **THEN** the dialog closes, and the button still says "Subscribed"

#### Scenario: A configuration change with the dialog open
- **WHEN** the dialog is open and the theme changes
- **THEN** the dialog is still open, for the same channel

### Requirement: Tapping a channel opens its page
Tapping a channel row anywhere but its button SHALL open that channel's page. Search YouTube SHALL keep its text and results underneath, so going back shows them as they were, with no new search. Tapping a second time while the page opens SHALL NOT open it twice.

#### Scenario: Opening a channel
- **WHEN** the screen shows results and the user taps the row of "Kettle and Crumb", outside its button
- **THEN** the channel page of "Kettle and Crumb" opens

#### Scenario: Going back to the results
- **WHEN** the user opened a channel from the results and goes back
- **THEN** the screen shows the same text and results, and nothing is sent to YouTube again

#### Scenario: A double tap
- **WHEN** the user taps the same channel row twice quickly
- **THEN** one channel page opens, and one back gesture returns to the results

## MODIFIED Requirements

### Requirement: Tapping a video opens it
Tapping a video row SHALL open that video's video screen, for a video, a live stream and an upcoming video alike. Search YouTube SHALL keep its text and results underneath, so going back shows them as they were, with no new search. After the system ends the app's process in the background, going back shows the text alone, as the screen's state requirement says. Tapping a second time while the video screen opens SHALL NOT open it twice.

#### Scenario: Opening a result
- **WHEN** the screen shows results and the user taps a video row
- **THEN** the video screen opens for that row's video

#### Scenario: Going back to the results
- **WHEN** the user opened a video from the results and goes back
- **THEN** the screen shows the same text and results, and nothing is sent to YouTube again

#### Scenario: A double tap
- **WHEN** the user taps the same video row twice quickly
- **THEN** one video screen opens, and one back gesture returns to the results

### Requirement: The screen explains itself before the first search
Before the first search, the screen SHALL say that it finds videos and channels, that a video opened from it streams right away, and that a channel's new videos show in the inbox once the user subscribes to it. The line under the screen's title SHALL say "Videos to stream and channels to follow".

#### Scenario: Nothing searched yet
- **WHEN** the screen opens and nothing has been searched
- **THEN** it shows "Find a video or a channel", the message, and no results

### Requirement: No results says so
When a search succeeds with no channels and no videos to show, the screen SHALL say that nothing on YouTube matches the search text, and suggest checking the spelling or trying fewer words.

#### Scenario: Nothing matches
- **WHEN** a search for "fermented birch sap" returns no channels and no videos
- **THEN** the screen says "Nothing on YouTube matches “fermented birch sap”. Check the spelling, or try fewer words.", and shows no rows

#### Scenario: Only Shorts or channels match
- **WHEN** a search returns only Shorts and channels
- **THEN** the screen lists the channels, with no "Videos" heading, and doesn't say that nothing matches

#### Scenario: Only Shorts match
- **WHEN** a search returns only Shorts
- **THEN** the screen says that nothing on YouTube matches the search text, and shows no rows

### Requirement: Search text stays private
The app SHALL never log the search text, or the titles, channel names, descriptions and IDs of results, and SHALL send them nowhere but YouTube. It SHALL keep no search history. The only copies kept outside the screen SHALL be the field's text, and the ID of a video or a channel the user opened from the results, in the state Android saves for a process that it ends in the background. The player spec's "Video details stay private" covers the opened video.

#### Scenario: Logs after a search
- **WHEN** the user searches and results show
- **THEN** the app's log lines name the search and its outcome category and count, and hold no search text, title, channel name, description or ID

#### Scenario: Opening a result
- **WHEN** the user opens a result and the system later ends the app's process in the background
- **THEN** the state Android saved holds the field's text and that video's ID, and no title, channel name or other result

#### Scenario: Opening a channel
- **WHEN** the user opens a channel from the results and the system later ends the app's process in the background
- **THEN** the state Android saved holds the field's text and that channel's ID, and no channel name, description or other result

#### Scenario: Subscribing from a channel row
- **WHEN** the user subscribes from a channel row
- **THEN** no request leaves the phone, nothing about the channel is written to the app's log, and the channel is kept only as the subscriptions spec's "What Dewfall keeps stays on the phone" says
