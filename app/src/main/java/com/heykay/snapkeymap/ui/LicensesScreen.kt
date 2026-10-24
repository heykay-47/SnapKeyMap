package com.heykay.snapkeymap.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
fun LicensesScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val notices = remember(context) {
        JSONObject(context.assets.open("licenses/notices.json").bufferedReader().use { it.readText() })
    }
    val attributions = remember(context) {
        context.assets.open("licenses/${notices.getString("attributions")}").bufferedReader().use { it.readText() }
    }
    val licenseTexts = remember(context) {
        val licenses = notices.getJSONArray("licenses")
        List(licenses.length()) { index ->
            val license = licenses.getJSONObject(index)
            license.getString("name") to context.assets.open("licenses/${license.getString("file")}")
                .bufferedReader().use { it.readText() }
        }
    }
    BackHandler(onBack = onDone)
    SnapScaffold(title = "Open-source licences", onBack = onDone) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val dependencies = notices.getJSONArray("dependencies")
            repeat(dependencies.length()) { index ->
                val dependency = dependencies.getJSONObject(index)
                val url = dependency.getString("url")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(dependency.getString("name"), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() })
                        Text(dependency.getString("license"))
                        TextButton(onClick = { context.openSafely(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                            Text(url)
                        }
                    }
                }
            }
            Text("Attribution notices", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            Text(attributions)
            licenseTexts.forEach { (name, text) ->
                Text(name, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() })
                Text(text)
            }
        }
    }
}
