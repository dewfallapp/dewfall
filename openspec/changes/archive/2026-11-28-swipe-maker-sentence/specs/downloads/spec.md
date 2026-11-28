## MODIFIED Requirements

### Requirement: Downloads run one at a time without any screen
Downloads SHALL run in a background job that no screen owns, one video at a time, in the order the user asked for them. A download SHALL go on after the user leaves the screen it started from, leaves the app or turns the screen off. A download that stopped because the app's process ended, the phone restarted or the connection dropped SHALL go on by itself from the bytes it already has, once the phone is connected again. A download that Android refused to schedule, such as one asked for just as the app left the screen, SHALL go on the next time the app shows. When the user stops Dewfall while a download runs, such as with the Stop button in Android's Task Manager or Force stop in its settings, every download that was waiting or running SHALL count as paused, and SHALL go on only when the user taps its download control; on Android 10, which can't tell the app such a stop apart, it SHALL go on the next time the app shows. Updating the app SHALL NOT pause a download, and on stock Android neither SHALL swiping Dewfall away from Android's recent apps; on a phone whose maker ends a running download's process on that swipe, the download SHALL count as paused, as after a Stop, unless the phone records that end exactly as stock Android records a swipe, which the app can't tell apart from stock Android, so the download SHALL go on as it does there. While no download can run, its video SHALL say "Waiting to download".

#### Scenario: Leaving the screen
- **WHEN** the user taps "Download", goes back to the inbox and opens another video
- **THEN** the first video goes on downloading

#### Scenario: Two downloads
- **WHEN** the user asks for two videos to download, one after the other
- **THEN** the first downloads while the second says "Waiting to download", and the second starts when the first is done

#### Scenario: The connection drops
- **WHEN** a download is at 40 percent and the phone loses its connection, then gets it back
- **THEN** the video says "Waiting to download" while the phone has no connection, and goes on from about 40 percent once it is back, without fetching the first 40 percent again

#### Scenario: The app's process ends
- **WHEN** a download is at 40 percent, the app isn't showing, and Android ends the app's process
- **THEN** the download goes on from about 40 percent by itself, within about a minute while the phone is awake and connected, without the user opening the app or turning the screen on

#### Scenario: Stopped from the Task Manager
- **WHEN** a download is at 40 percent and the user stops Dewfall from Android's Task Manager, then opens the app again
- **THEN** the video says "Paused, 40%", and goes on from about 40 percent only when the user taps its download button

#### Scenario: Swiped away from the recent apps
- **WHEN** a download waits for a connection and the user swipes Dewfall away from the recent apps, then the connection comes back
- **THEN** the download goes on by itself, and the video never says "Paused"

#### Scenario: Swiped away while downloading in the background
- **WHEN** on stock Android 12 or 13, a download goes on in the background after the phone restarted, and the user opens the app, leaves it and swipes it away from the recent apps
- **THEN** the download goes on from its bytes, and the video never says "Paused"

#### Scenario: The phone restarts
- **WHEN** a download is at 40 percent and the phone restarts
- **THEN** the download goes on from about 40 percent once the phone is connected, without the user opening the app
