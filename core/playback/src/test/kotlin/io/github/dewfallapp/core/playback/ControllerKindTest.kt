package io.github.dewfallapp.core.playback

import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControllerKindTest {
    @Test
    fun `every controller gets its kind, and the media notification controller comes first`() {
        for (legacy in listOf(false, true)) {
            for (trusted in listOf(false, true)) {
                for (own in listOf(false, true)) {
                    // Media3's own controller, from the app's package, never counts as OWN.
                    assertEquals(
                        ControllerKind.MEDIA_NOTIFICATION,
                        controllerKind(
                            isMediaNotificationController = true,
                            isOwnPackage = own,
                            isLegacy = legacy,
                            isTrusted = trusted,
                        ),
                    )
                }
                // The screens' controller.
                assertEquals(
                    ControllerKind.OWN,
                    controllerKind(
                        isMediaNotificationController = false,
                        isOwnPackage = true,
                        isLegacy = false,
                        isTrusted = trusted,
                    ),
                )
                // A legacy controller of the app's own, as Media3 sends media buttons before its
                // media notification controller connects.
                assertEquals(
                    ControllerKind.TRUSTED,
                    controllerKind(
                        isMediaNotificationController = false,
                        isOwnPackage = true,
                        isLegacy = true,
                        isTrusted = trusted,
                    ),
                )
            }
            // The Bluetooth service, or an app with notification access.
            assertEquals(
                ControllerKind.TRUSTED,
                controllerKind(
                    isMediaNotificationController = false,
                    isOwnPackage = false,
                    isLegacy = legacy,
                    isTrusted = true,
                ),
            )
            assertEquals(
                ControllerKind.REJECTED,
                controllerKind(
                    isMediaNotificationController = false,
                    isOwnPackage = false,
                    isLegacy = legacy,
                    isTrusted = false,
                ),
            )
        }
    }

    @Test
    fun `the playback commands play, pause, seek within the video, stop and read`() {
        assertEquals(
            setOf(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
            ),
            PLAYBACK_COMMANDS.toSet(),
        )
        val never =
            listOf(
                Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
                Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS,
                Player.COMMAND_SET_SPEED_AND_PITCH,
                Player.COMMAND_SET_VOLUME,
                Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
            )
        for (command in never) {
            assertFalse(command in PLAYBACK_COMMANDS)
        }
    }

    @Test
    fun `the session's player drops the skip commands and keeps seeking by steps`() {
        assertEquals(
            setOf(
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            ),
            SKIP_COMMANDS.toSet(),
        )
        assertFalse(Player.COMMAND_SEEK_BACK in SKIP_COMMANDS)
        assertFalse(Player.COMMAND_SEEK_FORWARD in SKIP_COMMANDS)
    }

    @Test
    fun `only a media button with no item cancels the leftover notification`() {
        assertTrue(cancelsLeftoverNotification(isMediaButton = true, hasItem = false))
        // This process's own notification, under the same ID, replaced the leftover one.
        assertFalse(cancelsLeftoverNotification(isMediaButton = true, hasItem = true))
        assertFalse(cancelsLeftoverNotification(isMediaButton = false, hasItem = false))
    }
}
