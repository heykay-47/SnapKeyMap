package com.heykay.snapkeymap.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private data class NoticeEntry(
    val id: String,
    val title: String,
    val document: String,
    val displayName: String = title,
    val license: String? = null,
    val projectUrl: String? = null,
    val versions: String? = null,
)

@Composable
fun LicensesScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val notices = remember(context) {
        JSONObject(context.assets.open("licenses/notices.json").bufferedReader().use { it.readText() })
    }
    val licenseEntries = remember(notices) {
        val licenses = notices.getJSONArray("licenses")
        List(licenses.length()) { index ->
            val license = licenses.getJSONObject(index)
            NoticeEntry(
                id = "license:$index",
                title = license.getString("name"),
                document = license.getString("file"),
            )
        }
    }
    val dependencies = remember(notices, licenseEntries) {
        val dependencies = notices.getJSONArray("dependencies")
        List(dependencies.length()) { index ->
            val dependency = dependencies.getJSONObject(index)
            val license = dependency.getString("license")
            NoticeEntry(
                id = "dependency:$index",
                title = dependency.getString("name"),
                document = licenseEntries.first { it.title == license }.document,
                displayName = dependency.optString("display_name", dependency.getString("name")),
                license = license,
                projectUrl = dependency.getString("url"),
                versions = dependency.optString("versions").takeIf { it.isNotEmpty() },
            )
        }
    }
    val attributionEntry = remember(notices) {
        NoticeEntry("attributions", "Complete attribution notices", notices.getString("attributions"))
    }
    var selectedEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedEntry = (dependencies + attributionEntry + licenseEntries).firstOrNull { it.id == selectedEntryId }
    val indexScroll = rememberScrollState()
    val onBack: () -> Unit = { if (selectedEntry != null) selectedEntryId = null else onDone() }

    BackHandler(onBack = onBack)
    SnapScaffold(title = "Open-source licences", onBack = onBack) { padding ->
        if (selectedEntry == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(indexScroll).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Libraries", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() })
                Card(Modifier.fillMaxWidth()) {
                    dependencies.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider()
                        NoticeRow(entry) { selectedEntryId = entry.id }
                    }
                }
                Card(Modifier.fillMaxWidth()) {
                    NoticeRow(attributionEntry) { selectedEntryId = attributionEntry.id }
                }
                Text("Licence texts", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() })
                Card(Modifier.fillMaxWidth()) {
                    licenseEntries.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider()
                        NoticeRow(entry) { selectedEntryId = entry.id }
                    }
                }
            }
        } else {
            key(selectedEntry.id) {
                val text = remember(context, selectedEntry.document) {
                    context.assets.open("licenses/${selectedEntry.document}").bufferedReader().use { it.readText() }
                }
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding)
                        .verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(selectedEntry.title, style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() })
                    selectedEntry.versions?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    selectedEntry.projectUrl?.let { url ->
                        CardTextButton("Project website") {
                            context.openSafely(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                        CardTextButton("Read complete attribution notices") {
                            selectedEntryId = attributionEntry.id
                        }
                    }
                    selectedEntry.license?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() })
                    }
                    SelectionContainer {
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeRow(entry: NoticeEntry, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(entry.displayName) },
        supportingContent = if (entry.license != null) { { Text(entry.license) } } else null,
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
    )
}
