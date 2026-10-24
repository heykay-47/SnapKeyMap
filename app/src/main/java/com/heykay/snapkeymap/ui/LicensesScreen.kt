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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun LicensesScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onDone)
    SnapScaffold(title = "Open-source licences", onBack = onDone) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            listOf(
                Triple("Shizuku API", "RikkaApps", "https://github.com/RikkaApps/Shizuku-API"),
                Triple("HiddenApiBypass", "LSPosed", "https://github.com/LSPosed/AndroidHiddenApiBypass"),
                Triple("AndroidX / Jetpack Compose", "Google", "https://github.com/androidx/androidx"),
            ).forEach { (name, author, url) ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Text("$author · Apache License 2.0")
                        TextButton(onClick = { context.openSafely(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                            Text(url)
                        }
                    }
                }
            }
            Text("Apache License, Version 2.0", style = MaterialTheme.typography.titleMedium)
            Text("Licensed under the Apache License, Version 2.0 (the \"License\"); " +
                "you may not use this file except in compliance with the License. " +
                "You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0\n\n" +
                "Unless required by applicable law or agreed to in writing, software distributed under the License " +
                "is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, " +
                "either express or implied. See the License for the specific language governing permissions " +
                "and limitations under the License.")
            TextButton(onClick = {
                context.openSafely(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.apache.org/licenses/LICENSE-2.0")))
            }) { Text("Read the full licence") }
        }
    }
}
