package com.heykay.snapkeymap.ui

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.heykay.snapkeymap.canUseShizuku
import com.heykay.snapkeymap.grantDndAccessWithShizuku
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun DndAccessDialog(onDone: () -> Unit, forSilent: Boolean = false) {
    val context = LocalContext.current
    val notifications = context.getSystemService(NotificationManager::class.java)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val granted by produceState(notifications.isNotificationPolicyAccessGranted) {
        while (true) {
            value = notifications.isNotificationPolicyAccessGranted
            delay(1000)
        }
    }
    val shizukuAvailable by produceState(canUseShizuku()) {
        while (true) {
            value = canUseShizuku()
            delay(1000)
        }
    }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Allow Do Not Disturb access") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (forSilent) {
                    "Your action is saved. Silent works best with Shizuku running on Android 15 or later. " +
                        "Otherwise it needs Do Not Disturb access and may turn on Do Not Disturb."
                } else {
                    "Your action is saved. Do Not Disturb needs Do Not Disturb access. " +
                        "Allow it in Settings, or use Shizuku if your phone blocks the switch."
                })
                if (granted) {
                    Text("Do Not Disturb access granted.")
                } else {
                    TextButton(
                        enabled = !busy,
                        onClick = { context.openSafely(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                    ) { Text("Open settings") }
                    if (shizukuAvailable) {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                busy = true
                                message = null
                                scope.launch {
                                    try {
                                        val result = grantDndAccessWithShizuku(context)
                                        message = if (notifications.isNotificationPolicyAccessGranted) {
                                            "Do Not Disturb access granted."
                                        } else {
                                            result.output.trim().ifEmpty {
                                                "Access wasn't granted (exit ${result.exitCode}). Try Open settings."
                                            }
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        message = e.message ?: "Couldn't allow access. Try Open settings."
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                        ) { Text(if (busy) "Waiting for Shizuku…" else "Allow with Shizuku") }
                    }
                    message?.let { Text(it) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
    )
}
