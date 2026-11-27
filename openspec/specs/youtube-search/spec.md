# youtube-search Specification

## Purpose

Lets a user search YouTube for videos to stream and channels to follow, and see the results, an empty result or a failure on the Search YouTube screen. A channel found there can be subscribed to, unsubscribed from, or opened on its channel page.

## Requirements

### Requirement: The keyboard opens once
The search field SHALL take focus and the on-screen keyboard SHALL open the first time the screen opens. Each time the user opens Search YouTube from the inbox, the Channels screen or the Downloads screen counts as a first time, because it opens a new screen with an empty field. It SHALL NOT open again by itself after a configuration change or when the user comes back to the screen. Sending a search with the on-screen keyboard's search key SHALL hide that keyboard, so the results show.

#### Scenario: First open
- **WHEN** the screen opens with no saved state, such as when the user opens it from the inbox, the Channels screen or the Downloads screen
- **THEN** the field has focus and the on-screen keyboard is open

#### Scenario: After a search
- **WHEN** the user sends a search with the on-screen keyboard's search key
- **THEN** the on-screen keyboard closes, and opening it again takes a tap on the field

#### Scenario: A configuration change
- **WHEN** the screen shows results, the keyboard is closed, and the theme changes
- **THEN** the keyboard stays closed

### Requirement: A search runs only when the user sends it
The screen SHALL send a search to YouTube only when the user presses the on-screen keyboard's search key or Enter on a hardware keyboard, never while the user types. Each search SHALL use the text in the field, trimmed of spaces at both ends. The text SHALL never hold a line break: Enter sends the search, and a line break in pasted text becomes a space.

#### Scenario: Typing doesn't search
- **WHEN** the user types in the search field without pressing the search key
- **THEN** no request goes to YouTube, and only the field and its clear button change

#### Scenario: Sending a search
- **WHEN** the user types "sourdough" and presses the search key
- **THEN** the screen searches YouTube for "sourdough" and shows that it is loading

#### Scenario: The on-screen keyboard
- **WHEN** the user types "sourdough" with the on-screen keyboard and presses its search key
- **THEN** the screen searches YouTube for "sourdough"

#### Scenario: Enter on a hardware keyboard
- **WHEN** the user types "sourdough" on a hardware keyboard and presses Enter
- **THEN** the screen searches YouTube for "sourdough", and the field holds no line break

#### Scenario: Pasted text with a line break
- **WHEN** the user pastes "sourdough" and "starter" on two lines
- **THEN** the field holds "sourdough starter"

#### Scenario: Blank text
- **WHEN** the field is empty or holds only spaces, and the user presses the search key
- **THEN** no request goes to YouTube, and the results area doesn't change

### Requirement: The screen explains itself before the first search
Before the first search, the screen SHALL say that it finds videos and channels, that a video opened from it streams right away, and that a channel's new videos show in the inbox once the user subscribes to it. The line under the screen's title SHALL say "Videos to stream and channels to follow".

#### Scenario: Nothing searched yet
- **WHEN** the screen opens and nothing has been searched
- **THEN** it shows "Find a video or a channel", the message, and no results

### Requirement: One search at a time
While a search runs, the screen SHALL show that it is loading. A new search SHALL replace a running one, and the screen SHALL show only the answer to the latest search, even when two searches have the same text.

#### Scenario: A second search before the first answers
- **WHEN** the user sends "bread", then sends "sourdough" before the answer for "bread" arrives
- **THEN** the search for "bread" stops, and the screen shows only the answer for "sourdough"

#### Scenario: The same text after clearing
- **WHEN** the user sends "bread", clears the field, then sends "bread" again before the first answer arrives
- **THEN** the screen shows only the answer to the second search

### Requirement: Results show videos in YouTube's order
A successful search SHALL list the videos that YouTube returned, in YouTube's order, leaving out Shorts, and showing a video that YouTube lists twice only once, at its first place. Each row SHALL show a thumbnail box, the title, the channel's name and when the video was published.

