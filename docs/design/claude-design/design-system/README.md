Dewfall is an Android app that works like a podcast app for YouTube. New videos from channels the person chose download overnight while the phone charges on Wi-Fi, then play offline. It has no recommendation feed. The interface is quiet on purpose: the inbox is a list you can finish, and an empty inbox means done.

Build with Jetpack Compose and Material 3. Every color is a Material 3 role, every text style is a step of the Material 3 type scale, and both map one to one onto `MaterialTheme`. The Compose theme section has the code.

## Content fundamentals

- Write in sentence case with plain verbs. No exclamation marks, no urgency, no "Don't miss", no streaks.
- Buttons say what happens: "Download now", "Manage downloads", "Raise limit", "Mark as watched". Keep the same word through a flow: "Remove download" leads to "Download removed".
- An empty inbox reports that it's done and when the next downloads run: "You're all caught up. New videos download tonight while your phone charges." Never suggest something else to watch.
- Errors say what happened and what to do next, without apologizing: "YouTube isn't loading. Your downloads still play offline."
- Numbers: storage as "6.1 GB / 10 GB", durations as "42m" and "1h 14m", dates as "2 days ago" or "Yesterday", counts in words: "2 of 3 downloaded".
- Address the person as "you" sparingly. The app never talks about itself as "we".
- Every design uses made-up channels, titles and plain placeholder thumbnails, such as Larkspur Lectures, Tidewater Woodshop and Kettle and Crumb. Never real channels, creators, faces, YouTube thumbnails, or the YouTube logo or wordmark.

## Two searches

Dewfall has two searches and the person must always know which one they're in.

- Search YouTube finds videos to stream and channels to subscribe to. It needs the internet. Field placeholder: "Search YouTube". Glyph: `search`.
- Search subtitles searches the saved subtitles of watched videos, on the phone, offline. Field placeholder: "Search saved subtitles". Glyph: `subtitles`. The top app bar title says "Search subtitles" and its context line says how many videos it covers: "24 watched videos on this phone".
- Results differ too. YouTube results are video rows and channel headers. Subtitle results show the video, the matching line in `body-large` with its time ("18:24"), and open the player at that moment. Offline, they show the matching line with a few lines around it.
- Tell them apart by title, placeholder and glyph, never by color. Both screens use the SearchField component under the top app bar.
- Reach both from the search icon button in the top app bar of Inbox, Channels and Downloads (not Settings). It opens a bottom sheet with two choices, "Search YouTube" (needs the internet) and "Search subtitles" (works offline), so the person picks the search before typing.

## Color

- Use Material 3 roles by name: `primary`, `on-primary`, `primary-container`, `surface`, `on-surface`, `surface-container-low` and so on. In Compose read them from `MaterialTheme.colorScheme`. Never put a hex value in a component.
- Dynamic color is on by default on Android 12 and later and swaps the whole palette for colors from the wallpaper. So color never carries meaning by itself. Every state pairs a color with a glyph or words:
  - downloaded: `check` glyph
  - download available: `download` glyph
  - downloading: progress ring with a `pause` glyph and "Downloading, 42%"
  - queued: `schedule` glyph and "Queued for tonight"
  - selected chip: `check` glyph
  - selected tab: filled glyph and the pill indicator
  - storage limit reached: `pause_circle` glyph and a sentence, in the inbox banner and on the Downloads meter
- Text pairs: `on-surface` and `on-surface-variant` on `surface` and every `surface-container` step; `on-primary` on `primary`; `on-X-container` on `X-container`. Every pair is 4.5:1 or better in both schemes. The light container pairs sit just above the line (4.55 to 4.58:1), so never lighten them.
- Borders that mark a control (outlined buttons, unselected chips, outlined icon buttons) use `outline`, which is 3:1 or better on every surface in both schemes. `outline-variant` is for decorative hairlines only.
- `error` colors only the glyph of a failure message, such as YouTube not loading or a download that failed. The message itself sits in the neutral Banner on `surface-container-high`, never in an error-colored block: nothing is lost, it isn't the person's fault, and it can appear whenever YouTube changes something. A full storage limit the person set isn't a failure at all and uses the neutral `pause_circle` glyph.
- The fullscreen player always uses the dark scheme, in light mode too. People watch fullscreen video in dim rooms, and a pale frame around the video glares.
- `scrim` is black in both schemes. It fills the pillars around fullscreen video, and sits behind bottom sheets and dialogs at `scrim-modal` (32%). Never put text on it.
- The fixed roles (`primary-fixed` and the rest) keep the same tone in light and dark. No component uses them yet.

### Elevation

Depth comes from tonal surface steps. Dewfall has no drop shadows.

| Level | Role | Used for |
|---|---|---|
| 0 | `surface` | Every screen's canvas, the resting top app bar |
| 1 | `surface-container-low` | Channel groups, the storage meter |
| 2 | `surface-container` | The scrolled top app bar, the navigation bar |
| 2 | `surface-container-high` | The mini-player, banners, badges |
| 3 | `surface-container-highest` + 1dp `outline-variant` edge | Dialogs and bottom sheets |

