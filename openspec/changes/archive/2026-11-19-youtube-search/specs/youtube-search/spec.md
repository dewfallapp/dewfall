# Spec Delta

## Purpose

Lets a user search YouTube for videos to stream, and see the results, an empty result or a failure on the Search YouTube screen.

## ADDED Requirements

### Requirement: The app opens on Search YouTube
The app SHALL open on the Search YouTube screen, until a later change makes another screen the home screen. The screen SHALL have no back arrow, because no screen comes before it.

#### Scenario: Opening the app
- **WHEN** the user opens the app
- **THEN** the Search YouTube screen shows its title, a line saying it finds videos to stream, and an empty search field

### Requirement: The keyboard opens once
The search field SHALL take focus and the on-screen keyboard SHALL open the first time the screen opens. It SHALL NOT open again by itself after a configuration change or when the user comes back to the screen. Sending a search with the on-screen keyboard's search key SHALL hide that keyboard, so the results show.

#### Scenario: First open
- **WHEN** the screen opens with no saved state, such as when the app starts
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
Before the first search, the screen SHALL say that it finds videos, and that a video opened from it streams right away.

#### Scenario: Nothing searched yet
- **WHEN** the screen opens and nothing has been searched
- **THEN** it shows the message, and no results

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
A row SHALL show its publish time as the time since it, counted from the phone's clock and calendar: minutes and hours in short form, such as "7h ago", then "Yesterday", days up to 13, weeks until a calendar month has passed, then months and years. A row SHALL show no time when YouTube gave none, or when the time is in the future.

#### Scenario: Hours
- **WHEN** a video was published 7 hours before now
- **THEN** its row says "7h ago"

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
When a search succeeds with no videos to show, the screen SHALL say that it found no videos for the search text, and suggest checking the spelling or trying fewer words.

#### Scenario: Nothing matches
- **WHEN** a search for "fermented birch sap" returns no videos
- **THEN** the screen says that it found no videos for "fermented birch sap", and shows no rows

#### Scenario: Only Shorts or channels match
- **WHEN** a search returns only Shorts and channels
- **THEN** the screen says that it found no videos for the search text, and shows no rows

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
The app SHALL never log the search text, or the titles, channel names and IDs of results, and SHALL send them nowhere but YouTube. It SHALL keep no search history. The only copy kept outside the screen SHALL be the field's text in the state Android saves for a process that it ends in the background.

#### Scenario: Logs after a search
- **WHEN** the user searches and results show
- **THEN** the app's log lines name the search and its outcome category and count, and hold no search text, title, channel name or ID
