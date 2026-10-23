package com.heykay.snapkeymap.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.app.NotificationManagerCompat
import com.heykay.snapkeymap.Gesture
import com.heykay.snapkeymap.LogAccess
import com.heykay.snapkeymap.LogSource
import com.heykay.snapkeymap.R
import com.heykay.snapkeymap.SnapAccessibilityService
import com.heykay.snapkeymap.SnapAction
import com.heykay.snapkeymap.SnapEngine
import com.heykay.snapkeymap.ThemeMode
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku

private const val SHIZUKU_LOG_REQUEST = 2

@Composable
fun HomeScreen(
    onPick: (Gesture) -> Unit,
    onLearn: () -> Unit,
    onLogAccess: () -> Unit,
    onAbout: () -> Unit,
    onHelp: () -> Unit,
) {
    val context = LocalContext.current
    val config by SnapEngine.store.config.collectAsState()
    val access by SnapEngine.logAccess.collectAsState()
    val precise by SnapEngine.preciseDetection.collectAsState()
    val serviceOn by SnapAccessibilityService.running.collectAsState()
    val resumes by SnapEngine.uiResumes.collectAsState()
    var showThemeMenu by rememberSaveable { mutableStateOf(false) }
    var showTiming by rememberSaveable { mutableStateOf(false) }
    var showPausedApps by rememberSaveable { mutableStateOf(false) }
    var showUnlockHint by rememberSaveable { mutableStateOf(false) }
    val pausedSummary = remember(config.pausedApps, resumes) {
        if (config.pausedApps.isEmpty()) "None" else {
            val pm = context.packageManager
            config.pausedApps.mapNotNull { packageName ->
                runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }.getOrNull()
            }.sortedBy { it.lowercase() }.joinToString(", ")
                .takeIf { it.isNotEmpty() } ?: "${config.pausedApps.size} apps"
        }
    }
    val notificationsAllowed = remember(resumes) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val backgroundAllowed = remember(resumes) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }

    val hasLogPermission by produceState(SnapEngine.hasLogPermission(), resumes) {
        while (true) {
            value = SnapEngine.hasLogPermission()
            delay(1000)
        }
    }
    val warning = when {
        !serviceOn -> "Accessibility is off. Turn it on to run your actions."
        config.needsLogAccess && !hasLogPermission -> "Log access is missing. Grant it again to detect the key."
        !config.isTaught -> "Teach the Snap Key again so it can be detected."
        else -> null
    }

    SnapScaffold(
        title = "SnapKey Map",
        actions = {
            Box {
                IconButton(onClick = { showThemeMenu = true }) {
                    Icon(config.theme.icon, contentDescription = "Theme")
                }
                DropdownMenu(expanded = showThemeMenu, onDismissRequest = { showThemeMenu = false }) {
                    ThemeMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            leadingIcon = { Icon(mode.icon, contentDescription = null) },
                            trailingIcon = {
                                if (mode == config.theme) Icon(Icons.Rounded.Check, contentDescription = null)
                            },
                            onClick = {
                                SnapEngine.store.update { it.copy(theme = mode) }
                                showThemeMenu = false
                            },
                        )
                    }
                }
            }
            IconButton(onClick = onHelp) {
                Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = "Help")
            }
            IconButton(onClick = onAbout) {
                Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.about_title))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                StatusCard(
                    warning = warning,
                    onFix = {
                        when {
                            !serviceOn -> context.openAccessibilitySettings()
                            config.needsLogAccess && !hasLogPermission -> onLogAccess()
                            else -> onLearn()
                        }
                    },
                    enabled = config.enabled,
                    onEnabledChange = { on -> SnapEngine.store.update { it.copy(enabled = on) } },
                    listening = !config.needsLogAccess || precise || access == LogAccess.FULL,
                    access = access,
                )
            }

            item { SectionHeader("What the Snap Key does") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Gesture.entries.forEachIndexed { index, gesture ->
                        if (index > 0) HorizontalDivider()
                        GestureRow(
                            gesture = gesture,
                            action = config.action(gesture),
                            available = (gesture != Gesture.LONG && gesture != Gesture.TAP_HOLD) || config.canDetectHold,
                            unlockedOnly = gesture in config.unlockedOnly,
                            onUnlockedOnlyChange = { on ->
                                SnapEngine.store.update {
                                    it.copy(unlockedOnly = if (on) it.unlockedOnly + gesture else it.unlockedOnly - gesture)
                                }
                                if (on && !config.unlockHintShown) showUnlockHint = true
                            },
                            onClick = { onPick(gesture) },
                        )
                    }
                }
            }
            if (config.action(Gesture.DOUBLE) != SnapAction.NoAction || config.action(Gesture.TAP_HOLD) != SnapAction.NoAction) {
                item {
                    Text(
                        "With a double press or tap, then hold set, a single press waits a split second to see if a second press follows.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    ListItem(
                        modifier = Modifier.clickable { showTiming = true },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("Timing") },
                        leadingContent = { Icon(Icons.Rounded.AvTimer, contentDescription = null) },
                        supportingContent = { Text("Double ${config.doublePressWindowMs} ms · Hold ${config.holdMs} ms", maxLines = 1) },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                    )
                    HorizontalDivider()
                    ListItem(
                        modifier = Modifier.clickable {
                            SnapEngine.store.update { it.copy(showConfirmations = !it.showConfirmations) }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("Show confirmations") },
                        supportingContent = { Text("Pop-ups such as Vibrate or Do Not Disturb on. Errors always show.") },
                        trailingContent = { Switch(checked = config.showConfirmations, onCheckedChange = null) },
                    )
                    HorizontalDivider()
                    ListItem(
                        modifier = Modifier.clickable { showPausedApps = true },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("Pause in apps") },
                        leadingContent = { Icon(Icons.Rounded.Apps, contentDescription = null) },
                        supportingContent = { Text(pausedSummary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
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
                    CardTextButton("Open phone settings") { context.openPhoneSettings() }
                }
            }
            if (!notificationsAllowed) {
                item {
                    NoteCard(
                        icon = Icons.Rounded.Notifications,
                        title = "Allow notifications",
                        body = "Only to tell you when detection pauses. Your actions work without them.",
                    ) {
                        CardTextButton("Open settings") { context.openNotificationSettings() }
                    }
                }
            }
            if (!backgroundAllowed) {
                item {
                    NoteCard(
                        icon = Icons.Rounded.BatteryChargingFull,
                        title = "Let it run in the background",
                        body = "Reduces the chance of ColorOS closing the app. If it still stops working, " +
                            "open App info › Battery and allow background activity.",
                    ) {
                        @SuppressLint("BatteryLife")
                        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"))
                        CardTextButton("Allow") { context.openSafely(request) }
                        CardTextButton("App info") { context.openAppInfo() }
                    }
                }
            }
            item {
                TextButton(onClick = onLearn, modifier = Modifier.fillMaxWidth()) {
                    Text("Teach the Snap Key again")
                }
            }
        }
    }
    if (showTiming) TimingDialog(onDismiss = { showTiming = false })
    if (showPausedApps) PausedAppsSheet(onDismiss = { showPausedApps = false })
    if (showUnlockHint) UnlockHintDialog(onDismiss = {
        SnapEngine.store.update { it.copy(unlockHintShown = true) }
        showUnlockHint = false
    })
}

