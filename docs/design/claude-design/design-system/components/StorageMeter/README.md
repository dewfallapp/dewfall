# StorageMeter

The module at the top of the Downloads screen that shows how much of the offline storage limit is used, plus the banner form the inbox shows only when overnight downloads stop at that limit.

## Where it goes
- Downloads screen: always, directly under the top app bar, above the list of downloads.
- Inbox: never as a meter. The inbox shows storage only as the storage-limit banner, and only while overnight downloads are paused at the limit. The banner sits between the top app bar and the first channel group and goes away once there is room again.

## Meter (Downloads screen)
- Container: `surface-container-low`, `shape-medium` corners, `space-md` padding, `space-sm` between parts.
- Label row: "Offline storage" in `label-medium`, `on-surface-variant`, and the amount in `label-large`, `on-surface`, formatted "6.1 GB / 10 GB". The row wraps if both don't fit.
- Bar: 6dp tall, `shape-full` caps. Track `surface-container-highest`, indicator `primary`. The amount in words sits right above it, so the bar never has to be read alone.
- At the limit, the meter adds the message and a text button "Raise limit". It has no "Manage downloads" button here, because the person is already on Downloads.

## Storage-limit banner (Inbox)
- The inbox uses the Banner component's storage-limit kind: a `pause_circle` glyph, "Downloads paused at your 10 GB limit. 3 new videos are waiting for space.", and the actions "Raise limit" (text) and "Manage downloads" (tonal), which opens Downloads.
- No close button. It goes away on its own when there is room.

## Color
- The bar keeps `primary`, and the banner stays in the normal palette. Reaching a limit the person set is not an error, so no `error` color. The words and the pause glyph carry it.

## Compose
- Meter: `LinearProgressIndicator(progress = { used / limit }, modifier = Modifier.fillMaxWidth().height(6.dp), color = primary, trackColor = surfaceContainerHighest, strokeCap = StrokeCap.Round)`. Newer Material 3 versions draw a gap and a stop dot on this indicator by default. Turn both off to match this bar.
- Give the indicator `semantics { stateDescription = "6.1 of 10 GB used" }`.
- Inbox banner: see the Banner component.

## Don't
- No storage meter on the inbox.
- No percentages without the amount, no red bar, no "Upgrade" or upsell language.
