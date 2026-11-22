package io.github.dewfallapp.core.data.database

import javax.inject.Qualifier

/**
 * The app-wide CoroutineScope that the repositories run their writes in, so a write that started
 * finishes even when its caller's coroutine is cancelled, such as when the user leaves a screen.
 * Google's coroutines best practices show this pattern in "Creating coroutines in the business and
 * data layer". See docs/architecture.md#the-database-repositories.
 */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope
