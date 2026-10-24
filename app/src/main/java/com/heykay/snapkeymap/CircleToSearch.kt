package com.heykay.snapkeymap

import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

object CircleToSearch {
    fun start(): Boolean {
        if (canUseShizuku()) {
            try {
                val binder = SystemServiceHelper.getSystemService("voiceinteraction")
                if (binder != null && showSession(ShizukuBinderWrapper(binder))) return true
            } catch (_: Throwable) {
            }
        }
        return try {
            val binder = HiddenApiBypass.invoke(
                Class.forName("android.os.ServiceManager"), null, "getService", "voiceinteraction",
            ) as? IBinder ?: return false
            showSession(binder)
        } catch (_: Throwable) {
            false
        }
    }

    private fun showSession(binder: IBinder): Boolean {
        val service = HiddenApiBypass.invoke(
            Class.forName("com.android.internal.app.IVoiceInteractionManagerService\$Stub"),
            null, "asInterface", binder,
        ) ?: return false
        // Essentials' OmniTriggerUtil supplies these invocation values for Google's contextual search.
        val args = Bundle().apply {
            putLong("invocation_time_ms", SystemClock.elapsedRealtime())
            putInt("omni.entry_point", 1)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HiddenApiBypass.invoke(service.javaClass, service, "showSessionFromSession", null, args, 7, "hyperOS_home")
        } else {
            HiddenApiBypass.invoke(service.javaClass, service, "showSessionFromSession", null, args, 7)
        } == true
    }
}
