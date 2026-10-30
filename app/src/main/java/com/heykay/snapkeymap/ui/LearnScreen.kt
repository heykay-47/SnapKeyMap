package com.heykay.snapkeymap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heykay.snapkeymap.LearnResult
import com.heykay.snapkeymap.LogAccess
import com.heykay.snapkeymap.SnapAccessibilityService
import com.heykay.snapkeymap.SnapEngine
import com.heykay.snapkeymap.TeachingFailure
import com.heykay.snapkeymap.TeachingHandle
import com.heykay.snapkeymap.TeachingSession
import com.heykay.snapkeymap.TeachingState
import kotlinx.coroutines.delay

private data class LearnPrompt(val prompt: String, val detail: String, val progress: Float)

@Composable
fun LearnScreen(onDone: () -> Unit) {
    var handle by remember { mutableStateOf<TeachingHandle?>(null) }
    var prompt by remember { mutableStateOf(LearnPrompt("Hands off", "Don't touch the phone for a moment…", 0f)) }
    val teaching by SnapEngine.teaching.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val cancel = {
        handle?.let(SnapEngine::cancelTeaching)
        handle = null
    }
    val leave = {
        cancel()
        SnapEngine.releaseTeachingLogs()
        onDone()
    }
    val retry = {
        prompt = LearnPrompt("Hands off", "Don't touch the phone for a moment…", 0f)
        handle = SnapEngine.beginTeaching()
    }

    LaunchedEffect(handle) {
        val owner = handle ?: return@LaunchedEffect

        suspend fun phase(title: String, detail: String, millis: Long): LongRange {
            val start = SnapEngine.now()
            while (true) {
                val elapsed = SnapEngine.now() - start
                if (elapsed >= millis) break
                prompt = LearnPrompt(title, detail, elapsed / millis.toFloat())
                delay(50)
            }
            return start..SnapEngine.now()
        }

        val baseline = phase("Hands off", "Don't touch the phone for a moment…", 5000)
        SnapEngine.markKeyReleased(owner)
        val taps = (1..3).map { round ->
            val window = phase("Press the Snap Key once", "Press $round of 3", 3000)
            phase("Let go", "Get ready for the next one…", 1000)
            window
        }
        val hold = phase("Now press and hold it", "Keep holding until the bar is full", 3500)
        SnapEngine.captureTeaching(owner, baseline, taps, hold)
    }
    val checking = teaching as? TeachingState.Checking
    LaunchedEffect(handle, checking?.startedAtMs) {
        val owner = handle ?: return@LaunchedEffect
        val start = checking?.startedAtMs ?: return@LaunchedEffect
        val duration = TeachingSession.CHECK_MS + TeachingSession.SETTLE_MS
        while (SnapEngine.now() - start < duration) {
            val elapsed = SnapEngine.now() - start
            prompt = LearnPrompt(
                if (elapsed < TeachingSession.CHECK_MS) "Press a different key once, then release it" else "Hands off",
                if (elapsed < TeachingSession.CHECK_MS) "A volume key is recommended. Do not touch the Snap Key."
                else "Checking the last events…",
                (elapsed / duration.toFloat()).coerceIn(0f, 1f),
            )
            delay(50)
        }
        SnapEngine.finishDifferentKeyCheck(owner)
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                cancel()
                SnapEngine.releaseTeachingLogs()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            cancel()
            SnapEngine.releaseTeachingLogs()
            lifecycle.removeObserver(observer)
        }
    }
    BackHandler(onBack = leave)

    SnapScaffold(title = "Teach your Snap Key", onBack = leave) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val current = teaching.takeIf { handle != null }) {
                null, TeachingState.Closed -> Intro(onStart = retry)
                TeachingState.Capturing, is TeachingState.Checking -> Running(prompt, onCancel = leave)
                else -> TeachingResult(
                    state = current,
                    onCheck = {
                        prompt = LearnPrompt(
                            "Press a different key once, then release it",
                            "A volume key is recommended. Do not touch the Snap Key.", 0f,
                        )
                        handle?.let(SnapEngine::checkDifferentKey)
                    },
                    onSave = { handle?.let { if (SnapEngine.saveTeaching(it)) onDone() } },
                    onRetry = retry,
                    onBack = leave,
                )
            }
        }
    }
}

