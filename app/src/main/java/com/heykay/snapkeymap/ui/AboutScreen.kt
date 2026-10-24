package com.heykay.snapkeymap.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.EmojiFoodBeverage
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.heykay.snapkeymap.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AboutScreen(onDone: () -> Unit, onLicenses: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val version by produceState("", context, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            value = withContext(Dispatchers.Default) {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            }
        }
    }
    val repoUrl = stringResource(R.string.repository_url)
    val profileUrl = stringResource(R.string.author_profile_url)
    val teaUrl = stringResource(R.string.buy_me_a_tea_url)
    val sponsorsUrl = stringResource(R.string.github_sponsors_url)

    SnapScaffold(title = stringResource(R.string.about_title), onBack = onDone) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.author_avatar),
                            contentDescription = stringResource(R.string.author_photo_description),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                            Text(
                                stringResource(R.string.app_version, version) + " · " + stringResource(R.string.author_credit),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(stringResource(R.string.about_description), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onLicenses) { Text("Open-source licences") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinkButton(stringResource(R.string.view_repository), Modifier.weight(1f)) {
                            context.openSafely(Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl)))
                        }
                        LinkButton(stringResource(R.string.view_author_profile), Modifier.weight(1f)) {
                            context.openSafely(Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl)))
                        }
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Favorite, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.support_heading), style = MaterialTheme.typography.titleLarge)
                    }
                    Text(stringResource(R.string.support_description), style = MaterialTheme.typography.bodyMedium)
                    SupportButton(Icons.Rounded.EmojiFoodBeverage, stringResource(R.string.buy_me_a_tea)) {
                        context.openSafely(Intent(Intent.ACTION_VIEW, Uri.parse(teaUrl)))
                    }
                    SupportButton(Icons.Rounded.Favorite, stringResource(R.string.github_sponsors)) {
                        context.openSafely(Intent(Intent.ACTION_VIEW, Uri.parse(sponsorsUrl)))
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 12.dp)) {
        Text(text, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SupportButton(icon: ImageVector, text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}
