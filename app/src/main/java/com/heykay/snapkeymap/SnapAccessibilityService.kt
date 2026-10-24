package com.heykay.snapkeymap

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps the app alive in the background, lets it open apps from the background, runs global
 * actions (screenshot, home, ...) and receives the Snap Key directly on phones that share it.
 */
class SnapAccessibilityService : AccessibilityService() {
    private val messageHandler = Handler(Looper.getMainLooper())
    private var messageView: TextView? = null
    private val hideMessage = Runnable { removeMessage() }
    private val activityPackages = LinkedHashMap<ComponentName, String?>()

    override fun onServiceConnected() {
        instance = this
        runningState.value = true
        SnapEngine.onServiceConnected()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = SnapEngine.onKeyEvent(event)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return
        val component = ComponentName(packageName, className)
        val activityPackage = if (activityPackages.containsKey(component)) {
            activityPackages[component]
        } else {
            val resolved = runCatching { packageManager.getActivityInfo(component, 0).packageName }.getOrNull()
            if (activityPackages.size >= 64) activityPackages.remove(activityPackages.keys.first())
            activityPackages[component] = resolved
            resolved
        } ?: return
        SnapEngine.onForegroundApp(activityPackage)
    }

    override fun onInterrupt() = Unit

    fun showMessage(text: CharSequence) {
        messageHandler.removeCallbacks(hideMessage)
        val current = messageView
        if (current != null) {
            current.text = text
        } else {
            val metrics = resources.displayMetrics
            val density = metrics.density
            val view = TextView(this).apply {
                this.text = text
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
                maxWidth = (metrics.widthPixels * 0.8f).toInt()
                setPadding(
                    (16 * density).toInt(), (10 * density).toInt(),
                    (16 * density).toInt(), (10 * density).toInt(),
                )
                background = GradientDrawable().apply {
                    cornerRadius = 24 * density
                    setColor(Color.parseColor("#E6303030"))
                }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = (metrics.heightPixels * 0.12f).toInt()
            }
            if (runCatching { getSystemService(WindowManager::class.java).addView(view, params) }.isFailure) {
                Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
                return
            }
            messageView = view
        }
        messageHandler.postDelayed(hideMessage, 2000L)
    }

    private fun removeMessage() {
        messageHandler.removeCallbacks(hideMessage)
        messageView?.let { view ->
            runCatching { getSystemService(WindowManager::class.java).removeView(view) }
        }
        messageView = null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        clear()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        clear()
        super.onDestroy()
    }

    private fun clear() {
        removeMessage()
        activityPackages.clear()
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
