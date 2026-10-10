# IconButton

Round buttons with a single glyph, for actions so familiar they need no word on screen. Each one still has a spoken label.

## Variants
- Standard: glyph in `on-surface-variant`, no container. App bar actions (sync, overflow, back).
- Filled: `primary` fill, `on-primary` glyph. The mini-player's play and pause toggle.
- Tonal: `secondary-container` fill, `on-secondary-container` glyph. Toggles where on and off both matter. When on, the glyph switches to its filled form (FILL 1).
- Outlined: 1dp `outline` border, `on-surface-variant` glyph. The download action in a video row.

## Anatomy
- 40dp circle (`shape-full`) inside a 48dp touch target. Glyph 24dp.
- States: `state-hover`, `state-focus` and `state-pressed` layers in the glyph color. Keyboard and D-pad focus also draws a 2dp `primary` outline with a 2dp gap.
- Disabled: glyph in `on-surface` at `disabled-content` opacity, no state layer.
- Every icon button has a `contentDescription` that names the action, not the glyph: "Check for new videos", not "Sync". A toggle names its current state for screen readers too.

## Compose
- `IconButton`, `FilledIconButton`, `FilledTonalIconToggleButton` and `OutlinedIconButton`, each with an `Icon` whose `contentDescription` is set.
- The syncing rotation is an infinite linear rotation on the `Icon` only, skipped when the system's animation scale is 0.

## Don't
- No icon-only button for anything a person could misread. "Remove download" and "Raise limit" are text buttons.
