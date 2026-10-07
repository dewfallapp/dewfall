# Contributing to Dewfall

Thanks for your interest in Dewfall. This page explains how changes get into the project.

Dewfall is in early development, and there is no app code yet. See [docs/roadmap.md](docs/roadmap.md) for the current phase and its tasks.

Everyone who takes part follows the [Code of Conduct](CODE_OF_CONDUCT.md).

## Before you start

Read [docs/vision.md](docs/vision.md). It says what the app is and what it will never do. Dewfall has no recommendation feed, accounts, ads, analytics or tracking, and user data stays on the device. Changes that bend these rules will not be accepted unless the vision changes first.

## Branches and pull requests

- Never push to main. Work on a branch and open a pull request.
- Name the branch after the type of change, such as `feat/inbox-screen` or `docs/phase-0`.
- Keep each pull request small, with one concern.
- A pull request is merged only when every CI check passes.

## Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/). Start each message with one of these types: `feat`, `fix`, `docs`, `test`, `refactor`, `build`, `ci` or `chore`. For example:

```
feat: add storage limit setting
fix: crash on empty inbox
```

The release workflow builds the changelog from these messages, so the type matters.

## Spec first, with OpenSpec

Every new feature or change in behavior starts as an OpenSpec change, before any code is written.

1. Run `/opsx:propose` in your AI coding tool to create the change. It goes in `openspec/changes/`.
2. Open a pull request with the proposal and wait for approval.
3. Build the feature.
4. Once it is merged, archive the change so its requirements move into the living specs in `openspec/specs/`.

Write every requirement with WHEN and THEN scenarios, so each one maps onto a test.

Docs, small fixes, and refactors that do not change behavior do not need an OpenSpec change.

## Decisions go in ADRs

A decision that would be hard to reverse gets an architecture decision record (ADR) in [docs/adr/](docs/adr/). Copy [docs/adr/0000-template.md](docs/adr/0000-template.md) and give the new file the next number.

When the structure of the app changes, update [docs/architecture.md](docs/architecture.md) in the same pull request.

## Tests with every change

- New logic ships with tests in the same pull request.
- Unit tests never touch the network.
- Screens get screenshot tests in light, dark and large font.
- Never delete, skip or weaken a test to make it pass. If a test looks wrong, say why in the pull request.

See [docs/testing.md](docs/testing.md) for the test layers and CI workflows.

## Dependencies

Ask in an issue before adding a dependency. Every dependency must be compatible with GPLv3. Apache 2.0, MIT, BSD and LGPL are fine. No dependency may send data anywhere the user did not choose.

## Things never to commit

Secrets, keystores, signing passwords and `local.properties`.

## Writing docs

Write docs in plain English with short sentences, in Markdown, with Mermaid for diagrams. No emoji in docs, code comments or commit messages.

## Reporting security problems

Do not open a public issue. Follow [SECURITY.md](SECURITY.md).
