package com.heykay.snapkeymap.ui

import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heykay.snapkeymap.DeviceAction
import com.heykay.snapkeymap.Gesture
import com.heykay.snapkeymap.RingerMode
import com.heykay.snapkeymap.Section
import com.heykay.snapkeymap.SnapAction
import com.heykay.snapkeymap.SnapEngine
import com.heykay.snapkeymap.canUseShizuku
import com.heykay.snapkeymap.editRingerPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class AppEntry(val label: String, val component: ComponentName) {
    val packageName: String get() = component.packageName
}

internal fun loadActivities(pm: PackageManager, intent: Intent, exclude: String): List<AppEntry> =
    pm.queryIntentActivities(intent, 0)
        .map { AppEntry(it.loadLabel(pm).toString(), ComponentName(it.activityInfo.packageName, it.activityInfo.name)) }
        .filter { it.packageName != exclude }
        .sortedBy { it.label.lowercase() }

internal fun loadLauncherApps(pm: PackageManager, exclude: String): List<AppEntry> =
    loadActivities(pm, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), exclude)
        .distinctBy { it.packageName }

@Composable
fun ActionPickerScreen(gesture: Gesture, onDone: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val mindSpaceInstalled = remember(pm) {
        runCatching { pm.getPackageInfo("com.oplus.aimemory", 0) }.isSuccess
    }
    val config by SnapEngine.store.config.collectAsStateWithLifecycle()
    val current = config.action(gesture)
    var query by rememberSaveable { mutableStateOf("") }
    var showLinkDialog by rememberSaveable { mutableStateOf(false) }
    var showShortcuts by rememberSaveable { mutableStateOf(false) }
    var showRingerDialog by rememberSaveable { mutableStateOf(false) }
    var showDndDialog by rememberSaveable { mutableStateOf(false) }

    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) {
            loadLauncherApps(pm, context.packageName)
        }
    }
    val shortcutApps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) {
            loadActivities(pm, Intent(Intent.ACTION_CREATE_SHORTCUT), context.packageName)
        }
    }

    fun choose(action: SnapAction) {
        SnapEngine.store.update { it.copy(actions = it.actions + (gesture to action)) }
        val needsDnd = action is SnapAction.Ringer && RingerMode.SILENT in action.modes && !canUseShizuku() ||
            action == SnapAction.Device(DeviceAction.DO_NOT_DISTURB)
        if (needsDnd && !context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) {
            showDndDialog = true
        } else onDone()
    }

    val shortcutResult = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) return@rememberLauncherForActivityResult
        @Suppress("DEPRECATION")
        val shortcut = IntentCompat.getParcelableExtra(data, Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
        if (shortcut == null) {
            Toast.makeText(context, "That app's shortcuts can't be used here", Toast.LENGTH_LONG).show()
        } else {
            @Suppress("DEPRECATION")
            val name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: "Shortcut"
            choose(SnapAction.OpenShortcut(shortcut.toUri(Intent.URI_INTENT_SCHEME), name))
        }
    }

    val contactResult = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data
        val contact = runCatching {
            uri?.let {
                context.contentResolver.query(it, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val number = cursor.getString(cursor.getColumnIndexOrThrow(Phone.NUMBER))?.trim()
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME))?.trim()
                    if (number.isNullOrBlank()) null else SnapAction.CallContact(number, name?.takeIf { it.isNotBlank() } ?: number)
                }
            }
        }.getOrNull()
        if (contact == null) {
            Toast.makeText(context, "Couldn't read that contact's phone number", Toast.LENGTH_LONG).show()
        } else {
            choose(contact)
        }
    }

    val search = query.trim()
    fun matches(text: String) = search.isEmpty() || text.contains(search, ignoreCase = true)
    val filteredApps = remember(apps, search) { apps?.filter { matches(it.label) } }
    val actionsBySection = remember(search, mindSpaceInstalled) {
        Section.entries.associateWith { section ->
            DeviceAction.entries.filter {
                it.section == section && matches(it.label) &&
                    (it != DeviceAction.SEND_SCREENSHOT_TO_MIND_SPACE || mindSpaceInstalled)
            }
        }
    }

    SnapScaffold(title = gesture.title, onBack = onDone) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding).imePadding(),
            contentPadding = padding,
        ) {
            if (current != SnapAction.NoAction) {
                item(key = "current") {
                    PickRow(
                        icon = { VectorIcon(actionIcon(current)) },
                        title = "Current",
                        subtitle = current.label,
                        selected = true,
                    ) { onDone() }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search actions and apps") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (matches("Do nothing")) {
                item {
                    PickRow(
                        icon = { Icon(Icons.Rounded.Block, contentDescription = null) },
                        title = "Do nothing",
                        selected = current == SnapAction.NoAction,
                    ) { choose(SnapAction.NoAction) }
                }
            }
            Section.entries.forEach { section ->
                val actions = actionsBySection.getValue(section)
                val showRinger = section == Section.PHONE && matches("Ringer mode, Ring, Vibrate, Silent")
                if (actions.isNotEmpty() || showRinger) {
                    item { SectionHeader(section.title, Modifier.padding(horizontal = 16.dp)) }
                    if (showRinger) item {
                        PickRow(
                            icon = { VectorIcon(actionIcon(SnapAction.Ringer(RingerMode.entries.toList()))) },
                            title = "Ringer mode",
                            subtitle = (current as? SnapAction.Ringer)?.label ?: "Choose which modes to cycle through",
                            selected = current is SnapAction.Ringer,
                        ) { showRingerDialog = true }
                    }
                    items(actions, key = { "device:" + it.name }) { device ->
                        val action = SnapAction.Device(device)
                        PickRow(
                            icon = { VectorIcon(actionIcon(action)) },
                            title = device.label,
                            selected = current == action,
                        ) { choose(action) }
                    }
                }
            }

            val showCall = matches("Call a contact, dialer, phone number")
            val showLink = matches("Open a link, website, URL, deep link")
            val showShortcut = matches("App shortcut, direct dial, Tasker")
            if (showCall || showLink || showShortcut) {
                item { SectionHeader("More", Modifier.padding(horizontal = 16.dp)) }
                if (showCall) item {
                    PickRow(
                        icon = { VectorIcon(Icons.Rounded.Call) },
                        title = "Call a contact…",
                        subtitle = (current as? SnapAction.CallContact)?.label ?: "Open the dialer with their number filled in",
                        selected = current is SnapAction.CallContact,
                    ) {
                        try {
                            contactResult.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "No contact picker is available", Toast.LENGTH_SHORT).show()
                        } catch (e: SecurityException) {
                            Toast.makeText(context, "Couldn't open the contact picker", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                if (showLink) item {
                    PickRow(
                        icon = { VectorIcon(Icons.Rounded.Link) },
                        title = "Open a link…",
                        subtitle = (current as? SnapAction.OpenLink)?.url
                            ?: "A website, or a deep link like a WhatsApp chat or Spotify playlist",
                        selected = current is SnapAction.OpenLink,
                    ) { showLinkDialog = true }
                }
                if (showShortcut) item {
                    PickRow(
                        icon = { VectorIcon(Icons.Rounded.Bolt) },
                        title = "App shortcut…",
                        subtitle = (current as? SnapAction.OpenShortcut)?.name
                            ?: "Direct dial, a Tasker task, directions home, and more",
                        selected = current is SnapAction.OpenShortcut,
                    ) { showShortcuts = !showShortcuts }
                }
                if (showShortcut && showShortcuts) {
                    val list = shortcutApps
                    if (list == null) {
                        item { Loading() }
                    } else {
                        if (list.isEmpty()) {
                            item {
                                Text(
                                    "None of your apps offer shortcuts.",
                                    modifier = Modifier.padding(horizontal = 72.dp, vertical = 8.dp),
                                )
                            }
                        }
                        items(list, key = { "shortcut:" + it.component.flattenToString() }) { entry ->
                            PickRow(
                                icon = { AppIcon(entry.component) },
                                title = entry.label,
                                selected = false,
                                indent = true,
                            ) {
                                try {
                                    shortcutResult.launch(Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(entry.component))
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(context, "Couldn't open ${entry.label}", Toast.LENGTH_SHORT).show()
                                } catch (e: SecurityException) {
                                    Toast.makeText(context, "Couldn't open ${entry.label}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Open an app", Modifier.padding(horizontal = 16.dp)) }
            val appList = filteredApps
            if (appList == null) {
                item { Loading() }
            } else {
                items(appList, key = { "app:" + it.packageName }) { entry ->
                    PickRow(
                        icon = { AppIcon(entry.component) },
                        title = entry.label,
                        selected = current is SnapAction.OpenApp && current.packageName == entry.packageName,
                    ) { choose(SnapAction.OpenApp(entry.packageName, entry.label)) }
                }
            }
        }
    }

    if (showLinkDialog) {
        LinkDialog(
            initial = (current as? SnapAction.OpenLink)?.url ?: "https://",
            onDismiss = { showLinkDialog = false },
            onConfirm = { url ->
                showLinkDialog = false
                choose(SnapAction.OpenLink(url))
            },
        )
    }
    if (showRingerDialog) {
        RingerSheet(
            presets = config.ringerPresets,
            selected = (current as? SnapAction.Ringer)?.modes,
            assigned = config.actions.values.filterIsInstance<SnapAction.Ringer>().map { it.modes }.toSet(),
            onPick = { modes ->
                showRingerDialog = false
                choose(SnapAction.Ringer(modes))
            },
            onSave = { old, modes ->
                SnapEngine.store.update {
                    if (old == null) it.copy(ringerPresets = it.ringerPresets + listOf(modes)) else it.editRingerPreset(old, modes)
                }
            },
            onDelete = { modes ->
                SnapEngine.store.update { it.copy(ringerPresets = it.ringerPresets - listOf(modes).toSet()) }
            },
            onDismiss = { showRingerDialog = false },
        )
    }
    if (showDndDialog) DndAccessDialog(onDone, forSilent = current is SnapAction.Ringer)
}

@Composable
private fun PickRow(
    icon: @Composable () -> Unit,
    title: String,
    selected: Boolean,
    subtitle: String? = null,
    indent: Boolean = false,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = if (indent) 40.dp else 0.dp),
        leadingContent = icon,
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle, maxLines = 2) }
        } else {
            null
        },
        trailingContent = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
    )
}

@Composable
private fun VectorIcon(icon: ImageVector) {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

private val iconCache = LruCache<String, ImageBitmap>(300)

@Composable
internal fun AppIcon(component: ComponentName) {
    val pm = LocalContext.current.packageManager
    val sizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val key = "${component.flattenToString()}:$sizePx"
    val icon by produceState<ImageBitmap?>(iconCache.get(key), key) {
        value = iconCache.get(key)
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { pm.getActivityIcon(component).toBitmap(sizePx, sizePx).asImageBitmap() }
                    .getOrNull()
                    ?.also { iconCache.put(key, it) }
            }
        }
    }
    Box(Modifier.size(40.dp)) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
private fun LinkDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val valid = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*:.+""").matches(text.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open a link") },
        text = {
            Box(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Link") },
                    supportingText = { Text("For example spotify: or\nsms:+15551234567?body=On my way") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
