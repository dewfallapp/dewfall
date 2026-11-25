package io.github.dewfallapp.core.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.dewfallapp.core.model.DownloadProgress
import io.github.dewfallapp.core.playback.SessionActivity
import javax.inject.Inject

/** The download notification's ID. The media notification uses 1001. */
internal const val DOWNLOAD_NOTIFICATION_ID = 2001

/** The download notification channel's ID. */
private const val CHANNEL_ID = "downloads"

/** The progress bar's maximum, a percentage. */
private const val WHOLE = 100

/**
 * Builds the download notification: "Downloading a video" with the percentage, once the size is
 * known, and a progress bar, in its own low-importance channel, "Downloads", so it makes no sound.
 * It never names the video, so the lock screen and apps that read notifications learn only that a
 * download runs. Tapping it opens the app, as the media notification does. The app doesn't ask for
 * the notification permission, so without it Android shows the download only in its Task Manager.
 * See ADR 0010.
 */
class DownloadNotifications
@Inject
internal constructor(
    @ApplicationContext private val context: Context,
    private val sessionActivity: SessionActivity,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    /** The notification for a download at [progress], or at its start when null. */
    fun running(progress: DownloadProgress?): Notification {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val percent = progress?.percent
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.download_notification_title))
            .setContentText(
                percent?.let { context.getString(R.string.download_notification_percent, it) },
            )
            .setProgress(WHOLE, percent ?: 0, percent == null)
            .setContentIntent(sessionActivity.pendingIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .build()
    }
}