## Typography

- Manrope for all text, from `fonts/Manrope-Variable.woff2` (weight axis 200 to 800, SIL Open Font License, license in `licenses/`). Dewfall uses weights 400, 500 and 600.
- Name styles by the Material 3 type scale: `display-large` down to `label-small`.
- `title-large` sets "Dewfall" in the top app bar and the video title on the player. `title-medium` sets video titles in rows, lists and the mini-player. `title-small` sets channel names. `body-large` sets descriptions and subtitle excerpts. `body-small` sets dates and counts. Label styles set numbers and controls: the storage amount, durations, badges, buttons, chips and tab labels.
- Display and headline styles are rare: onboarding, empty states and dialog titles.
- Text wraps and is never cut off. No `maxLines`, no `TextOverflow.Ellipsis`, no fixed heights around text, no "…". Check every screen at the largest system font size.

## Layout and spacing

- Portrait phone layout on a 4-column grid with 16dp `margin` and 16dp `gutter`. The fullscreen player is the only landscape screen.
- Spacing follows an 8dp rhythm: `space-sm` 8dp, `space-md` 16dp, `space-lg` 24dp, `space-xl` 32dp. `space-xs` (4dp) is only for padding inside badges and chips.
- Inbox order, top to bottom: top app bar; a Banner, only while something needs saying (offline, YouTube not loading, or downloads paused at the storage limit); channel groups with `space-lg` between them; the status pill for tonight's downloads at the end of the list; then the mini-player and navigation bar. The inbox has no storage meter.
- Downloads order, top to bottom: top app bar, the storage meter, then the list of downloads.
- Touch targets are at least 48 x 48dp, even when the visible control is smaller.

## Shape

| Token | dp | Used for |
|---|---|---|
| `shape-extra-small` | 4 | Duration tags, badges |
| `shape-small` | 8 | Thumbnails, segmented buttons |
| `shape-medium` | 12 | Video row state layer, chips, the storage meter |
| `shape-large` | 16 | Channel groups, bottom sheets |
| `shape-extra-large` | 24 | Dialogs |
| `shape-full` | pill | Buttons, icon buttons, channel emblems, status pills, the navigation indicator, the storage bar |

## States and motion

- Interactive elements paint a state layer of their content color: `state-hover` 8%, `state-focus` 10%, `state-pressed` 10%, `state-dragged` 16%.
- Keyboard and D-pad focus also draws a solid 2dp `primary` outline with a 2dp gap. It is 3:1 or better on every surface in both schemes.
- Disabled: content in `on-surface` at `disabled-content` (38%), fills in `on-surface` at `disabled-container` (12%).
- Dewfall's one piece of ambient motion is the sync glyph turning slowly while it checks for new videos: linear, continuous, no bounce. With reduced motion on it stays still, and the words under "Dewfall" still say "Checking for new videos". Other motion only answers a tap: expanding, opening, confirming.

## Iconography

- Material Symbols Rounded at 24dp, weight 400, grade 0, optical size 24. Outlined (FILL 0) by default. Filled (FILL 1) only for the selected navigation destination and toggles that are on. Every navigation glyph needs a filled form that looks clearly different, because the fill marks the selected tab under dynamic color. That's why Downloads uses `download_for_offline`, not `download`.
- Every glyph sits next to a word or has a content description that names the action ("Check for new videos", not "Sync").
- Glyphs in use: `inbox`, `subscriptions`, `download_for_offline`, `settings`, `download`, `sync`, `more_vert`, `arrow_back`, `check`, `schedule`, `pause`, `pause_circle`, `play_arrow`, `search`, `subtitles`, `keep`, `done_all`, `visibility`, `close`, `add`, `tune`, `download_done`, `cloud_off`, `wifi_off`, `battery_charging_full`, `storage`, `error`, `info`, `fullscreen`, `fullscreen_exit`, `keyboard_arrow_down`, `chevron_right`.
- `fonts/MaterialSymbolsRounded-Dewfall.woff2` is a subset of the real Material Symbols Rounded font (Apache License 2.0) with only those glyphs, used by the previews here. In the app, import the same names as vector drawables.
- No logo this round. The app icon is out of scope, so "Dewfall" is set in `title-large` with no mark beside it.

## Not in Dewfall

The early screenshots include things Dewfall won't have. Don't build them:
- a profile button or avatar
- a playback speed control
- an "Audio mode" chip or a video quality chip
- a transcript panel with its own search on the player
- "Copy text" and "Save highlight" buttons
- titles cut off with "…"

Also out of this round: channel suggestions, silence skipping, an audio-only default, a sleep timer, blocking, time limits, notes and add-ons like SponsorBlock. Overnight downloads always wait for Wi-Fi and charging, so there's no mobile data option anywhere.
