package com.heykay.snapkeymap.ui

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.heykay.snapkeymap.GrantMethod
import com.heykay.snapkeymap.Gesture
import com.heykay.snapkeymap.LogAccess
import com.heykay.snapkeymap.SetupPage
import com.heykay.snapkeymap.SnapAccessibilityService
import com.heykay.snapkeymap.SnapAction
import com.heykay.snapkeymap.SnapEngine
import com.heykay.snapkeymap.canUseShizuku
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku

private data class SetupChecks(
    val logs: Boolean,
    val shizuku: Boolean,
    val shizukuRunning: Boolean,
    val battery: Boolean,
    val notifications: Boolean,
)

@Composable
fun SetupScreen(
    pageName: String?,
    onPageChange: (String) -> Unit,
    onLearn: () -> Unit,
    onPick: (Gesture) -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val config by SnapEngine.store.config.collectAsStateWithLifecycle()
    val serviceOn by SnapAccessibilityService.running.collectAsStateWithLifecycle()
    val resumes by SnapEngine.uiResumes.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val animate = ValueAnimator.areAnimatorsEnabled()
    var showUnlockHint by rememberSaveable { mutableStateOf(false) }
    fun readChecks() = SetupChecks(
        logs = SnapEngine.hasLogPermission(),
        shizuku = canUseShizuku(),
        shizukuRunning = runCatching { Shizuku.pingBinder() }.getOrDefault(false),
        battery = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName),
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
    )
    val checksState by produceState<SetupChecks?>(null, resumes, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = readChecks()
                delay(1000)
            }
        }
    }
    val checks = checksState ?: return
    var selectedMethod by remember(config.grantMethod) {
        mutableStateOf(config.grantMethod ?: if (checks.shizukuRunning) GrantMethod.SHIZUKU else GrantMethod.ADB)
    }
    fun isDone(page: SetupPage): Boolean = when (page) {
        SetupPage.METHOD -> config.grantMethod != null
        SetupPage.LOG_ACCESS -> checks.logs && (config.grantMethod != GrantMethod.SHIZUKU || checks.shizuku)
        SetupPage.ACCESSIBILITY -> serviceOn
        SetupPage.BATTERY -> checks.battery
        SetupPage.NOTIFICATIONS -> checks.notifications
        SetupPage.TEACH -> config.isTaught
        SetupPage.ACTIONS -> Gesture.entries.any { config.action(it) != SnapAction.NoAction }
    }
    val page = pageName?.let { SetupPage.valueOf(it) } ?: SetupPage.entries.firstOrNull {
        !isDone(it) && it !in config.setupSkipped
    } ?: SetupPage.ACTIONS
    val scrollState = rememberScrollState()
    LaunchedEffect(page) { scrollState.scrollTo(0) }
    LaunchedEffect(Unit) {
        if (pageName == null) onPageChange(page.name)
    }
    LaunchedEffect(checks.logs, checks.shizuku, config.grantMethod) {
        if (checks.logs && SnapEngine.logAccess.value == LogAccess.OFF) {
            SnapEngine.connect(preferShizuku = config.grantMethod == GrantMethod.SHIZUKU && checks.shizuku)
        }
    }
    val back = { onPageChange(SetupPage.entries[(page.ordinal - 1).coerceAtLeast(0)].name) }
    BackHandler(enabled = page != SetupPage.METHOD, onBack = back)
    val optional = page == SetupPage.BATTERY || page == SetupPage.NOTIFICATIONS
    fun title(step: SetupPage) = when (step) {
        SetupPage.METHOD -> "How do you want to give log access?"
        SetupPage.LOG_ACCESS -> "Give log access"
        SetupPage.ACCESSIBILITY -> "Turn on accessibility"
        SetupPage.BATTERY -> "Let detection run in the background"
        SetupPage.NOTIFICATIONS -> "Know when detection pauses"
        SetupPage.TEACH -> "Teach your Snap Key"
        SetupPage.ACTIONS -> "Choose your actions"
    }
    fun next() {
        if (page == SetupPage.METHOD) {
            SnapEngine.store.update { it.copy(grantMethod = selectedMethod) }
        }
        onPageChange(SetupPage.entries[page.ordinal + 1].name)
    }

    SnapScaffold(title = "Set up SnapKey Map", onBack = if (page != SetupPage.METHOD) back else null) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(padding),
        ) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    if (!animate) (EnterTransition.None togetherWith ExitTransition.None).using(null)
                    else {
                        val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                        (slideInHorizontally(tween(180)) { direction * it / 12 } + fadeIn(tween(180))) togetherWith
                            (slideOutHorizontally(tween(140)) { -direction * it / 12 } + fadeOut(tween(140)))
                    }
                },
                label = "Setup step",
            ) { step ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Step ${step.ordinal + 1} of ${SetupPage.entries.size}" +
                    if (step == SetupPage.BATTERY || step == SetupPage.NOTIFICATIONS) " · Optional" else "")
                LinearProgressIndicator(
                    progress = { (step.ordinal + 1) / SetupPage.entries.size.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(title(step), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() })
                when (step) {
                    SetupPage.METHOD -> {
                        Text("Assign your own actions to a press, double press, hold or tap then hold. Oppo keeps the key private, " +
                            "so SnapKey Map needs system logs to detect it. Logs stay on your phone.")
                        MethodOption(
                            title = "Shizuku",
                            body = "No computer. No device-logs prompt after restarts while Shizuku runs and " +
                                "SnapKey Map is allowed in it. Install Shizuku and start it with Wireless debugging " +
                                "(Android 11+). Shizuku may need starting again after a phone restart.",
                            selected = selectedMethod == GrantMethod.SHIZUKU,
                            onSelect = { selectedMethod = GrantMethod.SHIZUKU },
                        )
                        TextButton(onClick = {
                            context.openSafely(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/guide/setup/")))
                        }) { Text("Shizuku setup guide") }
                        MethodOption(
                            title = "Computer (ADB)",
                            body = "One command over USB. The grant stays after restarts, but Android asks for " +
                                "one-time device-log access after each app or phone restart and may ask on reconnect.",
                            selected = selectedMethod == GrantMethod.ADB,
                            onSelect = { selectedMethod = GrantMethod.ADB },
                        )
                        Text("Your progress is saved. You can close the app and continue later.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    SetupPage.LOG_ACCESS -> LogAccessInstructions(method = config.grantMethod)
                    SetupPage.ACCESSIBILITY -> {
                        Text("Even with ADB or Shizuku, accessibility runs actions: screenshot, lock, home, back, " +
                            "recents and notification shade. It lets SnapKey Map open apps and the device-logs " +
                            "prompt from the background, catches the key directly on devices that send it, " +
                            "and helps keep the app running. It cannot read screen content.")
                        Button(onClick = { context.openAccessibilitySettings() }) { Text("Open accessibility settings") }
                        Text("Find SnapKey Map and switch it on. If Android blocks the switch, open App info, " +
                            "tap the three-dot menu, choose Allow restricted settings, then try again.")
                        OutlinedButton(onClick = { context.openAppInfo() }) { Text("App info") }
                    }
                    SetupPage.BATTERY -> {
                        Text("ColorOS can stop background apps and pause key detection. Ignoring battery " +
                            "optimisation can reduce interruptions, but does not guarantee background detection. " +
                            "You can skip this and change it later in App info › Battery.")
                        @SuppressLint("BatteryLife")
                        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"))
                        Button(onClick = { context.openSafely(request) }) { Text("Allow background operation") }
                        OutlinedButton(onClick = { context.openAppInfo() }) { Text("App info") }
                    }
                    SetupPage.NOTIFICATIONS -> {
                        Text("Notifications are only for telling you when detection is paused. They are optional; " +
                            "your key actions still work without them.")
                        val prefs = remember { context.getSharedPreferences("setup_ui", Context.MODE_PRIVATE) }
                        var asked by remember { mutableStateOf(prefs.getBoolean("notifications_asked", false)) }
                        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                            asked = true
                            prefs.edit().putBoolean("notifications_asked", true).apply()
                        }
                        val useSettings = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            (asked && activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == false)
                        Button(onClick = {
                            if (useSettings) context.openNotificationSettings()
                            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }) { Text(if (useSettings) "Open notification settings" else "Allow notifications") }
                        if (!useSettings) {
                            TextButton(onClick = { context.openNotificationSettings() }) { Text("Notification settings") }
                        }
                    }
                    SetupPage.TEACH -> {
                        Text("First set Settings › Snap Key › Oppo's own action to None, so both actions don't run. " +
                            "Then teach SnapKey Map: press three times, hold once, and check a different key before saving. It takes about 30 seconds.")
                        OutlinedButton(onClick = { context.openPhoneSettings() }) { Text("Open phone settings") }
                        Button(onClick = onLearn) { Text(if (config.isTaught) "Teach again" else "Teach the Snap Key") }
                    }
                    SetupPage.ACTIONS -> {
                        Text("Tap a gesture to choose what it does. At least one action is needed. " +
                            "After finishing, try the key and check Last: on the home screen.")
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Gesture.entries.forEach { gesture ->
                                GestureRow(
                                    gesture = gesture,
                                    action = config.action(gesture),
                                    available = true,
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
                        if (!config.canDetectHold) {
                            Text("Your phone doesn't report holding the key, so Press & hold and Tap, then hold cannot be detected.")
                        }
                    }
                }
                if (step != SetupPage.METHOD && isDone(step)) {
                    Text("Done", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium)
                }
            }
            }
            if (page != SetupPage.METHOD && !isDone(page)) {
                Text(
                    when (page) {
                        SetupPage.LOG_ACCESS -> if (checks.logs && config.grantMethod == GrantMethod.SHIZUKU) {
                            "Start Shizuku and allow SnapKey Map to continue."
                        } else "Grant log access to continue."
                        SetupPage.ACCESSIBILITY -> "Turn on accessibility to continue."
                        SetupPage.BATTERY -> "Allow background operation to continue, or skip this step."
                        SetupPage.NOTIFICATIONS -> "Allow notifications to continue, or skip this step."
                        SetupPage.TEACH -> "Teach your Snap Key to continue."
                        SetupPage.ACTIONS -> "Choose at least one action to finish."
                        SetupPage.METHOD -> "Choose an access method to continue."
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (page != SetupPage.METHOD) OutlinedButton(onClick = back) { Text("Back") }
                if (optional) TextButton(onClick = {
                    SnapEngine.store.update { it.copy(setupSkipped = it.setupSkipped + page) }
                    next()
                }) { Text("Skip") }
                Button(
                    enabled = page == SetupPage.METHOD || isDone(page),
                    onClick = {
                        if (page == SetupPage.ACTIONS) {
                            SnapEngine.store.update { it.copy(setupComplete = true) }
                            onFinish()
                        } else next()
                    },
                ) { Text(if (page == SetupPage.ACTIONS) "Finish" else "Next") }
            }
        }
    }
    if (showUnlockHint) UnlockHintDialog(onDismiss = {
        SnapEngine.store.update { it.copy(unlockHintShown = true) }
        showUnlockHint = false
    })
}

@Composable
private fun MethodOption(title: String, body: String, selected: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButton(selected = selected, onClick = null)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

internal fun Context.openNotificationSettings() = openSafely(
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
)
