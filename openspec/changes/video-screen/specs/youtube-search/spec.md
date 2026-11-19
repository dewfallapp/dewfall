# Spec Delta

## ADDED Requirements

### Requirement: Tapping a video opens it
Tapping a row SHALL open that video's video screen, for a video, a live stream and an upcoming video alike. Search YouTube SHALL keep its text and results underneath, so going back shows them as they were, with no new search. Tapping a second time while the video screen opens SHALL NOT open it twice.

#### Scenario: Opening a result
- **WHEN** the screen shows results and the user taps a row
- **THEN** the video screen opens for that row's video

#### Scenario: Going back to the results
- **WHEN** the user opened a video from the results and goes back
- **THEN** the screen shows the same text and results, and nothing is sent to YouTube again

#### Scenario: A double tap
- **WHEN** the user taps the same row twice quickly
- **THEN** one video screen opens, and one back gesture returns to the results

## MODIFIED Requirements

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

### Requirement: Search text stays private
The app SHALL never log the search text, or the titles, channel names and IDs of results, and SHALL send them nowhere but YouTube. It SHALL keep no search history. The only copies kept outside the screen SHALL be the field's text, and the ID of a result the user opened, in the state Android saves for a process that it ends in the background. The player spec's "Video details stay private" covers the opened video.

#### Scenario: Logs after a search
- **WHEN** the user searches and results show
- **THEN** the app's log lines name the search and its outcome category and count, and hold no search text, title, channel name or ID

#### Scenario: Opening a result
- **WHEN** the user opens a result and the system later ends the app's process in the background
- **THEN** the state Android saved holds the field's text and that video's ID, and no title, channel name or other result
