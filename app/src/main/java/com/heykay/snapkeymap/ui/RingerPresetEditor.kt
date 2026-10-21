package com.heykay.snapkeymap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heykay.snapkeymap.RingerMode
import com.heykay.snapkeymap.SnapAction

@Composable
internal fun RingerPresetEditor(
    initial: List<RingerMode>?,
    presets: List<List<RingerMode>>,
    onDismiss: () -> Unit,
    onSave: (List<RingerMode>) -> Unit,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    var names by rememberSaveable { mutableStateOf(initial?.map { it.name }.orEmpty()) }
    val modes = names.map(RingerMode::valueOf)
    val duplicate = modes != initial && modes in presets
    val valid = modes.size in 2..3 && !duplicate
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New preset" else "Edit preset") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Tap modes in the order you want. Tap again to remove one.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RingerMode.entries.forEach { mode ->
                        val position = modes.indexOf(mode)
                        FilterChip(
                            selected = position >= 0,
                            onClick = { names = if (position >= 0) names - mode.name else names + mode.name },
                            label = { Text(if (position >= 0) "${position + 1}. ${mode.label}" else mode.label) },
                        )
                    }
                }
                Text(if (modes.size < 2) "Choose at least two modes" else SnapAction.Ringer(modes).label)
                if (duplicate) Text("This preset already exists.")
                if (initial != null && canDelete) {
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(modes) }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
