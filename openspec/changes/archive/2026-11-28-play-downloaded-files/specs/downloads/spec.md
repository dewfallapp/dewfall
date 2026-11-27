# Spec Delta

## ADDED Requirements

### Requirement: Only a finished download plays
A download's files SHALL play only once the download is done and its files match it, as the player spec defines that. A download that waits, runs, is paused or failed SHALL never play from its partial files, and its video SHALL stream when its playback starts. Playing a downloaded video SHALL NOT change its download, and checking its files SHALL NOT change its status, even when a file is missing.

#### Scenario: Playing while it downloads
- **WHEN** the user opens a video that the player doesn't hold while its download runs
- **THEN** the video streams, and the download goes on and finishes as it would have

#### Scenario: A finished download
- **WHEN** a download finishes, its files still match it, and the user opens its video, which the player doesn't hold
- **THEN** the video plays from the download's files, and the download still says "Downloaded"
