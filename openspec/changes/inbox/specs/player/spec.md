# Spec Delta

## MODIFIED Requirements

### Requirement: A media notification shows while a video plays
While a video plays, and while it is paused until Android stops the player, the app SHALL show a media notification with a play and pause button, and the video's title and channel name once its details have loaded. It SHALL show no picture. Tapping it SHALL open the app as it was, on the video screen or on the screen under the mini player. While the video plays, the user SHALL NOT be able to dismiss it.

#### Scenario: Playing in the background
- **WHEN** a video plays and the user goes to the home screen
- **THEN** a notification shows the video's title and channel name with a pause button, and the pause button pauses the video

#### Scenario: Playing in the app
- **WHEN** a video plays on the video screen, or in the mini player
- **THEN** the notification shows too

#### Scenario: Tapping the notification
- **WHEN** a video plays in the background and the user taps its notification
- **THEN** Dewfall opens as it was, on the video screen, or on the screen under the video screen with the mini player, such as the inbox, Search YouTube or a channel page, and the video plays on

#### Scenario: Details that failed
- **WHEN** a video plays but its details failed to load
- **THEN** the notification shows no title or channel name, and its button still plays and pauses

#### Scenario: Paused for a long time
- **WHEN** a video stays paused in the background for more than 10 minutes
- **THEN** the user can dismiss the notification, which stops playback, and Android may remove it

#### Scenario: A notification left by an ended process
- **WHEN** the system ended the app's process while a paused video's notification still showed, and the user taps its play button
- **THEN** nothing plays, nothing crashes, and the notification goes away

#### Scenario: A failure in the background
- **WHEN** a video plays in the background and the player runs out of data because the network went away
- **THEN** the notification stays with a play button, which tries again from the same position, and once the video plays again the video screen, or the mini player when the video is minimized, shows it playing, with no failure

### Requirement: The mini player shows while a video is minimized
While the player has a video and no video screen shows, the app SHALL show a mini player at the bottom of the screen. It SHALL show for a video that plays, is paused, waits for data, has ended or failed while playing. It SHALL go away when playback stops, or when Android stops the player.

#### Scenario: A minimized video
- **WHEN** a video plays and the user minimizes it
- **THEN** the mini player shows at the bottom of the screen that was under the video screen, such as the inbox, Search YouTube or a channel page

#### Scenario: The video screen
- **WHEN** the video screen shows
- **THEN** no mini player shows

#### Scenario: No video
- **WHEN** the app opens and nothing has played
- **THEN** no mini player shows

#### Scenario: A video that couldn't start
- **WHEN** the user minimizes a video whose stream links failed to load, so the player has no video
- **THEN** no mini player shows

#### Scenario: After the process ended
- **WHEN** the system ended the app's process while the mini player showed, and the user returns to the app
- **THEN** no mini player shows, and nothing plays

#### Scenario: Paused and minimized in the background, back much later
- **WHEN** the user paused a minimized video, went to the home screen, and Android stopped the player before the user came back
- **THEN** no mini player shows, and opening the video again starts it from where it was paused
