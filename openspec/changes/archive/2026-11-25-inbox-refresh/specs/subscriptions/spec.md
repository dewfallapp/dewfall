# Spec Delta

## ADDED Requirements

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

## MODIFIED Requirements

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
