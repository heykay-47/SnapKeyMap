package com.heykay.snapkeymap.ui

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.heykay.snapkeymap.LearnResult
import com.heykay.snapkeymap.LogAccess
import com.heykay.snapkeymap.SnapAccessibilityService
import com.heykay.snapkeymap.SnapEngine
import kotlinx.coroutines.delay

private sealed interface LearnState {
    data object Intro : LearnState
    data class Running(val prompt: String, val detail: String, val progress: Float) : LearnState
    data class Finished(val result: LearnResult) : LearnState
}

@Composable
fun LearnScreen(onDone: () -> Unit) {
    var state by remember { mutableStateOf<LearnState>(LearnState.Intro) }
    var runs by remember { mutableIntStateOf(0) }
    var finishedAt by remember { mutableLongStateOf(Long.MAX_VALUE) }

    LaunchedEffect(runs) {
        if (runs == 0) return@LaunchedEffect
        SnapEngine.startLearning()

        suspend fun phase(prompt: String, detail: String, millis: Long): LongRange {
            val start = SnapEngine.now()
            while (true) {
                val elapsed = SnapEngine.now() - start
                if (elapsed >= millis) break
                state = LearnState.Running(prompt, detail, elapsed / millis.toFloat())
                delay(50)
            }
            return start..SnapEngine.now()
        }

        val baseline = phase("Hands off", "Don't touch the phone for a moment…", 5000)
        SnapEngine.markKeyReleased()
        val taps = (1..3).map { round ->
            val window = phase("Press the Snap Key once", "Press $round of 3", 3000)
            phase("Let go", "Get ready for the next one…", 1000)
            window
        }
        val hold = phase("Now press and hold it", "Keep holding until the bar is full", 3500)
        val result = SnapEngine.finishLearning(baseline, taps, hold)
        finishedAt = SnapEngine.now()
        state = LearnState.Finished(result)
    }
    DisposableEffect(Unit) {
        onDispose { SnapEngine.cancelLearning() }
    }

    SnapScaffold(title = "Teach your Snap Key", onBack = onDone) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val current = state) {
                LearnState.Intro -> Intro(onStart = { runs++ })
                is LearnState.Running -> Running(current, onCancel = {
                    runs = 0
                    SnapEngine.cancelLearning()
                    state = LearnState.Intro
                })
                is LearnState.Finished -> Finished(
                    result = current.result,
                    finishedAt = finishedAt,
                    onRetry = { runs++ },
                    onDone = onDone,
                )
            }
        }
    }
}

@Composable
private fun Intro(onStart: () -> Unit) {
    val context = LocalContext.current
    val config by SnapEngine.store.config.collectAsState()
    val access by SnapEngine.logAccess.collectAsState()
    val serviceOn by SnapAccessibilityService.running.collectAsState()
    val resumes by SnapEngine.uiResumes.collectAsState()
    val hasLogPermission = remember(resumes) { SnapEngine.hasLogPermission() }
    val logReady = !hasLogPermission || access == LogAccess.FULL

    Text(
        "You'll press the key 3 times, then hold it once. It takes about 20 seconds.",
        style = MaterialTheme.typography.bodyLarge,
    )
    NoteCard(
        icon = Icons.Rounded.Settings,
        title = "First: set Oppo's Snap Key action to None",
        body = "Open Settings › Snap Key and choose None. Then come back here.",
    ) {
        TextButton(onClick = { context.openPhoneSettings() }) { Text("Open phone settings") }
    }
    if (!serviceOn) {
        NoteCard(
            icon = Icons.Rounded.Warning,
            title = "Turn on the SnapKey Map service first",
            body = "Open accessibility settings and turn on SnapKey Map, then return here.",
            container = MaterialTheme.colorScheme.errorContainer,
        ) {
            TextButton(onClick = { context.openAccessibilitySettings() }) { Text("Open settings") }
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
                TextButton(onClick = { SnapEngine.connect() }) { Text("Connect") }
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
private fun Running(state: LearnState.Running, onCancel: () -> Unit) {
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
private fun Finished(result: LearnResult, finishedAt: Long, onRetry: () -> Unit, onDone: () -> Unit) {
    val last by SnapEngine.lastGesture.collectAsState()

    if (result == LearnResult.NothingFound) {
        NoteCard(
            icon = Icons.Rounded.Warning,
            title = "Couldn't find your Snap Key",
            body = "• If you use log access, make sure it's connected before you start.\n" +
                "• In Settings › Snap Key, pick a different option, then try again. Some options " +
                "keep the key hidden.\n" +
                "• Press the key once each time you're asked, and hold it at the end.",
            container = MaterialTheme.colorScheme.errorContainer,
        )
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        return
    }

    val body = when (result) {
        is LearnResult.KeyEventFound -> "Your Snap Key talks to apps directly, so no log access is needed."
        is LearnResult.LogLineFound -> if (result.canDetectHold) {
            "The app will spot your Snap Key in the system log."
        } else {
            "The app will spot your Snap Key in the system log. Your phone doesn't report holding the " +
                "key, so press and double press work, but press & hold doesn't."
        }
        LearnResult.NothingFound -> ""
    }
    NoteCard(
        icon = Icons.Rounded.CheckCircle,
        title = "Got it!",
        body = body,
        container = MaterialTheme.colorScheme.primaryContainer,
    )
    val tried = last?.takeIf { it.atMs > finishedAt }
    Text(
        if (tried == null) "Try it now: press the Snap Key." else "Detected: ${tried.gesture.title} ✓",
        style = MaterialTheme.typography.titleLarge,
    )
    if (result is LearnResult.LogLineFound) {
        Text(
            "Matched log line: ${result.signature.tag}: ${result.signature.message}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Teach it again") }
}
