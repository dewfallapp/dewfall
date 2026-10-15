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
 * A one-time instruction to the UI, such as showing a message or opening another screen. The
 * ViewModel holds it as pending until the UI reports that it handled it. Each screen declares a
 * sealed interface that extends this one.
 */
interface MviEffect
