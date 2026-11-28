# Proposal

## Why

[#281](https://github.com/dewfallapp/dewfall/issues/281) makes a swipe from the recent apps stop pausing a download that runs in the background on Android 11 to 13. Dewfall tells that swipe from a Stop or Force stop by the record Android keeps of how the app's process ended: stock Android describes a swipe's kill as "remove task", with a background importance. The requirement "Downloads run one at a time without any screen" says that on a phone whose maker ends a running download's process on a swipe, the download SHALL count as paused. A maker's phone that writes stock Android's exact record looks like stock Android, so its download goes on. The maintainer accepted that on November 28, 2026 (answer 1 below), and asked for the spec to say so (answer 2).

## What Changes

- The requirement's maker sentence gets an exception: a maker's phone that records the swipe exactly as stock Android does goes on, as on stock Android. Any other record of a swipe's kill still counts as paused, as after a Stop.
- The requirement gets a scenario for the case #281 fixed: a swipe while a download runs in the background after a restart.
- No other behavior changes. The code that does this is in #281's pull request, #287.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `downloads`: "Downloads run one at a time without any screen" amends its maker sentence and gets one scenario.

## Impact

- The downloads spec only. The code, its tests, ADR 0010's note for #281, docs/architecture.md and docs/testing.md are in pull request #287, which archives this change as its last commit.
- No new dependency, permission, network call or schema change.

## The maintainer's answers

1. Given on November 28, 2026, in the coordinating session's question dialog. Question: "#281: on a phone whose maker kills a background download on a swipe but writes stock Android's exact exit record ('remove task', background importance), Dewfall can't tell it from stock Android, so the download goes on instead of counting as paused. Accept that the stock rule wins there?" Answer: "Yes, stock rule wins (Recommended)"
2. Given on November 28, 2026, in the coordinating session's question dialog. Question: "#281's reviewer asks (non-blocking): your accepted "stock rule wins" exception (maker phones that write stock Android's exact swipe record keep downloading) is recorded only in ADR 0010's note. Should a small OpenSpec change also amend the downloads spec's maker sentence, which today says such a download SHALL count as paused?" Answer: "Amend the spec in #281 (Recommended)"
3. Given on November 28, 2026, in the coordinating session's question dialog. Question: "#281: approve the OpenSpec change swipe-maker-sentence? It changes one sentence of the downloads spec to: "on a phone whose maker ends a running download's process on that swipe, the download SHALL count as paused, as after a Stop, unless the phone records that end exactly as stock Android records a swipe, which the app can't tell apart from stock Android, so the download SHALL go on as it does there." It also adds a scenario: on stock Android 12 or 13, a download going on in the background after a restart, then the user opens the app, leaves it and swipes it away: the download goes on and never says "Paused"." Answer: "Approve (Recommended)"
