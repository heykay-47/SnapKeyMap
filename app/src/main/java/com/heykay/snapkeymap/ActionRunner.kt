package com.heykay.snapkeymap

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast

/** Runs a [SnapAction]. Called on the main thread. */
object ActionRunner {
    private var torchCameraId: String? = null
    private var torchOn = false
    private var torchWatched = false

    fun run(context: Context, action: SnapAction) {
        try {
            when (action) {
                SnapAction.NoAction -> Unit
                is SnapAction.OpenApp -> {
                    val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
                    if (intent == null) toast(context, "${action.appName} isn't installed any more")
                    else start(context, intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
                }
                is SnapAction.OpenShortcut -> {
                    val intent = Intent.parseUri(action.intentUri, Intent.URI_INTENT_SCHEME).apply {
                        selector = null
                        removeFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
                        )
                    }
                    if (intent.action == Intent.ACTION_CALL) {
                        toast(context, "Direct-dial shortcuts aren't supported. Use the dialer instead.")
                    } else start(context, intent)
                }
                is SnapAction.OpenLink -> start(
                    context,
                    Intent(Intent.ACTION_VIEW, Uri.parse(action.url)).addCategory(Intent.CATEGORY_BROWSABLE),
                )
                is SnapAction.CallContact -> start(
                    context,
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(action.number))),
                )
                is SnapAction.Device -> runDevice(context, action.action)
            }
        } catch (e: ActivityNotFoundException) {
            toast(context, "No app can do that: ${action.label}")
        } catch (e: Exception) {
            toast(context, "Couldn't ${action.label.lowercase()}: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun runDevice(context: Context, action: DeviceAction) {
        when (action) {
            DeviceAction.FLASHLIGHT -> toggleTorch(context)
            DeviceAction.RINGER_CYCLE -> cycleRinger(context)
            DeviceAction.DO_NOT_DISTURB -> toggleDoNotDisturb(context)
            DeviceAction.SCREENSHOT -> global(context, AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            DeviceAction.LOCK_SCREEN -> global(context, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            DeviceAction.NOTIFICATIONS -> global(context, AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            DeviceAction.QUICK_SETTINGS -> global(context, AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            DeviceAction.RECENT_APPS -> global(context, AccessibilityService.GLOBAL_ACTION_RECENTS)
            DeviceAction.HOME -> global(context, AccessibilityService.GLOBAL_ACTION_HOME)
            DeviceAction.BACK -> global(context, AccessibilityService.GLOBAL_ACTION_BACK)
            DeviceAction.POWER_MENU -> global(context, AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            DeviceAction.SPLIT_SCREEN -> global(context, AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            DeviceAction.PLAY_PAUSE -> mediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            DeviceAction.NEXT_TRACK -> mediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            DeviceAction.PREVIOUS_TRACK -> mediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            DeviceAction.MEDIA_VOLUME_UP -> context.getSystemService(AudioManager::class.java)
                .adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            DeviceAction.MEDIA_VOLUME_DOWN -> context.getSystemService(AudioManager::class.java)
                .adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            DeviceAction.VOLUME_PANEL -> context.getSystemService(AudioManager::class.java)
                .adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            DeviceAction.CAMERA -> {
                val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
                start(
                    context,
                    Intent(
                        if (locked) MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE
                        else MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA,
                    ),
                )
            }
            DeviceAction.VIDEO_CAMERA -> start(context, Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA))
            DeviceAction.ASSISTANT -> try {
                start(context, Intent(Intent.ACTION_VOICE_COMMAND))
            } catch (e: ActivityNotFoundException) {
                start(context, Intent(Intent.ACTION_ASSIST))
            }
            DeviceAction.WIFI_PANEL -> start(context, Intent(Settings.Panel.ACTION_WIFI))
            DeviceAction.BLUETOOTH_SETTINGS -> start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            DeviceAction.NFC_PANEL -> start(context, Intent(Settings.Panel.ACTION_NFC))
            DeviceAction.INTERNET_PANEL -> start(context, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
        }
    }

    private fun start(context: Context, intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun global(context: Context, id: Int) {
        val service = SnapAccessibilityService.instance
        if (service == null) toast(context, "Turn on the SnapKey Map service first")
        else service.performGlobalAction(id)
    }

    private fun mediaKey(context: Context, keyCode: Int) {
        val audio = context.getSystemService(AudioManager::class.java)
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun toggleTorch(context: Context) {
        val cameras = context.getSystemService(CameraManager::class.java)
        val id = torchCameraId ?: cameras.cameraIdList.firstOrNull {
            cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        if (id == null) {
            toast(context, "This phone has no flashlight")
            return
        }
        torchCameraId = id
        if (!torchWatched) {
            torchWatched = true
            // Keeps torchOn right when the flashlight is switched from quick settings too.
            cameras.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == torchCameraId) torchOn = enabled
                }
            }, Handler(Looper.getMainLooper()))
        }
        torchOn = !torchOn
        cameras.setTorchMode(id, torchOn)
    }

    private fun cycleRinger(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java)
        val canSilence = context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        val next = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> if (canSilence) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        audio.ringerMode = next
        toast(
            context,
            when (next) {
                AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                AudioManager.RINGER_MODE_SILENT -> "Silent"
                else -> "Ring"
            },
        )
    }

    private fun toggleDoNotDisturb(context: Context) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        if (!notifications.isNotificationPolicyAccessGranted) {
            toast(context, "Allow SnapKey Map to change Do Not Disturb")
            start(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        val turnOn = notifications.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        notifications.setInterruptionFilter(
            if (turnOn) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL,
        )
        toast(context, if (turnOn) "Do Not Disturb on" else "Do Not Disturb off")
    }

    private fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