@Composable
internal fun UnlockHintDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Only when unlocked") },
        text = {
            Text("With the lock on, this action won't run while your phone is locked, so presses in a pocket or bag do nothing. " +
                "Tap the lock again to turn it off.")
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
}

@Composable
private fun TimingDialog(onDismiss: () -> Unit) {
    val config by SnapEngine.store.config.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Timing") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Double-press window: ${config.doublePressWindowMs} ms")
                Slider(
                    value = config.doublePressWindowMs.toFloat(),
                    onValueChange = { value ->
                        SnapEngine.store.update { it.copy(doublePressWindowMs = (value / 25).roundToInt() * 25L) }
                    },
                    valueRange = 200f..600f,
                    steps = 15,
                    modifier = Modifier.semantics { contentDescription = "Double-press window in milliseconds" },
                )
                Text("How long a single press waits after release for another press.", style = MaterialTheme.typography.bodySmall)
                Text("Press & hold: ${config.holdMs} ms")
                Slider(
                    value = config.holdMs.toFloat(),
                    onValueChange = { value ->
                        SnapEngine.store.update { it.copy(holdMs = (value / 25).roundToInt() * 25L) }
                    },
                    valueRange = 300f..1200f,
                    steps = 35,
                    modifier = Modifier.semantics { contentDescription = "Hold time in milliseconds" },
                )
                Text("How long to keep the key down for a hold.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    SnapEngine.store.update { it.copy(doublePressWindowMs = 350L, holdMs = 650L) }
                }) { Text("Reset to defaults") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun StatusCard(
    warning: String?,
    onFix: () -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    listening: Boolean,
    access: LogAccess,
) {
    val last by SnapEngine.lastGesture.collectAsState()
    val lastKeySeenAt by SnapEngine.lastKeySeenAt.collectAsState()
    val listeningSince by SnapEngine.listeningSince.collectAsState()
    val logSource by SnapEngine.logSource.collectAsState()
    val precise by SnapEngine.preciseDetection.collectAsState()
    val inputLive by SnapEngine.inputLive.collectAsState()
    val config by SnapEngine.store.config.collectAsState()
    val timeFormat = DateFormat.getTimeFormat(LocalContext.current)
    val now by produceState(SnapEngine.now()) {
        while (true) {
            delay(1000)
            value = SnapEngine.now()
        }
    }
    val colors = MaterialTheme.colorScheme
    val ready = warning == null && enabled && listening
    val (title, body) = when {
        warning != null -> "Needs attention" to warning
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
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.size(12.dp))
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            Text(
                listOfNotNull(
                    listeningSince?.let {
                        "Listening since ${timeFormat.format(Date(it))}" +
                            when {
                                precise -> " · Precise input via Shizuku"
                                logSource == LogSource.SHIZUKU && access == LogAccess.FULL -> " · Logs via Shizuku"
                                config.needsLogAccess -> " · System logs"
                                else -> ""
                            }
                    },
                    lastKeySeenAt?.let { "Last key press ${timeFormat.format(Date(it))}" } ?: "No key press yet",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            if (config.needsLogAccess && ((config.inputKey == null &&
                (inputLive || (logSource == LogSource.SHIZUKU && access == LogAccess.FULL))) ||
                (config.logSignature?.hasExplicitKeyEdges == true && config.logKeyCode == null))) {
                Text(
                    "Teach the Snap Key again for more precise detection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            if (warning != null) {
                Button(onClick = onFix) { Text("Fix") }
            } else if (enabled && !listening && access != LogAccess.WAITING) {
                Button(onClick = { SnapEngine.connect() }) { Text("Connect") }
            }
            val resumes by SnapEngine.uiResumes.collectAsState()
            val canAskShizuku = remember(resumes, logSource) {
                runCatching {
                    Shizuku.pingBinder() && !Shizuku.isPreV11() &&
                        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
                }.getOrDefault(false)
            }
            if (warning == null && enabled && canAskShizuku) {
                Text(
                    "Shizuku is running. Allow SnapKey Map in Shizuku and you won't be asked for log access after restarts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                OutlinedButton(onClick = { runCatching { Shizuku.requestPermission(SHIZUKU_LOG_REQUEST) } }) {
                    Text("Use Shizuku")
                }
            }
        }
    }
}

@Composable
internal fun GestureRow(
    gesture: Gesture,
    action: SnapAction,
    available: Boolean,
    unlockedOnly: Boolean,
    onUnlockedOnlyChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(enabled = available, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(gestureIcon(gesture), contentDescription = null) },
        headlineContent = { Text(gesture.title) },
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
            if (available) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (action != SnapAction.NoAction) {
                        IconToggleButton(checked = unlockedOnly, onCheckedChange = onUnlockedOnlyChange) {
                            Icon(
                                if (unlockedOnly) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                contentDescription = if (unlockedOnly) "Only when unlocked, on" else "Only when unlocked, off",
                                tint = if (unlockedOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                }
            }
        },
    )
}
