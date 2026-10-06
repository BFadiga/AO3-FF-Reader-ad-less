package com.ao3reader.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.text.format.DateUtils
import androidx.compose.material3.CircularProgressIndicator
import com.ao3reader.BuildConfig
import com.ao3reader.data.model.Site
import com.ao3reader.data.prefs.AccentColor
import com.ao3reader.data.prefs.AppSettings
import com.ao3reader.data.prefs.ListDensity
import com.ao3reader.data.prefs.ThemeMode
import com.ao3reader.ui.Navigator
import com.ao3reader.ui.components.ScreenScaffold
import com.ao3reader.ui.components.appContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(nav: Navigator) {
    val container = appContainer()
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val blocked by container.filters.blocked.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var downloadsBytes by remember { mutableLongStateOf(0L) }
    var confirmClear by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var syncNote by remember { mutableStateOf<String?>(null) }
    fun update(t: (AppSettings) -> AppSettings) = scope.launch { container.settings.update(t) }

    LaunchedEffect(Unit) { downloadsBytes = container.library.downloadsSize() }

    ScreenScaffold(title = "Settings", isTab = true) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Section("Accounts")
            AccountRow(Site.AO3, settings.ao3User, nav, onSignOut = { scope.launch { container.accounts.signOut(Site.AO3) } })
            AccountRow(Site.FFN, settings.ffnUser, nav, onSignOut = { scope.launch { container.accounts.signOut(Site.FFN) } })
            if (settings.ao3User != null || settings.ffnUser != null) {
                ListItem(
                    headlineContent = { Text(if (syncing) "Syncing…" else "Sync follows and likes now") },
                    supportingContent = {
                        Text(
                            syncNote ?: if (settings.lastSyncAt > 0) "Last synced ${DateUtils.getRelativeTimeSpanString(settings.lastSyncAt)}"
                            else "Imports what you follow and favorite on the sites",
                        )
                    },
                    trailingContent = { if (syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(enabled = !syncing) {
                        syncing = true
                        syncNote = null
                        container.appScope.launch {
                            val r = container.accounts.sync()
                            syncing = false
                            syncNote = buildString {
                                append("Added ${r.imported} works, updated details for ${r.details}.")
                                if (r.errors.isNotEmpty()) append(" Problems: ").append(r.errors.joinToString("; "))
                            }
                        }
                    },
                )
                SwitchRow(
                    "Mirror follows and likes on the sites",
                    settings.syncToSites,
                    "Following here also subscribes on AO3 / follows on FanFiction.net",
                ) { v -> update { it.copy(syncToSites = v) } }
            }

            Section("Appearance")
            Text("Theme", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ThemeMode.entries.forEach { m ->
                    FilterChip(selected = settings.themeMode == m, onClick = { update { it.copy(themeMode = m) } }, label = { Text(m.label) })
                }
            }
            SwitchRow("Pure black background", settings.amoledBlack, "Saves battery on OLED screens (dark theme only)") { v ->
                update { it.copy(amoledBlack = v) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow("Use wallpaper colors", settings.dynamicColor, "Material You colors instead of the accent below") { v ->
                    update { it.copy(dynamicColor = v) }
                }
            }
            Text("Accent color", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AccentColor.entries.forEach { a ->
                    val selected = settings.accent == a
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(a.argb))
                            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { update { it.copy(accent = a) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Default.Check, contentDescription = a.label, tint = Color.White)
                    }
                }
            }
            Text("List layout", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ListDensity.entries.forEach { d ->
                    FilterChip(selected = settings.density == d, onClick = { update { it.copy(density = d) } }, label = { Text(d.label) })
                }
            }
            SwitchRow("Show summaries in lists", settings.showSummaries) { v -> update { it.copy(showSummaries = v) } }
            SwitchRow("Show tags in lists", settings.showTags) { v -> update { it.copy(showTags = v) } }
            SwitchRow("Group library by series", settings.groupLibrary, "Works filed under their fandom, from both sites together") { v ->
                update { it.copy(groupLibrary = v) }
            }

            Section("Reading")
            NavRow("Reader settings", "Font, size, spacing, alignment, page color") { nav.readerSettings() }

            Section("Content filters")
            NavRow(
                "Blocked tags and authors",
                "${blocked.count { it.site == Site.AO3 }} on AO3 · ${blocked.count { it.site == Site.FFN }} on FanFiction.net",
            ) { nav.blocked() }
            SwitchRow(
                "Hide blocked works completely",
                settings.hideBlockedCompletely,
                "Otherwise they show as a small \"Hidden\" row you can tap to reveal",
            ) { v -> update { it.copy(hideBlockedCompletely = v) } }

            Section("Notifications")
            SwitchRow("Notify me about new chapters", settings.notificationsEnabled, "For works you follow") { v ->
                update { it.copy(notificationsEnabled = v) }
            }
            if (settings.notificationsEnabled) {
                Text("Check every", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 3, 6, 12, 24).forEach { h ->
                        FilterChip(
                            selected = settings.checkIntervalHours == h,
                            onClick = { update { it.copy(checkIntervalHours = h) } },
                            label = { Text(if (h == 1) "1 hour" else "$h hours") },
                        )
                    }
                }
                SwitchRow("Only check on Wi-Fi", settings.wifiOnlyChecks) { v -> update { it.copy(wifiOnlyChecks = v) } }
            }
            SwitchRow(
                "Auto-download new chapters",
                settings.autoDownloadUpdates,
                "Keeps offline copies of downloaded works up to date",
            ) { v -> update { it.copy(autoDownloadUpdates = v) } }

            Section("Storage")
            NavRow("Delete all downloads", "Offline copies use ${formatBytes(downloadsBytes)}") { confirmClear = true }

            Section("About")
            Text(
                "AO3 Reader ${BuildConfig.VERSION_NAME}. An unofficial, ad-free reader for Archive of Our Own and FanFiction.net. " +
                    "Not affiliated with either site or the Organization for Transformative Works. Please support AO3 by donating to the OTW.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all downloads?") },
            text = { Text("Works you follow stay in your library; only the offline copies are removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        container.library.deleteAllDownloads()
                        downloadsBytes = container.library.downloadsSize()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

private fun formatBytes(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    else -> "%.1f MB".format(b / (1024.0 * 1024.0))
}

@Composable
private fun Section(title: String) {
    Column {
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun AccountRow(site: Site, user: String?, nav: Navigator, onSignOut: () -> Unit) {
    ListItem(
        headlineContent = { Text(site.label) },
        supportingContent = {
            Text(
                when {
                    user == null -> "Not signed in"
                    user.isBlank() -> "Signed in"
                    else -> "Signed in as $user"
                },
            )
        },
        trailingContent = {
            if (user == null) TextButton(onClick = { nav.login(site) }) { Text("Sign in") }
            else TextButton(onClick = onSignOut) { Text("Sign out") }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
