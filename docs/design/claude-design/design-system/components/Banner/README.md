# Banner

One short notice at the top of the inbox when something affects what can play or what downloads tonight: the phone is offline, YouTube isn't loading, or downloads are paused at the storage limit.

## Anatomy
- Container: `surface-container-high`, `shape-medium`, `space-md` padding, `space-sm` between parts. It sits between the top app bar and the first channel group, inside the 16dp screen margins.
- Message row: a 20dp glyph, then one or two sentences in `body-medium`, `on-surface`, saying what happened and what still works.
- Actions, when there are any: right-aligned under the message, wrapping onto a new line at large font sizes.

## Kinds
| Kind | Glyph | Words | Actions |
|---|---|---|---|
| Offline | `wifi_off` in `on-surface-variant` | "You're offline. Downloaded videos still play." | none |
| YouTube not loading | `error` in `error` | "Couldn't check for new videos because YouTube isn't loading. Your downloads still play." | Text button "Try again" |
| Storage limit | `pause_circle` in `on-surface-variant` | "Downloads paused at your 10 GB limit. 3 new videos are waiting for space." | Text button "Raise limit", tonal button "Manage downloads" |

## Rules
- The glyph and the words carry the kind. The only color difference is the `error` glyph, and that's a second signal.
- Never an error-colored block. When YouTube isn't loading nothing is lost, it isn't the person's fault, and it can happen whenever YouTube changes something, so the banner stays as calm as the others.
- One banner at a time. If more than one applies, show offline first, then YouTube not loading, then the storage limit.
- No close button. A banner goes away when its cause clears.
- Not for tips, good news or anything to buy.

## Compose
- A `Column` with `Modifier.clip(MaterialTheme.shapes.medium).background(surfaceContainerHigh).padding(16.dp)`, a `Row` for the glyph and text, and a `FlowRow` with `Arrangement.End` for the actions.
- Mark it `semantics { liveRegion = LiveRegionMode.Polite }` so the message is read when it appears.
