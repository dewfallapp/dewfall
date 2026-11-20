package io.github.dewfallapp.core.playback

import androidx.media3.common.Player

/** Which commands a controller that connects to the session gets. */
internal enum class ControllerKind {
    /**
     * Media3's media notification controller. Media3 sends media buttons as this controller,
     * answers the system UI's connection with its commands, and advertises them in Android's media
     * session, so it gets only [PLAYBACK_COMMANDS].
     */
    MEDIA_NOTIFICATION,

    /** The screens' own MediaController, which keeps the default commands to set items. */
    OWN,

    /**
     * The Bluetooth service, apps with notification access, and a legacy controller from the app's
     * own package, which Media3 uses for media buttons before its media notification controller
     * connects. They get only [PLAYBACK_COMMANDS].
     */
    TRUSTED,

    /** Any other app. */
    REJECTED,
}

/**
 * The kind of a controller: whether it [isMediaNotificationController], comes from the app's own
 * package ([isOwnPackage]), is a legacy controller ([isLegacy]), and [isTrusted], as Media3's
 * ControllerInfo says. The media notification controller comes first, although its package is the
 * app's own. See docs/architecture.md#the-player.
 */
internal fun controllerKind(
    isMediaNotificationController: Boolean,
    isOwnPackage: Boolean,
    isLegacy: Boolean,
    isTrusted: Boolean,
): ControllerKind =
    when {
        isMediaNotificationController -> ControllerKind.MEDIA_NOTIFICATION
        isOwnPackage && !isLegacy -> ControllerKind.OWN
        isOwnPackage || isTrusted -> ControllerKind.TRUSTED
        else -> ControllerKind.REJECTED
    }

/**
 * The only commands that the media notification controller and trusted controllers get: play and
 * pause, prepare, stop, seeking within the item, and reading the current item, the timeline and the
 * metadata. None of them sets items, changes tracks, speed or volume, skips, or seeks by steps.
 * Media3 needs the timeline to know the notification has an item.
 */
internal val PLAYBACK_COMMANDS: List<Int> =
    listOf(
        Player.COMMAND_PLAY_PAUSE,
        Player.COMMAND_PREPARE,
        Player.COMMAND_STOP,
        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
        Player.COMMAND_GET_TIMELINE,
        Player.COMMAND_GET_METADATA,
    )

/**
 * The commands that the session's player doesn't offer. On Android 10 to 12, Media3's notification
 * builds its previous and next buttons from the session's player's commands, so without these it
 * shows none. A single video has no previous or next one, and previous would restart it.
 */
internal val SKIP_COMMANDS: List<Int> =
    listOf(
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
    )

/**
 * Whether the service cancels the media notification when `onGetSession` is asked by a caller that
 * [isMediaButton], and its player [hasItem]. Only a media button from a notification that an ended
 * process left asks it, and while the player has no item, this process shows no notification of its
 * own, so one with that ID can only be the one left behind.
 */
internal fun cancelsLeftoverNotification(isMediaButton: Boolean, hasItem: Boolean): Boolean =
    isMediaButton && !hasItem