#### Scenario: Videos found
- **WHEN** a search returns three videos
- **THEN** the screen lists the three videos in the order YouTube gave them, each with its title, channel name and publish time

#### Scenario: Shorts in the answer
- **WHEN** a search returns four videos, and one of them is a Short
- **THEN** the screen lists the other three, in YouTube's order

#### Scenario: A video listed twice
- **WHEN** a search returns the same video twice
- **THEN** the screen lists it once, where it first appears

#### Scenario: A title that doesn't fit
- **WHEN** a title is longer than one line, or the font size is large
- **THEN** the title wraps onto more lines and is never cut off

### Requirement: A row says how long a video is, or that it is live or upcoming
The thumbnail box SHALL show the video's length. A live stream SHALL show "Live" instead, and a video that hasn't started SHALL show "Upcoming". A video whose length YouTube didn't give, and that is neither, SHALL show no length.

#### Scenario: A video over an hour
- **WHEN** a video is 1 hour and 6 minutes long
- **THEN** its box shows "1h 06m", which a screen reader reads as "1 hour 6 minutes"

#### Scenario: A video under a minute
- **WHEN** a video is 45 seconds long
- **THEN** its box shows "45s"

#### Scenario: A live stream
- **WHEN** a video is a live stream
- **THEN** its box shows "Live" and no length

#### Scenario: An upcoming video
- **WHEN** a video hasn't started yet
- **THEN** its box shows "Upcoming" and no length

#### Scenario: An unknown length
- **WHEN** YouTube gave no length for a video that is neither live nor upcoming
- **THEN** its box shows no label

### Requirement: Publish times read as a time ago
A row SHALL show its publish time as the time since it, counted from the phone's clock and calendar: minutes and hours in short form, such as "7h ago", then "Today" for an older time that still falls on today's date, such as an estimated one, then "Yesterday", days up to 13, weeks until a calendar month has passed, then months and years. A row SHALL show no time when YouTube gave none, or when the time is in the future.

#### Scenario: Hours
- **WHEN** a video was published 7 hours before now
- **THEN** its row says "7h ago"

#### Scenario: An estimated time today
- **WHEN** YouTube gave only an estimated time, and it falls on today's date
- **THEN** its row says "Today"

#### Scenario: Under two weeks
- **WHEN** a video was published 10 days before today
- **THEN** its row says "10 days ago"

#### Scenario: A video from three weeks ago
- **WHEN** a video was published 22 days before today
- **THEN** its row says "3 weeks ago"

#### Scenario: YouTube's months
- **WHEN** YouTube said a video is "1 month ago", and today is in March of a year that isn't a leap year
- **THEN** its row says "1 month ago"

#### Scenario: No publish time
- **WHEN** YouTube gave no publish time for a video
- **THEN** its row shows the channel's name and no time

#### Scenario: A time in the future
- **WHEN** an upcoming video's time is tomorrow
- **THEN** its row shows the channel's name and no time

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

### Requirement: A failed search says why
When a search fails, the screen SHALL show a message for its YouTubeError category and no rows, and offer "Try again", which sends the same search again.

#### Scenario: YouTube can't be reached
- **WHEN** a search fails with NETWORK
- **THEN** the screen says that YouTube can't be reached and to check the connection, and offers "Try again"

#### Scenario: Any other category
- **WHEN** a search fails with BLOCKED, BROKEN or another category
- **THEN** the screen says that YouTube isn't loading right now, and offers "Try again"

#### Scenario: Trying again
- **WHEN** the user taps "Try again" after a search for "sourdough" failed
- **THEN** the screen searches for "sourdough" again and shows that it is loading

### Requirement: Clearing the field starts over
The field SHALL show a clear button while it holds text. Clearing SHALL empty the field, stop a running search, and show the screen as it was before the first search.

#### Scenario: Clearing after results
- **WHEN** the screen shows results and the user taps the clear button
- **THEN** the field is empty, the results are gone, and the message from before the first search shows

### Requirement: The screen keeps its state across configuration changes
A configuration change, such as a theme change, a fold or a resized window, SHALL keep the search text, the results and a running search. After the system ends the app's process in the background, the screen SHALL come back with the search text and nothing else.

