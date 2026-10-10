# NavigationBar

The bottom bar with Dewfall's four destinations, Inbox, Channels, Downloads and Settings, always in that order and with no count badges.

## Anatomy
- Container: `surface-container`, 80dp minimum height, full width, no shadow.
- Each item: a 24dp Material Symbols Rounded icon inside a 64 x 32dp pill indicator (`shape-full`), and a `label-medium` label under it. Labels always show.
- Selected: the pill fills with `secondary-container`, the icon switches to its filled form (FILL 1) in `on-secondary-container`, and the label turns `on-surface`. The filled glyph and the pill mark the selected tab, so it still reads when dynamic color makes the pill faint.
- Unselected: outlined icon (FILL 0) and label in `on-surface-variant`, no pill.
- Icons: `inbox`, `subscriptions`, `download_for_offline`, `settings`. Each has a filled form that looks clearly different from its outline. Plain `download` has no filled form (both forms are the same arrow), so it can't mark the selected tab and isn't used here.
- The mini-player sits directly above this bar when something is playing.

## Compose
- `NavigationBar(containerColor = surfaceContainer)` with four `NavigationBarItem`s, `alwaysShowLabel = true`, and `NavigationBarItemDefaults.colors(indicatorColor = secondaryContainer, selectedIconColor = onSecondaryContainer, selectedTextColor = onSurface, unselectedIconColor = onSurfaceVariant, unselectedTextColor = onSurfaceVariant)`.
- Pass the filled icon when `selected` and the outlined one otherwise. Set the icon's `contentDescription` to null, since the label already names it.
- The bar's default height is fixed. If a label stops fitting at a large font size, let the bar grow rather than ellipsize the label.

## Don't
- No numeric badges or dots, even for new videos. The inbox is a list you can finish, not a counter to clear.
- No fifth destination and no hidden labels.

Material 3's newer Expressive guidance also has a shorter "flexible" navigation bar (64dp tall, 56dp indicator). Dewfall uses the 80dp bar with the 64 x 32dp indicator.
