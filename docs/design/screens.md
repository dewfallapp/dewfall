# Screen inventory

This file lists every screen in the [Claude Design export](claude-design/README.md). Each row names a screen and links its light and dark frame files and a screenshot of each. To open the frames and click through the prototypes, see [How to view them](claude-design/README.md#how-to-view-them) in the Claude Design README.

The canvas has 64 frames in 32 light and dark pairs. 29 pairs are screens. The other 3 pairs only serve the onboarding prototype, so they have their own group at the end.

The headings follow the screen list in the [design brief](brief.md), in its order. Each screen's name is its frame title from [canvas.json](claude-design/design/canvas.json), without "light" or "dark". Every screen the brief lists has at least one frame.

Three of the 29 screens have names that start with "Flow 1". The canvas keeps those frames in its Flow frames sections, next to the skip-path copies. No other section of the canvas has these screens, so they are listed under Onboarding and Inbox.

## Screens

### Onboarding

"Flow 1: channels imported" shows the channels found in a Takeout file. "Flow 1: battery setting done" shows step 4 once the battery setting is done.

| Screen | Light frame | Dark frame | Light screenshot | Dark screenshot |
|---|---|---|---|---|
| Onboarding 1, how Dewfall works | [Onboarding1-light.dc.html](claude-design/design/Onboarding1-light.dc.html) | [Onboarding1-dark.dc.html](claude-design/design/Onboarding1-dark.dc.html) | [Onboarding1-light.png](claude-design/screenshots/Onboarding1-light.png) | [Onboarding1-dark.png](claude-design/screenshots/Onboarding1-dark.png) |
| Onboarding 2, import channels | [Onboarding2-light.dc.html](claude-design/design/Onboarding2-light.dc.html) | [Onboarding2-dark.dc.html](claude-design/design/Onboarding2-dark.dc.html) | [Onboarding2-light.png](claude-design/screenshots/Onboarding2-light.png) | [Onboarding2-dark.png](claude-design/screenshots/Onboarding2-dark.png) |
| Flow 1: channels imported | [OnboardingImported-light.dc.html](claude-design/design/OnboardingImported-light.dc.html) | [OnboardingImported-dark.dc.html](claude-design/design/OnboardingImported-dark.dc.html) | [OnboardingImported-light.png](claude-design/screenshots/OnboardingImported-light.png) | [OnboardingImported-dark.png](claude-design/screenshots/OnboardingImported-dark.png) |
| Onboarding 3, storage limit | [Onboarding3-light.dc.html](claude-design/design/Onboarding3-light.dc.html) | [Onboarding3-dark.dc.html](claude-design/design/Onboarding3-dark.dc.html) | [Onboarding3-light.png](claude-design/screenshots/Onboarding3-light.png) | [Onboarding3-dark.png](claude-design/screenshots/Onboarding3-dark.png) |
| Onboarding 4, battery setup | [Onboarding4-light.dc.html](claude-design/design/Onboarding4-light.dc.html) | [Onboarding4-dark.dc.html](claude-design/design/Onboarding4-dark.dc.html) | [Onboarding4-light.png](claude-design/screenshots/Onboarding4-light.png) | [Onboarding4-dark.png](claude-design/screenshots/Onboarding4-dark.png) |
| Flow 1: battery setting done | [Onboarding4Done-light.dc.html](claude-design/design/Onboarding4Done-light.dc.html) | [Onboarding4Done-dark.dc.html](claude-design/design/Onboarding4Done-dark.dc.html) | [Onboarding4Done-light.png](claude-design/screenshots/Onboarding4Done-light.png) | [Onboarding4Done-dark.png](claude-design/screenshots/Onboarding4Done-dark.png) |

### Inbox

The light inbox is Main.dc.html, and its dark pair is Inbox-dark.dc.html. "Flow 1 ends: inbox right after import" is the inbox right after onboarding, with every video queued for tonight.

| Screen | Light frame | Dark frame | Light screenshot | Dark screenshot |
|---|---|---|---|---|
| Inbox | [Main.dc.html](claude-design/design/Main.dc.html) | [Inbox-dark.dc.html](claude-design/design/Inbox-dark.dc.html) | [Main.png](claude-design/screenshots/Main.png) | [Inbox-dark.png](claude-design/screenshots/Inbox-dark.png) |
| Flow 1 ends: inbox right after import | [InboxFirstRun-light.dc.html](claude-design/design/InboxFirstRun-light.dc.html) | [InboxFirstRun-dark.dc.html](claude-design/design/InboxFirstRun-dark.dc.html) | [InboxFirstRun-light.png](claude-design/screenshots/InboxFirstRun-light.png) | [InboxFirstRun-dark.png](claude-design/screenshots/InboxFirstRun-dark.png) |

### Search

"Choosing a search" is the sheet that opens from the Search button on the inbox. It offers "Search YouTube" and "Search subtitles". The subtitle search screens are listed under [Search inside videos](#search-inside-videos).

| Screen | Light frame | Dark frame | Light screenshot | Dark screenshot |
|---|---|---|---|---|
| Choosing a search | [SearchA-sheet-light.dc.html](claude-design/design/SearchA-sheet-light.dc.html) | [SearchA-sheet-dark.dc.html](claude-design/design/SearchA-sheet-dark.dc.html) | [SearchA-sheet-light.png](claude-design/screenshots/SearchA-sheet-light.png) | [SearchA-sheet-dark.png](claude-design/screenshots/SearchA-sheet-dark.png) |
| Search YouTube, before typing | [SearchYouTube-light.dc.html](claude-design/design/SearchYouTube-light.dc.html) | [SearchYouTube-dark.dc.html](claude-design/design/SearchYouTube-dark.dc.html) | [SearchYouTube-light.png](claude-design/screenshots/SearchYouTube-light.png) | [SearchYouTube-dark.png](claude-design/screenshots/SearchYouTube-dark.png) |
| Search YouTube, results for sourdough | [SearchResults-light.dc.html](claude-design/design/SearchResults-light.dc.html) | [SearchResults-dark.dc.html](claude-design/design/SearchResults-dark.dc.html) | [SearchResults-light.png](claude-design/screenshots/SearchResults-light.png) | [SearchResults-dark.png](claude-design/screenshots/SearchResults-dark.png) |

### Player

The two full screen frames have different titles, so the row uses the part they share. The light one is "Player full screen, in light mode (dark scheme)", because the full screen player uses the dark scheme in light mode too. The [design system README](claude-design/design-system/README.md) explains why.

| Screen | Light frame | Dark frame | Light screenshot | Dark screenshot |
|---|---|---|---|---|
| Player | [Player-light.dc.html](claude-design/design/Player-light.dc.html) | [Player-dark.dc.html](claude-design/design/Player-dark.dc.html) | [Player-light.png](claude-design/screenshots/Player-light.png) | [Player-dark.png](claude-design/screenshots/Player-dark.png) |
| Player full screen | [Fullscreen-light.dc.html](claude-design/design/Fullscreen-light.dc.html) | [Fullscreen-dark.dc.html](claude-design/design/Fullscreen-dark.dc.html) | [Fullscreen-light.png](claude-design/screenshots/Fullscreen-light.png) | [Fullscreen-dark.png](claude-design/screenshots/Fullscreen-dark.png) |
| Mini player on the inbox | [MiniPlayer-light.dc.html](claude-design/design/MiniPlayer-light.dc.html) | [MiniPlayer-dark.dc.html](claude-design/design/MiniPlayer-dark.dc.html) | [MiniPlayer-light.png](claude-design/screenshots/MiniPlayer-light.png) | [MiniPlayer-dark.png](claude-design/screenshots/MiniPlayer-dark.png) |

### Channel page

| Screen | Light frame | Dark frame | Light screenshot | Dark screenshot |
|---|---|---|---|---|
| Channel page, Kettle and Crumb | [Channel-light.dc.html](claude-design/design/Channel-light.dc.html) | [Channel-dark.dc.html](claude-design/design/Channel-dark.dc.html) | [Channel-light.png](claude-design/screenshots/Channel-light.png) | [Channel-dark.png](claude-design/screenshots/Channel-dark.png) |

### Downloads and storage

| Screen | Light frame | Dark frame | Light screenshot | Dark screenshot |
|---|---|---|---|---|
| Downloads | [Downloads-light.dc.html](claude-design/design/Downloads-light.dc.html) | [Downloads-dark.dc.html](claude-design/design/Downloads-dark.dc.html) | [Downloads-light.png](claude-design/screenshots/Downloads-light.png) | [Downloads-dark.png](claude-design/screenshots/Downloads-dark.png) |

### Search inside videos

"Saved subtitles, offline" opens when the phone is offline and the video isn't on it. It shows the subtitles around the matching moment.

| Screen | Light frame | Dark frame | Light screenshot | Dark screenshot |
|---|---|---|---|---|
| Search subtitles, before typing | [SubSearch-light.dc.html](claude-design/design/SubSearch-light.dc.html) | [SubSearch-dark.dc.html](claude-design/design/SubSearch-dark.dc.html) | [SubSearch-light.png](claude-design/screenshots/SubSearch-light.png) | [SubSearch-dark.png](claude-design/screenshots/SubSearch-dark.png) |
| Search subtitles, results for light | [SubResults-light.dc.html](claude-design/design/SubResults-light.dc.html) | [SubResults-dark.dc.html](claude-design/design/SubResults-dark.dc.html) | [SubResults-light.png](claude-design/screenshots/SubResults-light.png) | [SubResults-dark.png](claude-design/screenshots/SubResults-dark.png) |
| Saved subtitles, offline | [SubOffline-light.dc.html](claude-design/design/SubOffline-light.dc.html) | [SubOffline-dark.dc.html](claude-design/design/SubOffline-dark.dc.html) | [SubOffline-light.png](claude-design/screenshots/SubOffline-light.png) | [SubOffline-dark.png](claude-design/screenshots/SubOffline-dark.png) |

### Download notification

| Screen | Light frame | Dark frame | Light screenshot | Dark screenshot |
|---|---|---|---|---|
| Download notification, overnight run | [NotifRun-light.dc.html](claude-design/design/NotifRun-light.dc.html) | [NotifRun-dark.dc.html](claude-design/design/NotifRun-dark.dc.html) | [NotifRun-light.png](claude-design/screenshots/NotifRun-light.png) | [NotifRun-dark.png](claude-design/screenshots/NotifRun-dark.png) |
| Download notification, paused at the limit | [NotifPaused-light.dc.html](claude-design/design/NotifPaused-light.dc.html) | [NotifPaused-dark.dc.html](claude-design/design/NotifPaused-dark.dc.html) | [NotifPaused-light.png](claude-design/screenshots/NotifPaused-light.png) | [NotifPaused-dark.png](claude-design/screenshots/NotifPaused-dark.png) |

### Settings

| Screen | Light frame | Dark frame | Light screenshot | Dark screenshot |
|---|---|---|---|---|
| Settings | [Settings-light.dc.html](claude-design/design/Settings-light.dc.html) | [Settings-dark.dc.html](claude-design/design/Settings-dark.dc.html) | [Settings-light.png](claude-design/screenshots/Settings-light.png) | [Settings-dark.png](claude-design/screenshots/Settings-dark.png) |

### Empty, error and offline states

The rows follow the order of the states in the brief. "No search results" has two rows, one for each search.

| Screen | Light frame | Dark frame | Light screenshot | Dark screenshot |
|---|---|---|---|---|
| Inbox, no channels yet | [StateNoChannels-light.dc.html](claude-design/design/StateNoChannels-light.dc.html) | [StateNoChannels-dark.dc.html](claude-design/design/StateNoChannels-dark.dc.html) | [StateNoChannels-light.png](claude-design/screenshots/StateNoChannels-light.png) | [StateNoChannels-dark.png](claude-design/screenshots/StateNoChannels-dark.png) |
| Inbox, all caught up | [StateCaughtUp-light.dc.html](claude-design/design/StateCaughtUp-light.dc.html) | [StateCaughtUp-dark.dc.html](claude-design/design/StateCaughtUp-dark.dc.html) | [StateCaughtUp-light.png](claude-design/screenshots/StateCaughtUp-light.png) | [StateCaughtUp-dark.png](claude-design/screenshots/StateCaughtUp-dark.png) |
| Inbox, offline | [StateOffline-light.dc.html](claude-design/design/StateOffline-light.dc.html) | [StateOffline-dark.dc.html](claude-design/design/StateOffline-dark.dc.html) | [StateOffline-light.png](claude-design/screenshots/StateOffline-light.png) | [StateOffline-dark.png](claude-design/screenshots/StateOffline-dark.png) |
| Inbox, YouTube not loading | [StateYouTubeError-light.dc.html](claude-design/design/StateYouTubeError-light.dc.html) | [StateYouTubeError-dark.dc.html](claude-design/design/StateYouTubeError-dark.dc.html) | [StateYouTubeError-light.png](claude-design/screenshots/StateYouTubeError-light.png) | [StateYouTubeError-dark.png](claude-design/screenshots/StateYouTubeError-dark.png) |
| Inbox, downloads paused at the limit | [StatePaused-light.dc.html](claude-design/design/StatePaused-light.dc.html) | [StatePaused-dark.dc.html](claude-design/design/StatePaused-dark.dc.html) | [StatePaused-light.png](claude-design/screenshots/StatePaused-light.png) | [StatePaused-dark.png](claude-design/screenshots/StatePaused-dark.png) |
| Search YouTube, no results | [StateNoResultsYouTube-light.dc.html](claude-design/design/StateNoResultsYouTube-light.dc.html) | [StateNoResultsYouTube-dark.dc.html](claude-design/design/StateNoResultsYouTube-dark.dc.html) | [StateNoResultsYouTube-light.png](claude-design/screenshots/StateNoResultsYouTube-light.png) | [StateNoResultsYouTube-dark.png](claude-design/screenshots/StateNoResultsYouTube-dark.png) |
| Search subtitles, no results | [StateNoResultsSubtitles-light.dc.html](claude-design/design/StateNoResultsSubtitles-light.dc.html) | [StateNoResultsSubtitles-dark.dc.html](claude-design/design/StateNoResultsSubtitles-dark.dc.html) | [StateNoResultsSubtitles-light.png](claude-design/screenshots/StateNoResultsSubtitles-light.png) | [StateNoResultsSubtitles-dark.png](claude-design/screenshots/StateNoResultsSubtitles-dark.png) |

### Frames for the prototypes only

These frames are copies of "Onboarding 3, storage limit", "Onboarding 4, battery setup" and "Flow 1: battery setting done". Only their links and the title inside each file differ. They carry the onboarding prototype along the skip path, which ends at "Inbox, no channels yet" instead of the first-run inbox.

| Screen | Light frame | Dark frame | Light screenshot | Dark screenshot |
|---|---|---|---|---|
| Flow 1, skip path: storage limit | [OnboardingSkip3-light.dc.html](claude-design/design/OnboardingSkip3-light.dc.html) | [OnboardingSkip3-dark.dc.html](claude-design/design/OnboardingSkip3-dark.dc.html) | [OnboardingSkip3-light.png](claude-design/screenshots/OnboardingSkip3-light.png) | [OnboardingSkip3-dark.png](claude-design/screenshots/OnboardingSkip3-dark.png) |
| Flow 1, skip path: battery setup | [OnboardingSkip4-light.dc.html](claude-design/design/OnboardingSkip4-light.dc.html) | [OnboardingSkip4-dark.dc.html](claude-design/design/OnboardingSkip4-dark.dc.html) | [OnboardingSkip4-light.png](claude-design/screenshots/OnboardingSkip4-light.png) | [OnboardingSkip4-dark.png](claude-design/screenshots/OnboardingSkip4-dark.png) |
| Flow 1, skip path: battery setting done | [OnboardingSkip4Done-light.dc.html](claude-design/design/OnboardingSkip4Done-light.dc.html) | [OnboardingSkip4Done-dark.dc.html](claude-design/design/OnboardingSkip4Done-dark.dc.html) | [OnboardingSkip4Done-light.png](claude-design/screenshots/OnboardingSkip4Done-light.png) | [OnboardingSkip4Done-dark.png](claude-design/screenshots/OnboardingSkip4Done-dark.png) |

## Prototypes

The frames link to each other, so three prototypes click through: onboarding, inbox to player, and search inside videos. The dark frames link the same way as the light ones. Each table below lists the taps in path order, with the frame each tap opens in light and in dark.

### Onboarding

The onboarding prototype starts at [Onboarding1-light](claude-design/design/Onboarding1-light.dc.html) and [Onboarding1-dark](claude-design/design/Onboarding1-dark.dc.html). Every step after the first also has a Back button.

| On | Tap | Opens in light | Opens in dark |
|---|---|---|---|
| Onboarding 1, how Dewfall works | Get started | Onboarding2-light | Onboarding2-dark |
| Onboarding 2, import channels | Choose Takeout file | OnboardingImported-light | OnboardingImported-dark |
| Flow 1: channels imported | Continue | Onboarding3-light | Onboarding3-dark |
| Onboarding 3, storage limit | Continue | Onboarding4-light | Onboarding4-dark |
| Onboarding 4, battery setup | Open battery setting | Onboarding4Done-light | Onboarding4Done-dark |
| Onboarding 4, battery setup | Skip for now | InboxFirstRun-light | InboxFirstRun-dark |
| Flow 1: battery setting done | Finish | InboxFirstRun-light | InboxFirstRun-dark |

The skip path starts at "Skip for now" on "Onboarding 2, import channels" and ends at "Inbox, no channels yet".

| On | Tap | Opens in light | Opens in dark |
|---|---|---|---|
| Onboarding 2, import channels | Skip for now | OnboardingSkip3-light | OnboardingSkip3-dark |
| Flow 1, skip path: storage limit | Continue | OnboardingSkip4-light | OnboardingSkip4-dark |
| Flow 1, skip path: battery setup | Open battery setting | OnboardingSkip4Done-light | OnboardingSkip4Done-dark |
| Flow 1, skip path: battery setup | Skip for now | StateNoChannels-light | StateNoChannels-dark |
| Flow 1, skip path: battery setting done | Finish | StateNoChannels-light | StateNoChannels-dark |

### Inbox to player

The inbox to player prototype starts at [Main](claude-design/design/Main.dc.html) in light and [Inbox-dark](claude-design/design/Inbox-dark.dc.html) in dark.

| On | Tap | Opens in light | Opens in dark |
|---|---|---|---|
| Inbox | The video "How reading rooms were lit before electric lamps" | Player-light | Player-dark |
| Player | Full screen button | Fullscreen-light | Fullscreen-dark |
| Player full screen | Exit full screen button | Player-light | Player-dark |
| Player | Minimize player button | MiniPlayer-light | MiniPlayer-dark |
| Mini player on the inbox | The mini player | Player-light | Player-dark |

### Search inside videos

The search inside videos prototype also starts at [Main](claude-design/design/Main.dc.html) in light and [Inbox-dark](claude-design/design/Inbox-dark.dc.html) in dark.

| On | Tap | Opens in light | Opens in dark |
|---|---|---|---|
| Inbox | Search button | SearchA-sheet-light | SearchA-sheet-dark |
| Choosing a search | Search subtitles | SubSearch-light | SubSearch-dark |
| Search subtitles, before typing | The "Search saved subtitles" field | SubResults-light | SubResults-dark |
| Search subtitles, results for light | The line at 18:24 | Player-light | Player-dark |
| Search subtitles, results for light | The line at 12:30 or 47:16 | SubOffline-light | SubOffline-dark |
| Saved subtitles, offline | Back button | SubResults-light | SubResults-dark |

The lines at 12:30 and 47:16 come from "Reading the color of starlight", which the results mark as not downloaded. Both open the same frame, which shows the subtitles around 12:30.
