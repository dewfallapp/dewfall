package io.github.dewfallapp.playback

import android.app.PendingIntent
import android.content.Intent
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.dewfallapp.MainActivity
import io.github.dewfallapp.core.playback.SessionActivity

/**
 * Provides the [SessionActivity] that opens the app from the media notification. Its intent names
 * MainActivity with the launcher's action, category and new-task flag, so it brings the app's task
 * to the front as it was, as the launcher does. Debug builds have a second launcher activity, the
 * YouTube layer's debug screen, so the package's launch intent wouldn't do. See
 * docs/architecture.md#the-player.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {
    @Provides
    fun provideSessionActivity(): SessionActivity = SessionActivity { context ->
        val intent =
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        PendingIntent.getActivity(
            context,
            /* requestCode= */ 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
