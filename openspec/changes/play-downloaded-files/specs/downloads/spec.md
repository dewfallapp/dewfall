# Spec Delta

## ADDED Requirements

### Requirement: Only a finished download plays
A download's files SHALL play only once the download is done and every file it kept is on the phone, complete, at the size the download recorded, as the player spec describes. A download that waits, runs, is paused or failed SHALL never play from its partial files, and its video SHALL stream. Playing a downloaded video SHALL NOT change its download, and checking its files SHALL NOT change its status, even when a file is missing.

#### Scenario: Playing while it downloads
- **WHEN** the user plays a video while its download runs
- **THEN** the video streams, and the download goes on and finishes as it would have

#### Scenario: A finished download
- **WHEN** a download finishes and the user plays its video
- **THEN** the video plays from the download's files, and the download still says "Downloaded"
