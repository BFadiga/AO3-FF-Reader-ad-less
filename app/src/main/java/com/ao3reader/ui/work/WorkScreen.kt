package com.ao3reader.ui.work

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ao3reader.AppContainer
import com.ao3reader.data.local.LibraryWork
import com.ao3reader.data.model.Chapter
import com.ao3reader.data.model.WorkSummary
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.LocalContentColor
import com.ao3reader.data.model.Site
import com.ao3reader.data.model.WorkIds
import com.ao3reader.data.remote.Ao3Urls
import com.ao3reader.data.remote.ffn.FfnUrls
import com.ao3reader.data.remote.wattpad.WattpadUrls
import com.ao3reader.ui.components.AuthorDialog
import com.ao3reader.ui.components.Cover
import com.ao3reader.ui.components.verificationUrl
import com.ao3reader.ui.Navigator
import com.ao3reader.ui.components.HtmlText
import com.ao3reader.ui.components.RatingBadge
import com.ao3reader.ui.components.ScreenScaffold
import com.ao3reader.ui.components.TagActionDialog
import com.ao3reader.ui.components.appViewModel
import com.ao3reader.ui.components.formatted
import com.ao3reader.ui.components.userMessage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkViewModel(private val c: AppContainer, val id: Long) : ViewModel() {
    val entry: StateFlow<LibraryWork?> = c.library.observeWork(id).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var summary by mutableStateOf<WorkSummary?>(null); private set
    var chapters by mutableStateOf<List<Chapter>>(emptyList()); private set
    var notesHtml by mutableStateOf<String?>(null); private set
    var published by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set
    var offlineCopy by mutableStateOf(false); private set
    var downloading by mutableStateOf(false); private set
    var liking by mutableStateOf(false); private set
    var kudosGiven by mutableStateOf(false); private set
    var verifyUrl by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null)
    val site = WorkIds.site(id)

    init { load(false) }

    fun load(force: Boolean) {
        loading = true
        error = null
        verifyUrl = null
        viewModelScope.launch {
            // Library works show their saved details at once; the site is asked for fresh ones meanwhile.
            if (summary == null) {
                c.library.savedDetails(id)?.let { (s, details) ->
                    summary = s
                    details?.let {
                        chapters = it.chapters
                        notesHtml = it.notesHtml
                        published = it.published
                    }
                }
            }
            try {
                val work = c.works.fullWork(id, forceRefresh = force)
                summary = work.summary
                chapters = work.chapters
                notesHtml = work.notesHtml
                published = work.published
                offlineCopy = false
                kudosGiven = work.actions.kudosGiven
                // Keep library metadata fresh whenever the work is opened.
                c.library.get(id)?.let { c.library.upsert(it.withMetadata(work.summary)) }
                c.library.saveDetails(work)
                // Subscribed on AO3 but not followed here yet: follow it, so the two stay in step.
                if (work.actions.subscribed && entry.value?.followed != true) c.library.follow(work.summary)
            } catch (e: Exception) {
                verifyUrl = e.verificationUrl()
                val (s, offline) = runCatching { c.library.chapters(id, preferOffline = true) }.getOrNull() ?: (null to emptyList())
                if (s != null && offline.isNotEmpty()) {
                    summary = s
                    chapters = offline
                    offlineCopy = true
                } else if (summary != null) {
                    offlineCopy = true
                } else {
                    error = e.userMessage()
                }
            } finally {
                loading = false
            }
        }
    }

    fun setPinned(work: WorkSummary, pinned: Boolean) = viewModelScope.launch {
        c.library.setPinned(work, pinned)
        message = if (pinned) "Pinned to the top of your library." else "Unpinned."
    }

    fun toggleFollow() = viewModelScope.launch {
        val s = summary ?: return@launch
        val follow = entry.value?.followed != true
        if (follow) {
            c.library.follow(s)
            message = "Following. You'll get a notification when a new chapter is posted."
        } else {
            c.library.unfollow(id)
            message = "Unfollowed"
        }
        // Mirror it on the site account in the background; only speak up if there's something to say.
        c.appScope.launch {
            c.accounts.pushFollow(s, follow)?.let { note -> message = note }
        }
    }

    /** Kudos on AO3, a favorite on FanFiction.net. */
    fun toggleLike() = viewModelScope.launch {
        val s = summary ?: return@launch
        if (entry.value?.liked == true) {
            c.accounts.unlike(s)
            message = if (site == Site.AO3) "Removed from your liked works (kudos stay on AO3)." else "Removed from your liked works here."
            return@launch
        }
        liking = true
        message = try {
            c.accounts.like(s).also { if (site == Site.AO3) kudosGiven = true }
        } catch (e: Exception) {
            "Couldn't do that: ${e.userMessage()}"
        } finally {
            liking = false
        }
    }

    fun download() = viewModelScope.launch {
        downloading = true
        try {
            val n = c.library.download(id)
            message = "Downloaded $n chapter${if (n == 1) "" else "s"} for offline reading."
        } catch (e: Exception) {
            message = "Download failed: ${e.userMessage()}"
        } finally {
            downloading = false
        }
    }

    fun deleteDownload() = viewModelScope.launch {
        c.library.deleteDownload(id)
        message = "Offline copy deleted."
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkScreen(id: Long, nav: Navigator) {
    val vm = appViewModel(key = "work-$id") { WorkViewModel(it, id) }
    val site = vm.site
    val entry by vm.entry.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var tagDialog by remember { mutableStateOf<String?>(null) }
    var authorDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    vm.message?.let { msg ->
        LaunchedEffect(msg) {
            snackbar.showSnackbar(msg)
            vm.message = null
        }
    }

    ScreenScaffold(
        title = vm.summary?.title ?: "Work",
        onBack = { nav.back() },
        snackbar = snackbar,
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Refresh from ${site.shortLabel}") }, onClick = { menu = false; vm.load(true) })
                    vm.summary?.let { s ->
                        val pinned = entry?.pinned == true
                        DropdownMenuItem(
                            text = { Text(if (pinned) "Unpin from library top" else "Pin to top of library") },
                            onClick = { menu = false; vm.setPinned(s, !pinned) },
                        )
                    }
                    DropdownMenuItem(text = { Text("Share link") }, onClick = {
                        menu = false
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${vm.summary?.title.orEmpty()} ${siteUrl(id)}".trim())
                        }
                        context.startActivity(Intent.createChooser(send, "Share work"))
                    })
                    if (site != Site.AO3) {
                        DropdownMenuItem(text = { Text(if (site == Site.FFN) "Reviews" else "Comments") }, onClick = { menu = false; nav.reviews(id) })
                        DropdownMenuItem(text = { Text("Open in the app's browser") }, onClick = { menu = false; nav.web(siteUrl(id)) })
                    } else {
                        DropdownMenuItem(text = { Text("Open on AO3 (comments)") }, onClick = {
                            menu = false
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Ao3Urls.work(id))))
                        })
                    }
                    vm.summary?.let { s ->
                        s.authors.forEachIndexed { i, author ->
                            if (author != "Anonymous") {
                                DropdownMenuItem(text = { Text("More by $author") }, onClick = {
                                    menu = false
                                    nav.author(site, s.authorIds.getOrNull(i).orEmpty(), author)
                                })
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        val summary = vm.summary
        when {
            vm.loading && summary == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            summary == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(vm.error ?: "Couldn't load this work.", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.verifyUrl?.let { url -> OutlinedButton(onClick = { nav.web(url) }) { Text("Verify") } }
                    Button(onClick = { vm.load(true) }) { Text("Retry") }
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (summary.coverUrl != null) {
                            Cover(summary.coverUrl, summary.title, summary.rating, Modifier.size(width = 72.dp, height = 100.dp))
                        } else {
                            RatingBadge(summary.rating)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(summary.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            FlowRow {
                                Text("by ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                summary.authors.ifEmpty { listOf("Anonymous") }.forEachIndexed { i, author ->
                                    Text(
                                        author + if (i < summary.authors.size - 1) ", " else "",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.clickable(enabled = author != "Anonymous") {
                                            authorDialog = author to summary.authorIds.getOrNull(i).orEmpty()
                                        },
                                    )
                                }
                            }
                            Text(
                                site.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (vm.offlineCopy) {
                        Text(
                            "Showing your saved copy (${site.shortLabel} couldn't be reached).",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary,
                        )
                        vm.verifyUrl?.let { url -> TextButton(onClick = { nav.web(url) }) { Text("Verify") } }
                    } else if (vm.loading) {
                        Text(
                            "Checking ${site.shortLabel} for changes…",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    val e = entry
                    val following = e?.followed == true
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { nav.reader(id, (e?.lastReadChapter ?: 0).coerceAtLeast(1)) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if ((e?.lastReadChapter ?: 0) > 0) "Continue ch. ${e!!.lastReadChapter}" else "Read")
                        }
                        FilledTonalButton(onClick = { vm.toggleFollow() }) {
                            Icon(if (following) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (following) "Following" else "Follow")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val liked = e?.liked == true || vm.kudosGiven
                    OutlinedButton(onClick = { vm.toggleLike() }, enabled = !vm.liking, modifier = Modifier.fillMaxWidth()) {
                        if (vm.liking) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (liked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                site == Site.AO3 && liked -> "Kudos left"
                                site == Site.AO3 -> "Leave kudos"
                                site == Site.WATTPAD && liked -> "Voted"
                                site == Site.WATTPAD -> "Vote"
                                liked -> "Favorited"
                                else -> "Favorite"
                            },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    when {
                        vm.downloading -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Downloading…")
                        }
                        e?.isDownloaded == true -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DownloadDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("${e.downloadedChapters} chapters saved offline", modifier = Modifier.weight(1f))
                            if (e.downloadedChapters < summary.chaptersPosted) {
                                TextButton(onClick = { vm.download() }) { Text("Update") }
                            }
                            IconButton(onClick = { vm.deleteDownload() }) { Icon(Icons.Default.Delete, contentDescription = "Delete offline copy") }
                        }
                        else -> OutlinedButton(onClick = { vm.download() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Download for offline reading")
                        }
                    }
                }
                item {
                    Text(
                        listOfNotNull(
                            summary.words.takeIf { it > 0 }?.let { "${it.formatted()} words" },
                            "${summary.chaptersLabel} chapters",
                            if (summary.complete) "Complete" else "In progress",
                            summary.language.ifBlank { null },
                            summary.kudos.takeIf { it > 0 }?.let {
                                when (site) {
                                    Site.FFN -> "${it.formatted()} favs"
                                    Site.WATTPAD -> "★ ${it.formatted()} votes"
                                    Site.AO3 -> "♥ ${it.formatted()}"
                                }
                            },
                            summary.follows.takeIf { it > 0 }?.let { "${it.formatted()} follows" },
                            summary.hits.takeIf { it > 0 }?.let { "${it.formatted()} ${if (site == Site.WATTPAD) "reads" else "hits"}" },
                            summary.comments.takeIf { it > 0 && site != Site.AO3 }?.let { "${it.formatted()} ${if (site == Site.FFN) "reviews" else "comments"}" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        listOfNotNull(vm.published?.let { "Published $it" }, summary.updated.ifBlank { null }?.let { "Updated $it" }).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    summary.series.forEach { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) }
                    if (site != Site.AO3) {
                        val noun = if (site == Site.FFN) "reviews" else "comments"
                        TextButton(onClick = { nav.reviews(id) }, contentPadding = PaddingValues(0.dp)) {
                            Text(if (summary.comments > 0) "Read ${summary.comments.formatted()} $noun" else noun.replaceFirstChar { it.uppercase() })
                        }
                    }
                }
                item {
                    TagGroup("Fandoms", summary.fandoms) { tagDialog = it }
                    TagGroup("Warnings", summary.warnings, warning = true) { tagDialog = it }
                    TagGroup("Categories", summary.categories) { tagDialog = it }
                    TagGroup("Relationships", summary.relationships) { tagDialog = it }
                    TagGroup("Characters", summary.characters) { tagDialog = it }
                    TagGroup(if (site == Site.FFN) "Genres" else "Tags", summary.freeforms) { tagDialog = it }
                }
                if (summary.summaryHtml.isNotBlank()) {
                    item {
                        Text("Summary", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        HtmlText(summary.summaryHtml)
                    }
                }
                vm.notesHtml?.takeIf { it.isNotBlank() }?.let { notes ->
                    item {
                        Text("Notes", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        HtmlText(notes, maxLines = 12)
                    }
                }
                item { Text("Chapters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                itemsIndexed(vm.chapters, key = { _, c -> c.index }) { _, chapter ->
                    val current = entry?.lastReadChapter == chapter.index
                    ListItem(
                        headlineContent = {
                            Text(
                                chapter.title + if (current) "  ·  reading" else "",
                                fontWeight = if (current) FontWeight.Bold else null,
                                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        modifier = Modifier.clickable { nav.reader(id, chapter.index) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    tagDialog?.let { t ->
        val fandoms = vm.summary?.fandoms.orEmpty()
        TagActionDialog(
            site = site,
            tag = t,
            isFandom = t in fandoms,
            onBrowse = { nav.browseTag(site, t, fandoms.singleOrNull(), isFandom = t in fandoms) },
            onDismiss = { tagDialog = null },
        )
    }
    authorDialog?.let { (name, authorId) ->
        AuthorDialog(site, name, onWorks = { nav.author(site, authorId, name) }, onDismiss = { authorDialog = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagGroup(label: String, tags: List<String>, warning: Boolean = false, onClick: (String) -> Unit) {
    if (tags.isEmpty()) return
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tags.forEach { tag ->
                Text(
                    tag,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onClick(tag) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private fun siteUrl(id: Long): String = when (WorkIds.site(id)) {
    Site.FFN -> FfnUrls.chapter(WorkIds.remote(id), 1)
    Site.WATTPAD -> WattpadUrls.storyPage(WorkIds.remote(id))
    Site.AO3 -> Ao3Urls.work(id)
}
