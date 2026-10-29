package com.heykay.snapkeymap

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.heykay.snapkeymap.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal fun tileState(config: Config, hasLogPermission: Boolean): Int = when {
    !config.isTaught || (config.needsLogAccess && !hasLogPermission) -> Tile.STATE_UNAVAILABLE
    config.enabled -> Tile.STATE_ACTIVE
    else -> Tile.STATE_INACTIVE
}

class SnapTileService : TileService() {
    private var listeningScope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        listeningScope?.cancel()
        render(SnapEngine.store.config.value)
        listeningScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
            scope.launch {
                SnapEngine.store.config.collect { render(it) }
            }
        }
    }

    override fun onStopListening() {
        listeningScope?.cancel()
        listeningScope = null
        super.onStopListening()
    }

    override fun onDestroy() {
        listeningScope?.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { handleClick() }
        else handleClick()
    }

    private fun handleClick() {
        val config = SnapEngine.store.config.value
        if (tileState(config, SnapEngine.hasLogPermission()) == Tile.STATE_UNAVAILABLE) {
            openApp()
        } else {
            SnapEngine.store.update { it.copy(enabled = !it.enabled) }
            render(SnapEngine.store.config.value)
        }
    }

    private fun render(config: Config) {
        val tile = qsTile ?: return
        tile.state = tileState(config, SnapEngine.hasLogPermission())
        tile.label = getString(R.string.snap_tile_label)
        tile.subtitle = getString(when (tile.state) {
            Tile.STATE_ACTIVE -> R.string.snap_tile_on
            Tile.STATE_INACTIVE -> R.string.snap_tile_off
            else -> R.string.snap_tile_setup
        })
        tile.updateTile()
    }

    @Suppress("DEPRECATION")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
