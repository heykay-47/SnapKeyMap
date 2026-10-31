package com.heykay.snapkeymap.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Restore
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.app.NotificationManagerCompat
import com.heykay.snapkeymap.Gesture
import com.heykay.snapkeymap.ActionRunner
import com.heykay.snapkeymap.Config
import com.heykay.snapkeymap.BackupResult
import com.heykay.snapkeymap.SettingsBackup
import com.heykay.snapkeymap.LogAccess
import com.heykay.snapkeymap.LogSource
import com.heykay.snapkeymap.R
import com.heykay.snapkeymap.SnapAccessibilityService
import com.heykay.snapkeymap.SnapAction
import com.heykay.snapkeymap.SnapEngine
import com.heykay.snapkeymap.ThemeMode
import com.heykay.snapkeymap.buildReport
import com.heykay.snapkeymap.bugReportUrl
import com.heykay.snapkeymap.gatherReportInfo
import com.heykay.snapkeymap.backupToJson
import com.heykay.snapkeymap.parseBackup
import java.time.LocalDate
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private const val SHIZUKU_LOG_REQUEST = 2
private const val MAX_BACKUP_BYTES = 1024 * 1024

private data class HomeChecks(
    val hasLogPermission: Boolean = false,
    val notificationsAllowed: Boolean = true,
    val backgroundAllowed: Boolean = true,
    val canAskShizuku: Boolean = false,
    val now: Long = SnapEngine.now(),
)

