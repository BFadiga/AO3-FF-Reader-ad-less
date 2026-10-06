package com.ao3reader.update

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ao3reader.BuildConfig
import com.ao3reader.ui.components.appContainer
import kotlinx.coroutines.launch

/** Settings row: shows the installed version, checks GitHub, and downloads/installs a newer build. */
@Composable
fun AppUpdateRow() {
    val updater = appContainer().appUpdater
    val state by updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val (title, subtitle) = when (val s = state) {
        UpdateState.Idle -> "Check for updates" to "Installed: ${BuildConfig.VERSION_NAME}"
        UpdateState.Checking -> "Checking GitHub…" to "Installed: ${BuildConfig.VERSION_NAME}"
        UpdateState.UpToDate -> "You're up to date" to "Installed: ${BuildConfig.VERSION_NAME}. Tap to check again."
        is UpdateState.Available -> "Update available: ${s.release.name}" to "Tap to download and install"
        is UpdateState.Downloading -> "Downloading ${s.release.name}…" to "${(s.progress * 100).toInt()}%"
        is UpdateState.Ready -> "Install ${s.release.name}" to
            if (s.needsPermission) "Allow \"Install unknown apps\" for this app, then tap here again" else "Downloaded. Tap to install"
        is UpdateState.Failed -> "Check for updates" to "Last try failed: ${s.message}"
    }
    Column {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable {
                scope.launch {
                    when (val s = state) {
                        is UpdateState.Available -> updater.downloadAndInstall(s.release)
                        is UpdateState.Ready -> updater.downloadAndInstall(s.release)
                        is UpdateState.Downloading, UpdateState.Checking -> Unit
                        else -> updater.check()
                    }
                }
            },
        )
        (state as? UpdateState.Downloading)?.let {
            LinearProgressIndicator(progress = { it.progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
    }
}

/** Asks once per app start when GitHub has a newer build. */
@Composable
fun AppUpdatePrompt() {
    val updater = appContainer().appUpdater
    val release by updater.prompt.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val r = release ?: return
    AlertDialog(
        onDismissRequest = { updater.prompt.value = null },
        title = { Text("Update available") },
        text = {
            Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                Text("${r.name} is ready on GitHub (you have ${BuildConfig.VERSION_NAME}).")
                if (r.notes.isNotBlank()) {
                    Text(r.notes, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                updater.prompt.value = null
                scope.launch { updater.downloadAndInstall(r) }
            }) { Text("Update") }
        },
        dismissButton = { TextButton(onClick = { updater.prompt.value = null }) { Text("Later") } },
    )
}
