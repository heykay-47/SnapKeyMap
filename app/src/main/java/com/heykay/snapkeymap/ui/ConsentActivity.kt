package com.heykay.snapkeymap.ui

import android.app.Activity
import com.heykay.snapkeymap.LogAccess
import com.heykay.snapkeymap.SnapEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ConsentActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    override fun onResume() {
        super.onResume()
        val config = SnapEngine.store.config.value
        if (!config.enabled || !config.needsLogAccess || !SnapEngine.hasLogPermission()) {
            finish()
            return
        }
        SnapEngine.onConsentResumed(connectReader = !started)
        if (!started) {
            started = true
            scope.launch {
                withTimeoutOrNull(60_000) {
                    SnapEngine.logAccess.first { it != LogAccess.WAITING }
                }
                finish()
            }
        }
    }

    override fun onPause() {
        SnapEngine.onConsentPaused()
        super.onPause()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