@Composable
fun HomeScreen(
    onPick: (Gesture) -> Unit,
    onLearn: () -> Unit,
    onLogAccess: () -> Unit,
    onAbout: () -> Unit,
    onHelp: () -> Unit,
) {
    val context = LocalContext.current
    val config by SnapEngine.store.config.collectAsStateWithLifecycle()
    val access by SnapEngine.logAccess.collectAsStateWithLifecycle()
    val precise by SnapEngine.preciseDetection.collectAsStateWithLifecycle()
    val serviceOn by SnapAccessibilityService.running.collectAsStateWithLifecycle()
    val resumes by SnapEngine.uiResumes.collectAsStateWithLifecycle()
    val last by SnapEngine.lastGesture.collectAsStateWithLifecycle()
    val lastKeySeenAt by SnapEngine.lastKeySeenAt.collectAsStateWithLifecycle()
    val listeningSince by SnapEngine.listeningSince.collectAsStateWithLifecycle()
    val logSource by SnapEngine.logSource.collectAsStateWithLifecycle()
    val inputLive by SnapEngine.inputLive.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val layoutDirection = LocalLayoutDirection.current
    var showThemeMenu by rememberSaveable { mutableStateOf(false) }
    var showTiming by rememberSaveable { mutableStateOf(false) }
    var showPausedApps by rememberSaveable { mutableStateOf(false) }
    var showUnlockHint by rememberSaveable { mutableStateOf(false) }
    var showBackupWarning by rememberSaveable { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<SettingsBackup?>(null) }
    val scope = rememberCoroutineScope()
    val backupFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val snapshot = SnapEngine.store.config.value
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = backupToJson(snapshot)
                    val output = checkNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                }
            }
            Toast.makeText(context, if (result.isSuccess) "Settings backed up" else "Couldn't write the backup file.", Toast.LENGTH_SHORT).show()
        }
    }
    val restoreFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val input = checkNotNull(context.contentResolver.openInputStream(uri))
                    val text = input.use { stream ->
                        val bytes = ByteArray(MAX_BACKUP_BYTES + 1)
                        var size = 0
                        while (size < bytes.size) {
                            val count = stream.read(bytes, size, bytes.size - size)
                            if (count < 0) break
                            size += count
                        }
                        require(size <= MAX_BACKUP_BYTES) { "The backup file is too large. Choose a file smaller than 1 MB." }
                        String(bytes, 0, size, Charsets.UTF_8)
                    }
                    parseBackup(text)
                }
            }
            result.fold(
                onSuccess = { parsed ->
                    when (parsed) {
                        is BackupResult.Ok -> pendingRestore = parsed.backup
                        is BackupResult.Error -> Toast.makeText(context, parsed.message, Toast.LENGTH_LONG).show()
                    }
                },
                onFailure = { error ->
                    val message = if (error is IllegalArgumentException) error.message else "Couldn't read the backup file."
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                },
            )
        }
    }
    val pausedSummary by produceState("None", config.pausedApps, resumes, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            value = withContext(Dispatchers.Default) {
                if (config.pausedApps.isEmpty()) "None" else {
                    val pm = context.packageManager
                    config.pausedApps.mapNotNull { packageName ->
                        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }.getOrNull()
                    }.sortedBy { it.lowercase() }.joinToString(", ")
                        .takeIf { it.isNotEmpty() } ?: "${config.pausedApps.size} apps"
                }
            }
        }
    }
    val checks by produceState(HomeChecks(), lifecycle, resumes) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = HomeChecks(
                    hasLogPermission = SnapEngine.hasLogPermission(),
                    notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                    backgroundAllowed = context.getSystemService(PowerManager::class.java)
                        .isIgnoringBatteryOptimizations(context.packageName),
                    canAskShizuku = runCatching {
                        Shizuku.pingBinder() && !Shizuku.isPreV11() &&
                            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
                    }.getOrDefault(false),
                )
                delay(1000)
            }
        }
    }
    val warning = when {
        !serviceOn -> "Accessibility is off. Turn it on to run your actions."
        config.needsLogAccess && !checks.hasLogPermission -> "Log access is missing. Grant it again to detect the key."
        !config.isTaught -> "Teach the Snap Key again so it can be detected."
        else -> null
    }

    SnapScaffold(
        title = "SnapKey Map",
        titleLeadingContent = {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))) {
                Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null,
                    modifier = Modifier.matchParentSize())
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
                    modifier = Modifier.matchParentSize().graphicsLayer(scaleX = 1.4f, scaleY = 1.4f))
            }
        },
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection) + 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                end = padding.calculateEndPadding(layoutDirection) + 16.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                StatusCard(
                    warning = warning,
                    onFix = {
                        when {
                            !serviceOn -> context.openAccessibilitySettings()
                            config.needsLogAccess && !checks.hasLogPermission -> onLogAccess()
                            else -> onLearn()
                        }
                    },
                    enabled = config.enabled,
                    onEnabledChange = { on -> SnapEngine.store.update { it.copy(enabled = on) } },
                    listening = !config.needsLogAccess || precise || access == LogAccess.FULL,
                    access = access,
                    config = config,
                    last = last,
                    lastKeySeenAt = lastKeySeenAt,
                    listeningSince = listeningSince,
                    logSource = logSource,
                    precise = precise,
                    inputLive = inputLive,
                    now = checks.now,
                    canAskShizuku = checks.canAskShizuku,
                )
            }

            item {
                TextButton(onClick = onLearn, modifier = Modifier.fillMaxWidth()) {
                    Text("Teach the Snap Key again")
                }
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
                            onTry = {
                                ActionRunner.run(
                                    SnapAccessibilityService.instance ?: context,
                                    config.action(gesture),
                                    alwaysConfirm = true,
                                )
                            },
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
            item { SectionHeader("Backup") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    ListItem(
                        modifier = Modifier.clickable { showBackupWarning = true },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("Back up settings") },
                        leadingContent = { Icon(Icons.Rounded.Save, contentDescription = null) },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                    )
                    HorizontalDivider()
                    ListItem(
                        modifier = Modifier.clickable {
                            try {
                                restoreFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, "No file picker available", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("Restore settings") },
                        leadingContent = { Icon(Icons.Rounded.Restore, contentDescription = null) },
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
            if (!checks.notificationsAllowed) {
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
            if (!checks.backgroundAllowed) {
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
        }
    }
    if (showTiming) TimingDialog(config = config, onDismiss = { showTiming = false })
    if (showPausedApps) PausedAppsSheet(onDismiss = { showPausedApps = false })
    if (showBackupWarning) {
        AlertDialog(
            onDismissRequest = { showBackupWarning = false },
            title = { Text("Back up settings") },
            text = { Text("The file can contain contact numbers, links and app names. It is saved only where you choose.") },
            confirmButton = {
                TextButton(onClick = {
                    showBackupWarning = false
                    try {
                        backupFile.launch("snapkeymap-backup-${LocalDate.now()}.json")
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "No file picker available", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showBackupWarning = false }) { Text("Cancel") } },
        )
    }
    pendingRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore settings?") },
            text = { Text("This replaces your gesture actions, ringer presets, paused apps, timing, theme, confirmations and the learned key.") },
            confirmButton = {
                TextButton(onClick = {
                    SnapEngine.store.update { backup.applyTo(it) }
                    pendingRestore = null
                    Toast.makeText(context, "Settings restored", Toast.LENGTH_SHORT).show()
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
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
private fun TimingDialog(config: Config, onDismiss: () -> Unit) {
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
    config: Config,
    last: SnapEngine.Detected?,
    lastKeySeenAt: Long?,
    listeningSince: Long?,
    logSource: LogSource?,
    precise: Boolean,
    inputLive: Boolean,
    now: Long,
    canAskShizuku: Boolean,
) {
    val context = LocalContext.current
    val timeFormat = DateFormat.getTimeFormat(context)
    var showDetails by rememberSaveable { mutableStateOf(false) }
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
                    Text(title, style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                            stateDescription = if (ready && last != null) {
                                "Last: ${last.gesture.title.lowercase()} at " +
                                    java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM).format(Date(last.atMs))
                            } else if (ready) "Press the Snap Key to try it." else body
                        })
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.size(12.dp))
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            CardTextButton(
                text = if (showDetails) "Hide details" else "Details",
                modifier = Modifier.heightIn(min = 48.dp),
                onClick = { showDetails = !showDetails },
            )
            if (showDetails) {
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
                Row(Modifier.offset(x = (-12).dp)) {
                    TextButton(onClick = {
                        val info = gatherReportInfo(context, config, precise)
                        val wallOffset = System.currentTimeMillis() - SnapEngine.now()
                        val secondsFormat = java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM)
                        val report = buildReport(info, config, SnapEngine.detections.value) {
                            secondsFormat.format(Date(wallOffset + it))
                        }
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("SnapKey Map bug report", report))
                        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Report copied", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy report") }
                    TextButton(onClick = {
                        val info = gatherReportInfo(context, config, precise)
                        val wallOffset = System.currentTimeMillis() - SnapEngine.now()
                        val url = bugReportUrl(info, config, SnapEngine.lastGesture.value) {
                            timeFormat.format(Date(wallOffset + it))
                        }
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, "No browser available to open the bug form", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Report a bug") }
                }
            }
            if (warning != null) {
                Button(onClick = onFix) { Text("Fix") }
            } else if (enabled && !listening && access != LogAccess.WAITING) {
                Button(onClick = { SnapEngine.connect() }) { Text("Connect") }
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
    onTry: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val resumes by SnapEngine.uiResumes.collectAsStateWithLifecycle()
    val packageName = (action as? SnapAction.OpenApp)?.packageName
    val missingApp = remember(packageName, resumes) {
        packageName != null && context.packageManager.getLaunchIntentForPackage(packageName) == null
    }
    ListItem(
        modifier = Modifier.clickable(enabled = available, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(gestureIcon(gesture), contentDescription = null) },
        headlineContent = { Text(gesture.title) },
        supportingContent = {
            if (available) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(actionIcon(action), contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(if (missingApp) "App not installed" else action.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        if (onTry != null) {
                            IconButton(onClick = onTry) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Try ${gesture.title}")
                            }
                        }
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
