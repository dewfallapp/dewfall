# Spec Delta

## MODIFIED Requirements

### Requirement: Video rows show the thumbnail
The video rows of Search YouTube, the inbox, the channel page and the Downloads screen SHALL show the video's thumbnail in the thumbnail box, filling it and cut to its 16:9 shape and rounded corners. The length tag, "Live" or "Upcoming" SHALL stay in the box's lower right corner, over the image. The image SHALL be hidden from screen readers, which read the row's text as before.

#### Scenario: A search result with a thumbnail
- **WHEN** a search returns a video with thumbnails, and the image loads
- **THEN** its row shows the image in the box, with the video's length over its lower right corner

#### Scenario: A screen reader on a video row
- **WHEN** a screen reader reads a video row that shows a thumbnail
- **THEN** it reads the title, the length and the rest of the row's text, and nothing for the image

#### Scenario: A row of the Downloads screen
- **WHEN** the Downloads screen lists a download whose saved summary has thumbnails, and the image loads
- **THEN** its row shows the image in the box, with the video's length over its lower right corner
