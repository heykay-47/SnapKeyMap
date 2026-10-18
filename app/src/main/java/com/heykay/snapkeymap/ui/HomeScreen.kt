package com.heykay.snapkeymap.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.heykay.snapkeymap.Gesture
import com.heykay.snapkeymap.LogAccess
import com.heykay.snapkeymap.SnapAccessibilityService
import com.heykay.snapkeymap.SnapAction
import com.heykay.snapkeymap.SnapEngine
import kotlinx.coroutines.delay

private class SetupStep(
    val title: String,
    val body: String,
    val done: Boolean,
    val enabled: Boolean,
    val button: String,
    val onClick: () -> Unit,
    val secondButton: String? = null,
    val onSecondClick: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onPick: (Gesture) -> Unit, onLearn: () -> Unit, onLogAccess: () -> Unit) {
    val context = LocalContext.current
    val config by SnapEngine.store.config.collectAsState()
    val access by SnapEngine.logAccess.collectAsState()
    val serviceOn by SnapAccessibilityService.running.collectAsState()
    val resumes by SnapEngine.uiResumes.collectAsState()
    var permissionAnswers by remember { mutableIntStateOf(0) }

    val hasLogPermission = remember(resumes) { SnapEngine.hasLogPermission() }
    val notificationsAllowed = remember(resumes, permissionAnswers) {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val backgroundAllowed = remember(resumes) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionAnswers++
    }

    val steps = buildList {
        add(
            SetupStep(
                title = "Turn on the SnapKey Map service",
                body = "In the list, tap SnapKey Map and switch it on. If it's greyed out, open App info, " +
                    "tap ⋮ at the top right, choose \"Allow restricted settings\", then try again.",
                done = serviceOn,
                enabled = true,
                button = "Open settings",
                onClick = { context.openAccessibilitySettings() },
                secondButton = "App info",
                onSecondClick = { context.openAppInfo() },
            ),
        )
        if (config.needsLogAccess) {
            add(
                SetupStep(
                    title = "Give one-time log access",
                    body = "Oppo keeps the Snap Key private, so the app watches the phone's system log for it. " +
                        "Nothing leaves your phone. You need a computer for this, once.",
                    done = hasLogPermission,
                    enabled = true,
                    button = "Show me how",
                    onClick = onLogAccess,
                ),
            )
        }
        add(
            SetupStep(
                title = "Teach the app your Snap Key",
                body = "Press the key a few times so the app can recognise it. Takes about 20 seconds.",
                done = config.isTaught,
                enabled = serviceOn,
                button = "Start",
                onClick = onLearn,
            ),
        )
    }
    val setupDone = steps.all { it.done }

    Scaffold(topBar = { TopAppBar(title = { Text("SnapKey Map") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                StatusCard(
                    setupDone = setupDone,
                    stepsDone = steps.count { it.done },
                    stepCount = steps.size,
                    enabled = config.enabled,
                    onEnabledChange = { on -> SnapEngine.store.update { it.copy(enabled = on) } },
                    listening = !config.needsLogAccess || access == LogAccess.FULL,
                    access = access,
                )
            }

            if (!setupDone) {
                item { SectionHeader("Set up") }
                items(steps.size) { index -> StepCard(index + 1, steps[index]) }
            }

            item { SectionHeader("What the Snap Key does") }
            items(Gesture.entries) { gesture ->
                val action = config.action(gesture)
                val available = gesture != Gesture.LONG || config.canDetectHold
                GestureCard(
                    gesture = gesture,
                    action = action,
                    available = available,
                    onClick = { onPick(gesture) },
                )
            }
            if (config.action(Gesture.DOUBLE) != SnapAction.NoAction) {
                item {
                    Text(
                        "With a double press set, a single press waits a split second to see if a second press follows.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionHeader("Tips") }
            item {
                NoteCard(
                    icon = Icons.Rounded.Settings,
                    title = "Set Oppo's own Snap Key action to None",
                    body = "Settings › Snap Key. Otherwise Oppo's action runs as well as yours.",
                ) {
                    TextButton(onClick = { context.openPhoneSettings() }) { Text("Open phone settings") }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsAllowed) {
                item {
                    NoteCard(
                        icon = Icons.Rounded.Notifications,
                        title = "Allow notifications",
                        body = "So the app can tell you when the Snap Key needs turning back on, like after a restart.",
                    ) {
                        TextButton(onClick = { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text("Allow")
                        }
                    }
                }
            }
            if (!backgroundAllowed) {
                item {
                    NoteCard(
                        icon = Icons.Rounded.BatteryChargingFull,
                        title = "Let it run in the background",
                        body = "Stops ColorOS from closing the app. If it still stops working, open App info › " +
                            "Battery and allow background activity.",
                    ) {
                        @SuppressLint("BatteryLife")
                        val request = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"),
                        )
                        TextButton(onClick = { context.openSafely(request) }) { Text("Allow") }
                        TextButton(onClick = { context.openAppInfo() }) { Text("App info") }
                    }
                }
            }
            if (setupDone) {
                item {
                    TextButton(onClick = onLearn, modifier = Modifier.fillMaxWidth()) {
                        Text("Teach the Snap Key again")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    setupDone: Boolean,
    stepsDone: Int,
    stepCount: Int,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    listening: Boolean,
    access: LogAccess,
) {
    val last by SnapEngine.lastGesture.collectAsState()
    val now by produceState(SnapEngine.now()) {
        while (true) {
            delay(1000)
            value = SnapEngine.now()
        }
    }
    val colors = MaterialTheme.colorScheme
    val ready = setupDone && enabled && listening
    val (title, body) = when {
        !setupDone -> "Finish setup" to "$stepsDone of $stepCount steps done."
        !enabled -> "Turned off" to "The Snap Key does what Oppo set it to."
        !listening -> "Paused" to when (access) {
            LogAccess.WAITING -> "Tap \"Allow\" on the pop-up."
            LogAccess.LIMITED -> "Log access was declined. Wait a minute, then tap Connect and choose Allow."
            else -> "Tap Connect to start listening for the Snap Key again."
        }
        else -> "Ready" to (last?.let { detected ->
            val seconds = (now - detected.atMs).coerceAtLeast(0) / 1000
            "Last: ${detected.gesture.title.lowercase()} · " + if (seconds < 2) "just now" else "${seconds}s ago"
        } ?: "Press the Snap Key to try it.")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ready) colors.primaryContainer else colors.surfaceContainerHighest,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.size(12.dp))
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            if (setupDone && enabled && !listening && access != LogAccess.WAITING) {
                Button(onClick = { SnapEngine.connect() }) { Text("Connect") }
            }
        }
    }
}

@Composable
private fun StepCard(number: Int, step: SetupStep) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                if (step.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (step.done) "Done" else "Not done",
                tint = if (step.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$number. ${step.title}", style = MaterialTheme.typography.titleMedium)
                if (!step.done) {
                    Text(step.body, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = step.onClick, enabled = step.enabled) { Text(step.button) }
                        if (step.secondButton != null) {
                            OutlinedButton(onClick = step.onSecondClick) { Text(step.secondButton) }
                        }
                    }
                    if (!step.enabled) {
                        Text(
                            "Do step 1 first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureCard(gesture: Gesture, action: SnapAction, available: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, enabled = available, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            leadingContent = { Icon(gestureIcon(gesture), contentDescription = null) },
            headlineContent = { Text(gesture.title, fontWeight = FontWeight.SemiBold) },
            supportingContent = {
                if (available) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(actionIcon(action), contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(action.label, maxLines = 1)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Your phone doesn't report holding the key, so this can't be detected.")
                    }
                }
            },
            trailingContent = {
                if (available) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            },
        )
    }
}
