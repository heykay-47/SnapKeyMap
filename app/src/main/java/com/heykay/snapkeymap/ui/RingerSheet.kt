package com.heykay.snapkeymap.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.heykay.snapkeymap.RingerMode
import com.heykay.snapkeymap.SnapAction

private class Editing(val initial: List<RingerMode>?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RingerSheet(
    presets: List<List<RingerMode>>,
    selected: List<RingerMode>?,
    assigned: Set<List<RingerMode>>,
    onPick: (List<RingerMode>) -> Unit,
    onSave: (old: List<RingerMode>?, new: List<RingerMode>) -> Unit,
    onDelete: (List<RingerMode>) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<Editing?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        val target = editing
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (target == null) {
                PresetList(presets, selected, onPick, onEdit = { editing = Editing(it) }, onNew = { editing = Editing(null) })
            } else {
                PresetEditor(
                    initial = target.initial,
                    presets = presets,
                    canDelete = target.initial != null && presets.size > 1 && target.initial !in assigned,
                    onCancel = { editing = null },
                    onSave = { modes ->
                        onSave(target.initial, modes)
                        editing = null
                    },
                    onDelete = {
                        target.initial?.let(onDelete)
                        editing = null
                    },
                )
            }
        }
    }
}

@Composable
private fun PresetList(
    presets: List<List<RingerMode>>,
    selected: List<RingerMode>?,
    onPick: (List<RingerMode>) -> Unit,
    onEdit: (List<RingerMode>) -> Unit,
    onNew: () -> Unit,
) {
    SheetTitle("Ringer mode", "Each press moves to the next mode, then starts over.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        presets.forEach { modes ->
            PresetCard(modes, selected = modes == selected, onClick = { onPick(modes) }, onEdit = { onEdit(modes) })
        }
    }
    FilledTonalButton(onClick = onNew, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("New preset")
    }
}

@Composable
private fun PresetCard(modes: List<RingerMode>, selected: Boolean, onClick: () -> Unit, onEdit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) colors.secondaryContainer else colors.surfaceContainerHighest,
        ),
        border = if (selected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeSequence(modes, Modifier.weight(1f))
            if (selected) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = colors.primary)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit ${SnapAction.Ringer(modes).label}")
            }
        }
    }
}

@Composable
private fun PresetEditor(
    initial: List<RingerMode>?,
    presets: List<List<RingerMode>>,
    canDelete: Boolean,
    onCancel: () -> Unit,
    onSave: (List<RingerMode>) -> Unit,
    onDelete: () -> Unit,
) {
    var names by rememberSaveable { mutableStateOf(initial?.map { it.name }.orEmpty()) }
    val modes = names.map(RingerMode::valueOf)
    val duplicate = modes != initial && modes in presets
    val colors = MaterialTheme.colorScheme

    SheetTitle(
        if (initial == null) "New preset" else "Edit preset",
        "Tap the modes in the order you want. Tap one again to remove it.",
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RingerMode.entries.forEach { mode ->
            val position = modes.indexOf(mode)
            ModeTile(mode, position = position.takeIf { it >= 0 }?.plus(1), modifier = Modifier.weight(1f)) {
                names = if (position >= 0) names - mode.name else names + mode.name
            }
        }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Preview", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            if (modes.isEmpty()) {
                Text("No modes yet", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            } else {
                ModeSequence(modes, Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally))
            }
        }
    }
    val problem = when {
        modes.size == 1 -> "Add at least one more mode."
        duplicate -> "You already have this preset."
        else -> null
    }
    if (problem != null) {
        Text(problem, style = MaterialTheme.typography.bodyMedium, color = colors.error)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (canDelete) {
            TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = colors.error)) {
                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete")
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text("Cancel") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = { onSave(modes) }, enabled = modes.size in 2..3 && !duplicate) { Text("Save") }
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ModeSequence(
    modes: List<RingerMode>,
    modifier: Modifier = Modifier,
    arrangement: Arrangement.Horizontal = Arrangement.spacedBy(4.dp),
) {
    Row(modifier, horizontalArrangement = arrangement) {
        modes.forEachIndexed { index, mode ->
            if (index > 0) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp).size(16.dp),
                )
            }
            Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ModeBadge(mode)
                Spacer(Modifier.height(4.dp))
                Text(mode.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ModeBadge(mode: RingerMode) {
    Box(
        Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(mode.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ModeTile(mode: RingerMode, position: Int?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val chosen = position != null
    Surface(
        selected = chosen,
        onClick = onClick,
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (chosen) colors.primaryContainer else colors.surfaceContainerHighest,
        contentColor = if (chosen) colors.onPrimaryContainer else colors.onSurface,
        border = if (chosen) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Box {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(mode.icon, contentDescription = null)
                Text(mode.label, style = MaterialTheme.typography.labelLarge)
            }
            if (position != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).background(colors.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$position", style = MaterialTheme.typography.labelMedium, color = colors.onPrimary)
                }
            }
        }
    }
}

private val RingerMode.icon: ImageVector
    get() = when (this) {
        RingerMode.RING -> Icons.Rounded.NotificationsActive
        RingerMode.VIBRATE -> Icons.Rounded.Vibration
        RingerMode.SILENT -> Icons.Rounded.NotificationsOff
    }
