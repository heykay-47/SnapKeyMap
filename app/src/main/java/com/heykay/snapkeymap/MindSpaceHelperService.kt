package com.heykay.snapkeymap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo

class MindSpaceHelperService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var mindSpacePoll: Runnable? = null

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    fun sendScreenshotToMindSpace(onUnavailable: () -> Unit) {
        mindSpacePoll?.let { handler.removeCallbacks(it) }
        mindSpacePoll = null
        if (!performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)) {
            onUnavailable()
            return
        }
        val deadline = SystemClock.uptimeMillis() + 3000L
        val poll = object : Runnable {
            override fun run() {
                if (mindSpacePoll !== this) return
                val clicked = runCatching {
                    for (window in windows) {
                        val root = window.root ?: continue
                        if (root.packageName?.toString() != "com.oplus.screenshot") continue
                        val button = root.findAccessibilityNodeInfosByViewId(
                            "com.oplus.screenshot:id/collection_icon_container",
                        ).firstOrNull { it.isEnabled } ?: continue
                        return@runCatching button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    null
                }.getOrElse { false }
                if (clicked != null || SystemClock.uptimeMillis() >= deadline) {
                    mindSpacePoll = null
                    if (clicked != true) onUnavailable()
                } else {
                    handler.postDelayed(this, 150L)
                }
            }
        }
        mindSpacePoll = poll
        handler.postDelayed(poll, 150L)
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
        mindSpacePoll?.let { handler.removeCallbacks(it) }
        mindSpacePoll = null
        instance = null
    }

    companion object {
        @Volatile
        var instance: MindSpaceHelperService? = null
            private set

        fun isEnabled(context: Context): Boolean = context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                val service = it.resolveInfo.serviceInfo
                ComponentName(service.packageName, service.name) == ComponentName(context, MindSpaceHelperService::class.java)
            }
    }
}
