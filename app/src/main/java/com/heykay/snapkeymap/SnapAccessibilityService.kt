package com.heykay.snapkeymap

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps the app alive in the background, lets it open apps from the background, runs global
 * actions (screenshot, home, ...) and receives the Snap Key directly on phones that share it.
 */
class SnapAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
        runningState.value = true
        SnapEngine.onServiceConnected()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = SnapEngine.onKeyEvent(event)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        val keyboards = getSystemService(InputMethodManager::class.java).enabledInputMethodList
        if (keyboards.none { it.packageName == packageName }) SnapEngine.onForegroundApp(packageName)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        clear()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        clear()
        super.onDestroy()
    }

    private fun clear() {
        instance = null
        runningState.value = false
        SnapEngine.onServiceDisconnected()
    }

    companion object {
        @Volatile
        var instance: SnapAccessibilityService? = null
            private set

        private val runningState = MutableStateFlow(false)
        val running: StateFlow<Boolean> = runningState.asStateFlow()
    }
}
