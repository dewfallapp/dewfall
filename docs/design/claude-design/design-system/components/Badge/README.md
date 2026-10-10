# Badge

Small, non-interactive marks of one or two words that sit in a video's meta line or on its thumbnail.

## Kinds
- Duration tag: on the thumbnail's lower-right corner. `label-small` in `on-surface` on `surface` at `duration-scrim` opacity. "42m", "1h 14m".
- CC: the video's subtitles are saved and searchable with Search subtitles. `label-small` in `on-surface-variant` on `surface-container-high`. Announce it as "Searchable subtitles".
- Kept: the person chose to keep this video from automatic cleanup. `keep` glyph plus the word.
- Watched: `done_all` glyph plus the word. Shown in Downloads, where watched videos wait for cleanup.

## Anatomy
- `shape-extra-small` corners, `space-xs` horizontal padding, 16dp tall at the default font size. Glyphs are 14dp with a 2dp gap.
- Every badge has words. A badge is never a colored dot.

## Compose
- A `Surface(shape = MaterialTheme.shapes.extraSmall, color = surfaceContainerHigh)` around a `Text` in `labelSmall`, with `contentDescription` set where the visible word is an abbreviation (CC).
- Material 3's `Badge` composable is for notification counts. Dewfall has no counts, so don't use it.

## Don't
- No count badges, "New" badges or dots.
- No ALL CAPS words other than the CC abbreviation.