#### Scenario: Resizing with results
- **WHEN** the screen shows results and its window changes size, such as in split screen
- **THEN** the same text and results show, and nothing is sent to YouTube again

#### Scenario: Coming back after process death
- **WHEN** the system ended the app's process while it showed results, and the user returns to it
- **THEN** the field holds the same text, and the screen shows the message from before the first search

### Requirement: The screen fits any window
On a phone the screen SHALL stay in portrait. In any window wider than a phone in portrait, such as a tablet, an unfolded foldable, landscape on a large screen or split screen, the screen SHALL keep its content in one column of readable width, centered, and the background SHALL fill the window.

#### Scenario: Rotating a phone
- **WHEN** the user turns a phone to landscape
- **THEN** the screen stays in portrait

#### Scenario: A tablet in landscape
- **WHEN** the screen shows results on a tablet in landscape
- **THEN** the rows sit in one centered column no wider than the readable width, and nothing is cut off

### Requirement: Search text stays private
The app SHALL never log the search text, or the titles, channel names, descriptions and IDs of results, and SHALL send them nowhere but YouTube. It SHALL keep no search history. The only copies kept outside the screen SHALL be the text of each open Search YouTube's field, at most one per destination, and the ID of a video or a channel opened from the results, in the state Android saves before ending the process. The player spec's "Video details stay private" covers the opened video.

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

### Requirement: Results list the matching channels first
A successful search SHALL list the channels that YouTube returned under a "Channels" heading, above the videos under a "Videos" heading, in YouTube's order, showing a channel that YouTube lists twice only once, at its first place. Each row SHALL show a round emblem with the first character of the channel's name, or the channel's picture, hidden from screen readers, the name, and the excerpt of its description that YouTube gave, if any. A heading SHALL show only above a list that has rows.

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

### Requirement: Search YouTube opens from the inbox, the Channels screen and the Downloads screen
Search YouTube SHALL open from the search button of the inbox, of the Channels screen and of the Downloads screen, and from the "Search YouTube" button of the inbox's and the Channels screen's "No channels yet" states. It SHALL show its title, the line "Videos to stream and channels to follow", and an empty search field. Tapping a second time while the screen opens SHALL NOT open it twice.

#### Scenario: Opening Search YouTube
- **WHEN** the user taps the inbox's search button
- **THEN** Search YouTube shows its title, the line "Videos to stream and channels to follow", an empty search field and a back arrow

#### Scenario: Opening Search YouTube from the Channels screen
- **WHEN** the user taps the Channels screen's search button
- **THEN** Search YouTube shows an empty search field and a back arrow, and no navigation bar

#### Scenario: Opening Search YouTube from the Downloads screen
- **WHEN** the user taps the Downloads screen's search button
- **THEN** Search YouTube shows an empty search field and a back arrow, and no navigation bar

### Requirement: Search YouTube's back arrow returns to the screen it opened from
Search YouTube's top bar SHALL have a back arrow, which a screen reader SHALL hear as "Back". The back arrow and the back gesture SHALL return to the screen Search YouTube opened from, the inbox, the Channels screen or the Downloads screen. Tapping the back arrow twice SHALL return to that screen and go no further.

#### Scenario: Going back
- **WHEN** the user opened Search YouTube from the inbox, it shows results, and the user taps the back arrow
- **THEN** the inbox shows, and opening Search YouTube again shows an empty field

#### Scenario: Going back to the Channels screen
- **WHEN** the user opened Search YouTube from the Channels screen and makes the back gesture
- **THEN** the Channels screen shows, with "Channels" selected

#### Scenario: Going back to the Downloads screen
- **WHEN** the user opened Search YouTube from the Downloads screen and makes the back gesture
- **THEN** the Downloads screen shows, with "Downloads" selected

#### Scenario: A double tap on the back arrow
- **WHEN** the user opened Search YouTube from the inbox and taps its back arrow twice quickly
- **THEN** the inbox shows, and the app stays open
