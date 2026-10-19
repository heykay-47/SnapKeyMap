package com.heykay.snapkeymap.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.heykay.snapkeymap.DeviceAction
import com.heykay.snapkeymap.Gesture
import com.heykay.snapkeymap.R
import com.heykay.snapkeymap.SnapAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
            )
        },
        content = content,
    )
}

fun gestureIcon(gesture: Gesture): ImageVector = when (gesture) {
    Gesture.SINGLE -> Icons.Rounded.TouchApp
    Gesture.DOUBLE -> Icons.Rounded.Repeat
    Gesture.LONG -> Icons.Rounded.Timer
}

fun actionIcon(action: SnapAction): ImageVector = when (action) {
    SnapAction.NoAction -> Icons.Rounded.Block
    is SnapAction.OpenApp -> Icons.Rounded.Apps
    is SnapAction.OpenShortcut -> Icons.Rounded.Bolt
    is SnapAction.OpenLink -> Icons.Rounded.Link
    is SnapAction.Device -> when (action.action) {
        DeviceAction.FLASHLIGHT -> Icons.Rounded.FlashlightOn
        DeviceAction.RINGER_CYCLE -> Icons.Rounded.Vibration
        DeviceAction.DO_NOT_DISTURB -> Icons.Rounded.DoNotDisturbOn
        DeviceAction.SCREENSHOT -> Icons.Rounded.Screenshot
        DeviceAction.LOCK_SCREEN -> Icons.Rounded.Lock
        DeviceAction.NOTIFICATIONS -> Icons.Rounded.Notifications
        DeviceAction.QUICK_SETTINGS -> Icons.Rounded.Tune
        DeviceAction.RECENT_APPS -> Icons.Rounded.Dashboard
        DeviceAction.HOME -> Icons.Rounded.Home
        DeviceAction.BACK -> Icons.AutoMirrored.Rounded.ArrowBack
        DeviceAction.POWER_MENU -> Icons.Rounded.PowerSettingsNew
        DeviceAction.SPLIT_SCREEN -> Icons.Rounded.VerticalSplit
        DeviceAction.PLAY_PAUSE -> Icons.Rounded.PlayArrow
        DeviceAction.NEXT_TRACK -> Icons.Rounded.SkipNext
        DeviceAction.PREVIOUS_TRACK -> Icons.Rounded.SkipPrevious
        DeviceAction.VOLUME_PANEL -> Icons.AutoMirrored.Rounded.VolumeUp
        DeviceAction.CAMERA -> Icons.Rounded.PhotoCamera
        DeviceAction.VIDEO_CAMERA -> Icons.Rounded.Videocam
        DeviceAction.ASSISTANT -> Icons.Rounded.Mic
        DeviceAction.INTERNET_PANEL -> Icons.Rounded.Wifi
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** A tinted card with an icon, a title and some text, for tips and status messages. */
@Composable
fun NoteCard(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    actions: @Composable () -> Unit = {},
) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    actions()
                }
            }
        }
    }
}

fun Context.openSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "Couldn't open that screen on this phone", Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        Toast.makeText(this, "Couldn't open that screen on this phone", Toast.LENGTH_SHORT).show()
    }
}

fun Context.openPhoneSettings() = openSafely(Intent(Settings.ACTION_SETTINGS))

fun Context.openAccessibilitySettings() = openSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

fun Context.openAppInfo() =
    openSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))

fun Context.copyText(text: String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("SnapKey Map", text))
    Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
}

fun Context.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    openSafely(Intent.createChooser(send, null))
}
