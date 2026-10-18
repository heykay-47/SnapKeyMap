package com.heykay.snapkeymap.ui

import android.content.Intent
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.heykay.snapkeymap.SnapEngine

private const val PLATFORM_TOOLS_URL = "https://developer.android.com/tools/releases/platform-tools"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogAccessScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val resumes by SnapEngine.uiResumes.collectAsState()
    val granted = remember(resumes) { SnapEngine.hasLogPermission() }
    val command = "adb shell pm grant ${context.packageName} android.permission.READ_LOGS"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("One-time log access") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (granted) {
                NoteCard(
                    icon = Icons.Rounded.CheckCircle,
                    title = "Done!",
                    body = "Log access is granted. It stays after restarts and app updates, so you won't need the computer again.",
                    container = MaterialTheme.colorScheme.primaryContainer,
                )
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            }

            Text(
                "Android only lets an app read the system log after you allow it from a computer. " +
                    "It's free and takes about 5 minutes.",
                style = MaterialTheme.typography.bodyLarge,
            )

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
        Text("$number. $title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
        content()
    }
}
