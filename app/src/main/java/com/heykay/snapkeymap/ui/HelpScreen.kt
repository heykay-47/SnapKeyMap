package com.heykay.snapkeymap.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun HelpScreen(onDone: () -> Unit, onRunSetup: () -> Unit) {
    val context = LocalContext.current
    SnapScaffold(title = "Help", onBack = onDone) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NoteCard(
                icon = Icons.Rounded.RestartAlt,
                title = "Run setup again",
                body = "Go through the setup wizard again, for example after reinstalling Shizuku or teaching a new key. Your mappings stay.",
                container = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Button(onClick = onRunSetup) { Text("Run setup") }
            }
            HelpSection(
                icon = Icons.Rounded.Settings,
                title = "Getting started",
                initiallyExpanded = true,
                body = "The setup wizard saves your progress and resumes at the first unfinished step.\n\n" +
                    "1. Choose Shizuku or Computer (ADB).\n\n" +
                    "2. Grant log access. Shizuku must be running and allowed for prompt-free reading; " +
                    "otherwise allow device logs when Android asks.\n\n" +
                    "3. Enable accessibility to run actions and open apps from the background. It cannot read screen content.\n\n" +
                    "4. Optionally allow background operation to reduce ColorOS interruptions.\n\n" +
                    "5. Optionally allow notifications for detection paused alerts.\n\n" +
                    "6. Set Oppo's Snap Key action to None. Teach the key: press three times, then hold once.\n\n" +
                    "7. Choose Press, Double press, Press & hold and Tap, then hold actions, then tap Finish. Test them and check Last:.\n\n" +
                    "Tap the lock next to a gesture to run it only when your phone is unlocked.",
            )
            HelpSection(
                icon = Icons.AutoMirrored.Rounded.HelpOutline,
                title = "Troubleshooting",
                body = "Paused after a restart? Reopen the app, tap Connect if needed and allow device logs. " +
                    "The one-time grant does not need repeating. While Shizuku runs and SnapKey Map is " +
                    "allowed in it, no device-logs prompt is needed. Otherwise Android asks for one-time " +
                    "access after each app or phone restart and may ask again on reconnect.\n\n" +
                    "Key not detected? Teach the Snap Key again and set Oppo's own action to None.\n\n" +
                    "Double press read as two singles? Update the app, teach the key again and check Last:. " +
                    "If it says double press, check the assigned action.\n\n" +
                    "ColorOS closing the app? Allow background activity in App info > Battery and turn off " +
                    "battery optimisation. Reliability still depends on ColorOS.\n\n" +
                    "Grant refused with GRANT_RUNTIME_PERMISSIONS? In Developer options, turn on Disable " +
                    "permission monitoring, read the warning and RESTART the phone. Then retry the grant.",
            )
            HelpSection(
                icon = Icons.Rounded.Info,
                title = "Is it safe?",
                body = "The app has no INTERNET permission. It does not send your logs or settings off the phone. " +
                    "Apps and websites you open handle their own data.\n\n" +
                    "Log access can see the whole live system log. Lines are checked in memory, not saved as a log. " +
                    "While teaching, observations stay in memory with message digits replaced by #. " +
                    "One learned signature is saved: tag, shortened message and source UID, plus hold timing. " +
                    "Key lines must match that source UID, so other apps cannot fake presses by copying a line.\n\n" +
                    "Accessibility cannot read window content. Settings are private to the app; Android backup is off.\n\n" +
                    "If you allow SnapKey Map in Shizuku, the app also uses Shizuku to run logcat as the " +
                    "shell user. Shizuku must be running for this. Lines are still checked in memory " +
                    "and discarded, not saved as a log, with the teaching exception above. " +
                    "Through Shizuku the app also reads key input events to detect the Snap Key precisely, " +
                    "and switches the ringer mode so Silent doesn't turn on Do Not Disturb. " +
                    "Other mapped actions don't use Shizuku.\n\n" +
                    "Permissions: read device logs; vibrate; show notifications; access Do Not Disturb controls; " +
                    "ask to ignore battery optimisation; ask Shizuku for the one-time log grant and shell log reading.\n\n" +
                    "The code is open source. Provided as is, without warranty. READ_LOGS granted over adb is " +
                    "powerful. Give it only to apps you trust.",
                onSource = {
                    context.openSafely(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/heykay-47/SnapKeyMap")))
                },
            )
            Button(
                onClick = {
                    context.openSafely(Intent(Intent.ACTION_VIEW, Uri.parse("https://heykay-47.github.io/SnapKeyMap/help.html")))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Full help on the website") }
        }
    }
}

@Composable
private fun HelpSection(
    icon: ImageVector,
    title: String,
    body: String,
    initiallyExpanded: Boolean = false,
    onSource: (() -> Unit)? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    NoteCard(
        icon = icon,
        title = title,
        body = if (expanded) body else "Show the essentials.",
    ) {
        CardTextButton(
            if (expanded) "Hide" else "Read",
            modifier = Modifier.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
        ) { expanded = !expanded }
        if (expanded && onSource != null) {
            CardTextButton("Source code", onClick = onSource)
        }
    }
}
