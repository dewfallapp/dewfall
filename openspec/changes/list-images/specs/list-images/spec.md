## Purpose

Shows each video's thumbnail and each channel's picture where Dewfall's screens list videos and channels, loaded only from YouTube's image hosts, with the design's plain placeholders while an image loads or when it can't.

## ADDED Requirements

### Requirement: Video rows show the thumbnail
The video rows of Search YouTube, the inbox and the channel page SHALL show the video's thumbnail in the thumbnail box, filling it and cut to its 16:9 shape and rounded corners. The length tag, "Live" or "Upcoming" SHALL stay in the box's lower right corner, over the image. The image SHALL be hidden from screen readers, which read the row's text as before.

#### Scenario: A search result with a thumbnail
- **WHEN** a search returns a video with thumbnails, and the image loads
- **THEN** its row shows the image in the box, with the video's length over its lower right corner

#### Scenario: A screen reader on a video row
- **WHEN** a screen reader reads a video row that shows a thumbnail
- **THEN** it reads the title, the length and the rest of the row's text, and nothing for the image

### Requirement: Channel rows show the avatar
The channel rows of Search YouTube, the inbox's cards, the Channels screen, the channel page's header and the video screen's channel row SHALL show the channel's picture in the round emblem, filling the circle, with the emblem's ring around it. The picture SHALL be hidden from screen readers, which read the channel's name next to it.

#### Scenario: A subscribed channel with a picture
- **WHEN** the Channels screen lists a channel whose saved details have a picture, and the image loads
- **THEN** its row shows the picture in the circle, in place of the initial

#### Scenario: The channel page's header
- **WHEN** the channel page opens for a channel whose picture YouTube gave, and the image loads
- **THEN** the header shows the picture in its large circle above the name

### Requirement: Placeholders show until an image loads
While an image loads, and when it can't load, a video row SHALL show the plain thumbnail box, and a channel SHALL show its emblem with its initial. Nothing SHALL mark a failed image. A row whose video or channel has no image link SHALL keep its placeholder and never ask for one.

#### Scenario: No connection
- **WHEN** the inbox shows saved videos while the phone has no connection, and no image of them is on the phone
- **THEN** each row shows the plain box with its length, each card shows the emblem with the initial, and the rows open videos as before

#### Scenario: A channel imported from a file
- **WHEN** a channel subscribed from a Takeout file has no saved picture yet
- **THEN** it shows its emblem with its initial, and its picture once a save of its uploads brings one

### Requirement: An image asks for the size it fills
For each image, the app SHALL ask for the smallest size that YouTube gave that is at least as wide, in pixels, as the space the image fills on the screen. When none is that wide, it SHALL ask for the widest. Sizes whose width YouTube didn't give SHALL count only when no size has a width, and then the first of them SHALL be asked for.

#### Scenario: Several sizes
- **WHEN** a thumbnail comes in 120, 320 and 480 pixels wide, and the box is 189 pixels wide on the phone
- **THEN** the app asks for the 320-pixel one

#### Scenario: Larger text
- **WHEN** the user's font size makes the box 600 pixels wide, and the widest size is 480
- **THEN** the app asks for the 480-pixel one

### Requirement: Images load only from YouTube's image hosts
The app SHALL load an image only from a link that the YouTube layer gave, only over HTTPS, and only from i.ytimg.com, yt3.ggpht.com or yt3.googleusercontent.com. It SHALL ask for British English, name no country, send no cookies, follow no redirect, and load nothing from any other host, which leaves the placeholder. Image requests SHALL go straight to those hosts, with no Dewfall server between.

#### Scenario: A link to another host
- **WHEN** an image link points to any host other than the three, or uses plain HTTP
- **THEN** the app sends no request for it, and the row keeps its placeholder

#### Scenario: A link to something on the phone
- **WHEN** an image link is a `file://`, `content://`, `data:` or `android.resource://` link
- **THEN** the app loads nothing for it, and the row keeps its placeholder

#### Scenario: A redirect
- **WHEN** an image host answers with a redirect
- **THEN** the app doesn't follow it, and the row keeps its placeholder

### Requirement: Loaded images stay in the app's cache
The app SHALL keep loaded images in its own cache folder on the phone, up to a fixed size, dropping the least recently used first, so a list shown again doesn't load its images again. Nothing else SHALL read or send that cache.

#### Scenario: Returning to a list
- **WHEN** the user opens the inbox, closes the app, and opens the inbox again later without a connection
- **THEN** the thumbnails and channel pictures that loaded the first time show again, if the cache still holds them
