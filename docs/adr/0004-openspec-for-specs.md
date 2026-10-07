# 0004. Use OpenSpec for specs

## Status

Accepted

## Context

Dewfall needs a written record of what the app does, separate from the record of how it is built. The architecture document and the ADRs cover how it is built and why. Like every other doc, it should live in the repository as Markdown and change in the same pull request as the code it describes.

OpenSpec keeps specs as plain Markdown in the repository, next to the code. It works with Claude Code through slash commands. Its one-change-per-feature model fits small pull requests, and its living specs give a current picture of how the app behaves. It is free and MIT-licensed.

The alternatives:

- A hand-written spec per phase has no way to keep a current picture of the whole app.
- GitHub's Spec Kit is heavier than a solo project needs.
- No specs at all leaves AI agents working from vague prompts.

## Decision

Dewfall uses OpenSpec for specs.

- Each feature is one OpenSpec change, started with /opsx:propose. Its proposal, requirement changes and task list are reviewed before any code is written.
- Once the feature is merged, the change is archived, so its requirements move into the living specs.
- Living specs are grouped by area of the app: player, subscriptions, inbox, downloads, background-sync, cleanup, transcript-search and data-export.
- Every requirement is written with WHEN and THEN scenarios, so each one maps onto a test.

OpenSpec records what the app does. The architecture document and ADRs record how it is built and why.

## Consequences

- OpenSpec is an extra tool. It installs through npm, so contributors need Node.js alongside the Android tools.
- Specs can drift away from the app, because nothing forces anyone to update a spec when the code changes. The pull request template planned for Phase 2 adds the line "OpenSpec change archived, or not needed" to catch this.
- The OpenSpec workflow was rebuilt recently, so older tutorials use different command names. Trust the current OpenSpec README over blog posts.
