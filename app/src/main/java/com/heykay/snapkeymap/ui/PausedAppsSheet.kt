package com.heykay.snapkeymap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heykay.snapkeymap.SnapEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PausedAppsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) { loadLauncherApps(context.packageManager, context.packageName) }
    }
    val config by SnapEngine.store.config.collectAsState()
    val initiallyPaused = remember { config.pausedApps }
    val sortedApps = remember(apps, initiallyPaused) {
        apps?.sortedWith(compareBy<AppEntry> { it.packageName !in initiallyPaused }.thenBy { it.label.lowercase() })
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetTitle(
                        "Pause in apps",
                        "SnapKey Map does nothing while these apps are open, so the key keeps its normal behaviour there.",
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (val count = config.pausedApps.size) {
                                0 -> "No apps paused"
                                1 -> "1 app paused"
                                else -> "$count apps paused"
                            },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = { SnapEngine.store.update { it.copy(pausedApps = emptySet()) } },
                            enabled = config.pausedApps.isNotEmpty(),
                        ) {
                            Icon(Icons.Rounded.ClearAll, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Clear all")
                        }
                    }
                }
            }
            val appList = sortedApps
            if (appList == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (appList.isEmpty()) {
                item { Text("No launcher apps found.") }
            } else {
                items(appList, key = { it.packageName }) { entry ->
                    val selected = entry.packageName in config.pausedApps
                    SelectableCard(
                        selected = selected,
                        onClick = {
                            SnapEngine.store.update {
                                it.copy(pausedApps = if (entry.packageName in it.pausedApps) {
                                    it.pausedApps - entry.packageName
                                } else it.pausedApps + entry.packageName)
                            }
                        },
                        modifier = Modifier.semantics {
                            role = Role.Checkbox
                            stateDescription = if (selected) "Paused" else "Not paused"
                            toggleableState = ToggleableState(selected)
                        },
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        AppIcon(entry.component)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            entry.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (selected) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