@Composable
private fun Intro(onStart: () -> Unit) {
    val context = LocalContext.current
    val config by SnapEngine.store.config.collectAsStateWithLifecycle()
    val access by SnapEngine.logAccess.collectAsStateWithLifecycle()
    val serviceOn by SnapAccessibilityService.running.collectAsStateWithLifecycle()
    val resumes by SnapEngine.uiResumes.collectAsStateWithLifecycle()
    val hasLogPermission = remember(resumes) { SnapEngine.hasLogPermission() }
    val logReady = !hasLogPermission || access == LogAccess.FULL

    Text(
        "You'll press the key 3 times, hold it once, then check a different key before saving. It takes about 30 seconds.",
        style = MaterialTheme.typography.bodyLarge,
    )
    NoteCard(
        icon = Icons.Rounded.Settings,
        title = "First: set Oppo's Snap Key action to None",
        body = "Open Settings › Snap Key and choose None. Then come back here.",
    ) {
        CardTextButton("Open phone settings") { context.openPhoneSettings() }
    }
    if (!serviceOn) {
        NoteCard(
            icon = Icons.Rounded.Warning,
            title = "Turn on the SnapKey Map service first",
            body = "Open accessibility settings and turn on SnapKey Map, then return here.",
            container = MaterialTheme.colorScheme.errorContainer,
        ) {
            CardTextButton("Open settings") { context.openAccessibilitySettings() }
        }
    }
    if (config.needsLogAccess && !hasLogPermission) {
        NoteCard(
            icon = Icons.Rounded.Info,
            title = "No log access yet",
            body = "The app can only find the key if Android shares it directly. On Oppo phones it usually " +
                "doesn't, so you'll probably need the log access step.",
        )
    }
    if (hasLogPermission && access != LogAccess.FULL) {
        NoteCard(
            icon = Icons.Rounded.Info,
            title = "Connect to the system log",
            body = when (access) {
                LogAccess.WAITING -> "Tap \"Allow\" on the pop-up."
                LogAccess.LIMITED -> "Access was declined. Wait a minute, then tap Connect and choose Allow."
                else -> "Tap Connect, then choose Allow on the pop-up."
            },
        ) {
            if (access != LogAccess.WAITING) {
                CardTextButton("Connect") { SnapEngine.prepareTeachingLogs() }
            }
        }
    }
    Button(
        onClick = onStart,
        enabled = serviceOn && logReady,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Start") }
}

@Composable
private fun Running(state: LearnPrompt, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Rounded.TouchApp, contentDescription = null, modifier = Modifier.size(64.dp))
        Text(
            state.prompt,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(state.detail, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun TeachingResult(
    state: TeachingState,
    onCheck: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    if (state is TeachingState.Failed) {
        val title = when (state.reason) {
            TeachingFailure.NOT_FOUND -> "Couldn't find your Snap Key"
            TeachingFailure.COLLISION -> "Another key matched"
            TeachingFailure.NO_DIFFERENT_KEY -> "Couldn't observe a different key"
            TeachingFailure.ACCESS_LOST -> "The check was interrupted"
        }
        val guidance = when (state.reason) {
            TeachingFailure.NOT_FOUND -> "Make sure access is connected. Press the Snap Key only when asked, then hold it at the end."
            TeachingFailure.COLLISION -> "This candidate also matched during the different-key check. Try teaching again."
            TeachingFailure.NO_DIFFERENT_KEY -> "Try again and press and release a volume key during the check. A complete press and release must be observable."
            TeachingFailure.ACCESS_LOST -> "Restore the service, full system logs and any required Shizuku input, then try again. Reconnecting alone cannot complete this check."
        }
        NoteCard(
            icon = Icons.Rounded.Warning,
            title = title,
            body = "Nothing was saved. Your previous key is unchanged.\n\n$guidance",
            container = MaterialTheme.colorScheme.errorContainer,
        )
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        return
    }

    val result = when (state) {
        is TeachingState.Candidate -> state.result
        is TeachingState.Ready -> state.result
        else -> return
    }
    val body = when (result) {
        is LearnResult.KeyEventFound -> "Your Snap Key talks to apps directly, so no log access is needed."
        is LearnResult.LogLineFound -> if (result.inputKey != null) {
            "The app will use precise key input while Shizuku runs, with the system log as a fallback."
        } else if (result.canDetectHold) {
            "The app will spot your Snap Key in the system log."
        } else {
            "The app will spot your Snap Key in the system log. Your phone doesn't report holding the " +
                "key, so press and double press work, but press & hold and tap then hold don't."
        }
        LearnResult.NothingFound -> ""
    }
    NoteCard(
        icon = Icons.Rounded.CheckCircle,
        title = if (state is TeachingState.Ready) "Different-key check passed" else "Key found. Not saved yet.",
        body = body + if (state is TeachingState.Ready) {
            "\n\nThis check covers the key you pressed, not every possible key. Save this key?"
        } else {
            "\n\nCheck a different key before saving. Your previous key is still unchanged."
        },
        container = MaterialTheme.colorScheme.primaryContainer,
    )
    if (result is LearnResult.LogLineFound) {
        result.inputKey?.let { key ->
            Text("Matched input key: ${key.code}", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "Matched log line: ${result.signature.tag}: ${result.signature.message}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (state is TeachingState.Ready) {
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save key") }
    } else {
        Button(onClick = onCheck, modifier = Modifier.fillMaxWidth()) { Text("Check a different key") }
    }
    TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Teach it again") }
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
}
