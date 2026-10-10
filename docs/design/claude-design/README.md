# Claude Design export

This folder holds the Phase 1 export from Claude Design. It has every first-version screen in light and dark, the three clickable prototypes, and the Dewfall design system.

## Where the files came from

- [design/](design/) comes from the Claude Design canvas at https://claude.ai/artifact/CTvwr35JtbFRkm8csANfmo.
- [design-system/](design-system/) comes from the Dewfall design system at https://claude.ai/artifact/7HvvbWCoA7kXyKRXWktgZ9.
- Both are private to the maintainer, so the links only open for them. Both were exported on 2026-10-10.
- [screenshots/](screenshots/) was rendered from the frame files with headless Chrome and the Manrope font. The screenshots of the six dark onboarding frames added later, OnboardingImported-dark, Onboarding4Done-dark, InboxFirstRun-dark, OnboardingSkip3-dark, OnboardingSkip4-dark and OnboardingSkip4Done-dark, come from later runs, and their text looks slightly heavier than in the others.

The exported files are unchanged. The only files added are this README and the Material Symbols license, described under [Licenses](#licenses).

## What is in each folder

- **design/** holds the canvas: 64 frames, one .dc.html file each. canvas.json lists each frame's title and its position on the canvas. ds/dewfall/tokens.json is the canvas's copy of the design system tokens.
- **design-system/** holds README.md, tokens.json, compose-theme.md, design-system.json and licenses/. Its components/ folder has 12 components, each with a README.md and a preview.html. components/ also holds Cover/preview.html, the design system's cover card, and bundle.css, the styles the previews share.
- **screenshots/** holds a PNG of every frame at 2x, named after its frame.

Every frame exists in light and dark, so the 64 frames are 32 pairs. Fullscreen-light uses the dark scheme on purpose. The design system README explains why.

## How to view them

Open any frame in a browser. Frames link to each other, so the three prototype flows click through.

- Onboarding starts at [design/Onboarding1-light.dc.html](design/Onboarding1-light.dc.html).
- Inbox to player starts at [design/Main.dc.html](design/Main.dc.html), the light inbox.
- Search inside videos also starts at [design/Main.dc.html](design/Main.dc.html).

The dark frames link the same way, so the dark flows match the light ones. They start at [design/Onboarding1-dark.dc.html](design/Onboarding1-dark.dc.html) and [design/Inbox-dark.dc.html](design/Inbox-dark.dc.html).

Frames load Manrope from Google Fonts. Each frame also references ./support.js from Claude Design's runtime, which isn't included. The frames render without it.

The component previews don't link bundle.css or the tokens. Opened on their own, they show without Dewfall's styles or icons.

## Which file to follow

The [design system README](design-system/README.md) is the reference for colors, type, components and the Compose theme.

Where anything here disagrees with [docs/design/brief.md](../brief.md), the brief wins until the brief is updated.

## Licenses

- The icons in the frames and previews are Material Symbols, under the Apache License 2.0. The frames draw them, and the previews name them for an icon font that isn't included. The license text is in [design-system/licenses/MaterialSymbols-Apache-2.0.txt](design-system/licenses/MaterialSymbols-Apache-2.0.txt), copied from the LICENSE file of [github.com/google/material-design-icons](https://github.com/google/material-design-icons).
- The font files are not included, although the design system README mentions a fonts/ folder.
- The Manrope license stays in [design-system/licenses/Manrope-OFL.txt](design-system/licenses/Manrope-OFL.txt) as a reference.
