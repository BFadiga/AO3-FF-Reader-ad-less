package com.ao3reader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ao3reader.data.local.LibraryWork
import com.ao3reader.data.prefs.AppSettings
import com.ao3reader.ui.Navigator
import com.ao3reader.ui.components.Cover
import com.ao3reader.ui.components.ScreenScaffold
import com.ao3reader.ui.components.appContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class LibraryFilter(val label: String) {
    ALL("All"), NEW("New chapters"), READING("Reading"), UNREAD("Not started"), LIKED("Liked"), DOWNLOADED("Downloaded"), COMPLETE("Complete")
}

private enum class LibrarySort(val label: String) { ADDED("Recently added"), UPDATED("Recently updated"), TITLE("Title"), AUTHOR("Author") }

/**
 * The series a work is filed under: its fandom with AO3's medium suffixes trimmed, so
 * "Dragon Ball Z (Anime)" on AO3 and "Dragon Ball Z" on FanFiction.net share a heading.
 */
internal fun seriesOf(fandoms: List<String>): String {
    val names = fandoms.map { normalizeSeries(it) }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
    return when {
        names.isEmpty() -> "Other"
        names.size > 1 -> "Crossovers"
        else -> names.first()
    }
}

private fun normalizeSeries(fandom: String): String = fandom
    .substringAfterLast(" | ")
    .replace(Regex("""\s*-\s*All Media Types\s*$""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""\s*\([^)]*\)\s*$"""), "")
    .trim()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(nav: Navigator) {
    val container = appContainer()
    val works by container.library.observeLibrary().collectAsStateWithLifecycle(emptyList())
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.ADDED) }
    var sortMenu by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    var collapsed by rememberSaveable { mutableStateOf(listOf<String>()) }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            val s = container.settings.settings.first()
            if (s.ao3User != null || s.ffnUser != null) runCatching { container.accounts.sync(detailLimit = 20) }
            val result = container.updateChecker.checkAll(notify = false)
            refreshing = false
            snackbar.showSnackbar(
                when {
                    result.offline -> "Couldn't reach the sites."
                    result.updatedWorks == 0 -> "No new chapters in ${result.checked} followed works."
                    result.updatedWorks == 1 -> "1 work has new chapters."
                    else -> "${result.updatedWorks} works have new chapters."
                },
            )
        }
    }

    val shown = works
        .filter {
            when (filter) {
                // "All" is what you follow or keep offline; liked-only works live under "Liked".
                LibraryFilter.ALL -> it.followed || it.isDownloaded
                LibraryFilter.NEW -> it.newChapters > 0
                LibraryFilter.READING -> it.lastReadChapter > 0
                LibraryFilter.UNREAD -> it.lastReadChapter == 0 && (it.followed || it.isDownloaded)
                LibraryFilter.LIKED -> it.liked
                LibraryFilter.DOWNLOADED -> it.isDownloaded
                LibraryFilter.COMPLETE -> it.complete && (it.followed || it.isDownloaded)
            }
        }
        .filter {
            query.isBlank() || it.title.contains(query, true) || it.authors.any { a -> a.contains(query, true) } ||
                it.fandoms.any { f -> f.contains(query, true) }
        }
        .let { list ->
            when (sort) {
                LibrarySort.ADDED -> list.sortedByDescending { it.addedAt }
                LibrarySort.UPDATED -> list.sortedByDescending { it.updated }
                LibrarySort.TITLE -> list.sortedBy { it.title.lowercase() }
                LibrarySort.AUTHOR -> list.sortedBy { it.authors.firstOrNull()?.lowercase() }
            }
        }
        .sortedByDescending { it.newChapters > 0 }

    ScreenScaffold(
        title = "Library",
        isTab = true,
        snackbar = snackbar,
        actions = {
            IconButton(onClick = { scope.launch { container.settings.update { it.copy(groupLibrary = !it.groupLibrary) } } }) {
                Icon(
                    if (settings.groupLibrary) Icons.AutoMirrored.Filled.ViewList else Icons.Default.ViewAgenda,
                    contentDescription = if (settings.groupLibrary) "Show as one list" else "Group by series",
                )
            }
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    LibrarySort.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.label, fontWeight = if (s == sort) FontWeight.Bold else null) },
                            onClick = { sort = s; sortMenu = false },
                        )
                    }
                }
            }
            IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, contentDescription = "Check for new chapters") }
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refresh() }, modifier = Modifier.padding(padding)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                item(key = "filters") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(LibraryFilter.entries) { f ->
                            val count = when (f) {
                                LibraryFilter.NEW -> works.count { it.newChapters > 0 }
                                LibraryFilter.LIKED -> works.count { it.liked }
                                LibraryFilter.DOWNLOADED -> works.count { it.isDownloaded }
                                else -> null
                            }
                            FilterChip(
                                selected = filter == f,
                                onClick = { filter = f },
                                label = { Text(if (count != null && count > 0) "${f.label} ($count)" else f.label) },
                            )
                        }
                    }
                }
                if (works.size > 6) {
                    item(key = "query") {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            label = { Text("Filter your library") }, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        )
                    }
                }
                if (shown.isEmpty()) {
                    item(key = "empty") { EmptyLibrary(works.isEmpty()) }
                }
                if (settings.groupLibrary) {
                    val groups = shown.groupBy { seriesOf(it.fandoms) }.entries
                        .sortedWith(compareByDescending<Map.Entry<String, List<LibraryWork>>> { g -> g.value.any { it.newChapters > 0 } }
                            .thenBy { if (it.key == "Other") 1 else 0 }
                            .thenBy { it.key.lowercase() })
                    groups.forEach { (series, list) ->
                        val isCollapsed = series in collapsed
                        item(key = "h-$series") {
                            SeriesHeader(series, list.size, list.sumOf { it.newChapters }, isCollapsed) {
                                collapsed = if (isCollapsed) collapsed - series else collapsed + series
                            }
                        }
                        if (!isCollapsed) {
                            items(list, key = { "w-${it.id}" }) { work -> LibraryRow(work, nav) }
                        }
                    }
                } else {
                    items(shown, key = { "w-${it.id}" }) { work -> LibraryRow(work, nav) }
                }
            }
        }
    }
}

