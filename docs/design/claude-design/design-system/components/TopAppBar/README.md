# TopAppBar

The bar at the top of every screen: "Dewfall" and the screen name on root screens, a back button and the screen title on detail screens.

## Anatomy
- Root screens (Inbox, Channels, Downloads, Settings): "Dewfall" in `title-large`, `on-surface`, with the screen name under it in `body-medium`, `on-surface-variant`. Left-aligned.
- Detail screens: a back icon button, then the screen title in `title-large` and an optional context line in `body-medium`. On the two search screens the title says which search this is: "Search YouTube" or "Search subtitles".
- Trailing actions, in this order: the search icon button (Inbox, Channels and Downloads), the sync icon button (Inbox only), then the overflow menu. Settings has no search button. Icons in `on-surface-variant`.
- Container: `surface` at rest, `surface-container` once content scrolls under it. No shadow.
- Height: 64dp minimum, never fixed. Padding 16dp at the start on root screens, 4dp at each end around icon buttons.

## Sync
- While checking for new videos, the sync icon turns in a slow, continuous rotation (1.6s per turn, linear, no bounce) and the line under "Dewfall" reads "Checking for new videos". The words carry the state. The rotation is a second signal.
- With reduced motion on, the icon stays still and the words still change.
- The sync button can't be tapped while a sync runs.

## Search
- The search icon button (`search` glyph, labelled "Search") opens a bottom sheet that asks which search: "Search YouTube" or "Search subtitles", each with a one-line description. It never opens a search field inside the bar.
- The search screens are detail screens: a back button, the search's name as the title, and the search field below the bar.

## Compose
- `TopAppBar` with a `Column` title, `actions` for the icon buttons, and `TopAppBarDefaults.topAppBarColors(containerColor = surface, scrolledContainerColor = surfaceContainer)` with a pinned scroll behavior.
- Compose's `TopAppBar` has a fixed height. The two-line title outgrows it at large font sizes, so either raise the bar's height with the font scale or build the bar as a `Row` with a 64dp minimum height. The title wraps. It never clips or ellipsizes.

## Don't
- No profile button, avatar or logo in the bar.
- No search field in the bar. Search is an icon button that opens the choice sheet.
- No count badges on actions.
