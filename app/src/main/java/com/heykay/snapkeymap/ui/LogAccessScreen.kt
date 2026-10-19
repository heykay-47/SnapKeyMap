package com.heykay.snapkeymap.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.heykay.snapkeymap.LogAccess
import com.heykay.snapkeymap.SnapEngine
import com.heykay.snapkeymap.grantLogAccessWithShizuku
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

private const val PLATFORM_TOOLS_URL = "https://developer.android.com/tools/releases/platform-tools"
private const val SHIZUKU_REQUEST = 1
private const val GRANT_HINT = "On ColorOS, turn off Permission monitoring in Developer options " +
    "(or turn on Disable permission monitoring), or use the computer/ADB method below."

@Composable
fun LogAccessScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val resumes by SnapEngine.uiResumes.collectAsState()
    val currentOnDone by rememberUpdatedState(onDone)
    val scope = rememberCoroutineScope()
    var shizukuRunning by remember { mutableStateOf(Shizuku.pingBinder()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val command = "adb shell pm grant ${context.packageName} android.permission.READ_LOGS"

    // Only move on by itself when the grant arrives while this screen is open.
    val grantedOnEntry = remember { SnapEngine.hasLogPermission() }

    LaunchedEffect(resumes) {
        if (grantedOnEntry) return@LaunchedEffect
        while (true) {
            if (SnapEngine.hasLogPermission()) {
                if (SnapEngine.logAccess.value == LogAccess.OFF) SnapEngine.connect()
                currentOnDone()
                break
            }
            shizukuRunning = Shizuku.pingBinder()
            if (!shizukuRunning && busy) {
                busy = false
                message = "Shizuku disconnected. $GRANT_HINT"
            }
            delay(1000)
        }
    }

    val grant = rememberUpdatedState<() -> Unit> {
        scope.launch {
            try {
                grantLogAccessWithShizuku(context)
                message = "Log access granted. Reopen SnapKey Map and allow device logs if detection stays paused."
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message = "Couldn't grant log access. $GRANT_HINT"
            } finally {
                busy = false
            }
        }
    }

    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { requestCode, result ->
            if (requestCode == SHIZUKU_REQUEST && busy) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    grant.value()
                } else {
                    busy = false
                    message = "Shizuku permission denied. $GRANT_HINT"
                }
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        onDispose { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    SnapScaffold(title = "One-time log access", onBack = onDone) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (grantedOnEntry) {
                NoteCard(
                    icon = Icons.Rounded.CheckCircle,
                    title = "Done!",
                    body = "Log access is granted. It stays after restarts and app updates, so you won't need to grant it again.",
                    container = MaterialTheme.colorScheme.primaryContainer,
                )
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            }

            Text(
                "Grant system log access once using Shizuku or a computer. " +
                    "The permission stays after restarts and app updates.",
                style = MaterialTheme.typography.bodyLarge,
            )

            if (shizukuRunning) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Without a computer", style = MaterialTheme.typography.titleMedium)
                    Text("Shizuku is running. Allow SnapKey Map when Shizuku asks.")
                    Button(
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            busy = true
                            message = null
                            try {
                                if (Shizuku.isPreV11()) {
                                    busy = false
                                    message = "Update Shizuku to version 11 or newer. $GRANT_HINT"
                                } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                                    grant.value()
                                } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                                    busy = false
                                    message = "Allow SnapKey Map in Shizuku's authorized apps. $GRANT_HINT"
                                } else {
                                    Shizuku.requestPermission(SHIZUKU_REQUEST)
                                }
                            } catch (_: Exception) {
                                busy = false
                                message = "Shizuku isn't available. $GRANT_HINT"
                            }
                        },
                    ) { Text(if (busy) "Waiting for Shizuku…" else "Grant with Shizuku") }
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            Text("Using a computer", style = MaterialTheme.typography.titleMedium)

            GuideStep(
                1,
                "Turn on USB debugging",
                "Settings › About device › Version: tap Build number 7 times. Then go to Settings › " +
                    "Additional settings › Developer options and turn on USB debugging. If you see " +
                    "\"Disable permission monitoring\", turn that on too.",
            ) {
                OutlinedButton(onClick = {
                    context.openSafely(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                }) { Text("Open developer options") }
            }

            GuideStep(
                2,
                "Get ADB on your computer",
                "Download Google's free \"SDK Platform-Tools\" for Windows, Mac or Linux and unzip it. " +
                    "Open a terminal (Command Prompt on Windows) in the unzipped platform-tools folder.",
            ) {
                OutlinedButton(onClick = { context.shareText(PLATFORM_TOOLS_URL) }) {
                    Icon(Icons.Rounded.Share, contentDescription = null)
                    Text("  Send the link to my computer")
                }
            }

            GuideStep(
                3,
                "Plug in and run one command",
                "Connect the phone with a USB cable and tap Allow on the phone's pop-up. Then type this " +
                    "and press Enter. In PowerShell, type .\\adb instead of adb.",
            ) {
                SelectionContainer {
                    Text(
                        command,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { context.copyText(command) }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        Text("  Copy")
                    }
                    TextButton(onClick = { context.shareText(command) }) {
                        Icon(Icons.Rounded.Share, contentDescription = null)
                        Text("  Share")
                    }
                }
            }

            GuideStep(
                4,
                "Come back here",
                "This screen notices the change by itself.",
            )
        }
    }
}

@Composable
private fun GuideStep(number: Int, title: String, body: String, content: @Composable () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$number. $title", style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
        content()
    }
}