@Composable
private fun SeriesHeader(name: String, count: Int, newChapters: Int, collapsed: Boolean, onToggle: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                "  $count" + if (newChapters > 0) " · $newChapters new" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Icon(if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess, contentDescription = if (collapsed) "Expand" else "Collapse")
        }
        HorizontalDivider()
    }
}

/** One compact library entry: cover, title, author and progress on two short lines. */
@Composable
private fun LibraryRow(work: LibraryWork, nav: Navigator) {
    val total = work.chaptersPosted.coerceAtLeast(1)
    val read = work.lastReadChapter.coerceAtMost(total)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { nav.work(work.id) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(work.coverUrl, work.title, work.rating, Modifier.size(width = 42.dp, height = 58.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                work.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        append(work.authors.joinToString().ifBlank { "Anonymous" })
                        append(" · ")
                        append(if (work.lastReadChapter == 0) "${work.chaptersPosted} ch" else "ch $read/${work.chaptersPosted}")
                        if (!work.complete) append(" · ongoing")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (work.liked) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Favorite, contentDescription = "Liked", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                }
                if (work.isDownloaded) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.DownloadDone, contentDescription = "Downloaded", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                }
            }
            if (work.lastReadChapter > 0) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (read - 1 + work.lastReadProgress) / total },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    drawStopIndicator = {},
                )
            }
        }
        if (work.newChapters > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                "+${work.newChapters}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
        IconButton(onClick = { nav.reader(work.id, work.lastReadChapter.coerceAtLeast(1)) }) {
            Icon(Icons.Default.PlayArrow, contentDescription = if (work.lastReadChapter == 0) "Start reading" else "Continue reading")
        }
    }
}

@Composable
private fun EmptyLibrary(libraryEmpty: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null, modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (libraryEmpty) "Your library is empty.\nFollow or download a work and it shows up here."
            else "Nothing matches this filter.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
