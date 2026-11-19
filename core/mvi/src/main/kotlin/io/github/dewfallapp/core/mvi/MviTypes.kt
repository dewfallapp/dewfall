package io.github.dewfallapp.core.mvi

/**
 * A screen's state. Each screen declares one immutable class for it, usually a data class with val
 * properties and read-only collections. After the initial state, only the screen's reducer makes
 * new ones. See docs/architecture.md#the-mvi-contract.
 */
interface MviState

/**
 * Something that happens to a screen: what the user does, or the result of work that the ViewModel
 * ran, such as a load that finished. Each screen declares a sealed interface that extends this one,
 * and names results in the past tense, such as Loaded.
 */
interface MviIntent

/**
 * A one-time instruction to the UI that starts in the ViewModel, such as a message after a load, or
 * a screen that opens only after work finishes. The ViewModel holds it as pending until the UI
 * reports that it handled it. Each screen declares a sealed interface that extends this one. An
 * action that starts in the UI and changes no state, such as opening a screen on a tap, needs no
 * effect. See ADR 0002.
 */
interface MviEffect
