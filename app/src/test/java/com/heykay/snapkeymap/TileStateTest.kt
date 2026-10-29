package com.heykay.snapkeymap

import android.service.quicksettings.Tile
import org.junit.Assert.assertEquals
import org.junit.Test

class TileStateTest {
    @Test
    fun untaughtKeyIsUnavailableRegardlessOfPermissionOrEnabled() {
        for (config in listOf(Config(), Config(logSignature = KeySignature("tag", "message")))) {
            for (permission in listOf(false, true)) {
                for (enabled in listOf(false, true)) {
                    assertEquals(
                        "config=$config permission=$permission enabled=$enabled",
                        Tile.STATE_UNAVAILABLE,
                        tileState(config.copy(enabled = enabled), permission),
                    )
                }
            }
        }
    }

    @Test
    fun taughtLogKeyRequiresPermissionEvenWhenDisabled() {
        val config = Config(logSignature = KeySignature("tag", "message", "1000"))
        for (enabled in listOf(false, true)) {
            assertEquals(Tile.STATE_UNAVAILABLE, tileState(config.copy(enabled = enabled), false))
        }
        assertEquals(Tile.STATE_ACTIVE, tileState(config.copy(enabled = true), true))
        assertEquals(Tile.STATE_INACTIVE, tileState(config.copy(enabled = false), true))
    }

    @Test
    fun taughtKeyEventMirrorsEnabledWithoutLogPermission() {
        val config = Config(keyEvent = LearnedKey(100, 0))
        for (permission in listOf(false, true)) {
            assertEquals(Tile.STATE_ACTIVE, tileState(config.copy(enabled = true), permission))
            assertEquals(Tile.STATE_INACTIVE, tileState(config.copy(enabled = false), permission))
        }
    }
}
