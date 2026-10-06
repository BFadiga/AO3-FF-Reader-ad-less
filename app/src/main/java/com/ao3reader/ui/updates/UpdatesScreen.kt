package com.ao3reader.ui.updates

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ao3reader.data.local.UpdateEvent
import com.ao3reader.data.model.WorkIds
import com.ao3reader.ui.Navigator
import com.ao3reader.ui.components.Cover
import com.ao3reader.ui.components.ScreenScaffold
import com.ao3reader.ui.components.appContainer
import kotlinx.coroutines.launch

/** New chapters found on followed works, newest first. Tapping one opens the first new chapter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(nav: Navigator) {
    val container = appContainer()
    val updates by container.library.observeUpdates().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var refreshing by remember { mutableStateOf(false) }

    // Leaving the tab marks everything shown as seen (the unseen ones stay highlighted while you look).
    DisposableEffect(Unit) { onDispose { container.appScope.launch { container.library.markUpdatesSeen() } } }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            val result = container.updateChecker.checkAll(notify = false)
            refreshing = false
            snackbar.showSnackbar(
                when {
                    result.offline -> "Couldn't reach the sites."
                    result.updatedWorks == 0 -> "No new chapters in ${result.checked} followed works."
                    else -> "New chapters in ${result.updatedWorks} works."
                },
            )
        }
    }

    ScreenScaffold(
        title = "Updates",
        isTab = true,
        snackbar = snackbar,
        actions = {
            if (updates.isNotEmpty()) {
                IconButton(onClick = { scope.launch { container.library.clearUpdates() } }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all")
                }
            }
            IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, contentDescription = "Check for new chapters") }
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refresh() }, modifier = Modifier.padding(padding)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (updates.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.NotificationsNone, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.size(12.dp))
                            Text(
                                "No new chapters yet.\nStories you follow are checked in the background; pull down to check now.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                items(updates, key = { it.id }) { u -> UpdateRow(u) { nav.reader(u.workId, u.firstNewChapter) } }
            }
        }
    }
}

@Composable
private fun UpdateRow(u: UpdateEvent, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(u.coverUrl, u.title, u.rating, Modifier.size(width = 42.dp, height = 58.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                u.title, style = MaterialTheme.typography.titleSmall,
                fontWeight = if (u.seen) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(if (u.chaptersAdded == 1) "New chapter ${u.firstNewChapter}" else "${u.chaptersAdded} new chapters from ${u.firstNewChapter}")
                    if (u.latestChapterTitle.isNotBlank() && u.chaptersAdded == 1) append(": ${u.latestChapterTitle}")
                },
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(u.authors.joinToString().ifBlank { "Anonymous" }, WorkIds.site(u.workId).shortLabel,
                    DateUtils.getRelativeTimeSpanString(u.detectedAt).toString()).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (!u.seen) {
            Spacer(Modifier.width(8.dp))
            Spacer(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}
