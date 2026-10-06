package com.ao3reader.ui.author

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ao3reader.AppContainer
import com.ao3reader.data.model.Site
import com.ao3reader.data.model.WorkPage
import com.ao3reader.data.remote.ffn.FfnUrls
import com.ao3reader.data.remote.wattpad.WattpadUrls
import com.ao3reader.data.prefs.AppSettings
import com.ao3reader.data.repo.BlockLists
import com.ao3reader.ui.Navigator
import com.ao3reader.ui.components.BlockAuthorDialog
import com.ao3reader.ui.components.ScreenScaffold
import com.ao3reader.ui.components.TagActionDialog
import com.ao3reader.ui.components.WorkList
import com.ao3reader.ui.components.WorkListState
import com.ao3reader.ui.components.appContainer
import com.ao3reader.ui.components.appViewModel

/** An author's own works. [authorId] is the AO3 "/users/…" path or the FanFiction.net user id. */
class AuthorViewModel(c: AppContainer, val site: Site, val authorId: String, val name: String) : ViewModel() {
    var title by mutableStateOf(name)
        private set
    val list = WorkListState(viewModelScope) { page ->
        when (site) {
            Site.AO3 -> c.ao3.authorWorks(authorId.ifBlank { null }, name, page)
            Site.FFN -> {
                val (n, works) = c.ffn.authorStories(authorId)
                if (n.isNotBlank()) title = n
                WorkPage(works, 1, 1, heading = "${works.size} stories")
            }
            Site.WATTPAD -> {
                val works = c.wattpad.authorStories(authorId.ifBlank { name })
                WorkPage(works, 1, 1, heading = "${works.size} stories")
            }
        }
    }

    init { list.loadMore() }
}

@Composable
fun AuthorScreen(site: Site, authorId: String, name: String, nav: Navigator) {
    val vm = appViewModel(key = "$site/$authorId/$name") { AuthorViewModel(it, site, authorId, name) }
    val container = appContainer()
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val blockLists by container.filters.blockLists.collectAsStateWithLifecycle(BlockLists())
    val library by container.library.observeLibrary().collectAsStateWithLifecycle(emptyList())
    var tagDialog by remember { mutableStateOf<String?>(null) }
    var blockDialog by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = vm.title,
        onBack = { nav.back() },
        actions = {
            if (site != Site.AO3 && authorId.isNotBlank()) {
                IconButton(onClick = { nav.web(if (site == Site.FFN) FfnUrls.author(authorId) else WattpadUrls.profile(authorId)) }) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = "Profile on ${site.label}")
                }
            }
            IconButton(onClick = { blockDialog = true }) { Icon(Icons.Default.Block, contentDescription = "Block author") }
        },
    ) { padding ->
        WorkList(
            state = vm.list,
            settings = settings,
            blockList = blockLists,
            libraryIds = library.map { it.id }.toSet(),
            onWorkClick = { nav.work(it.id) },
            onTagClick = { tagDialog = it },
            emptyText = "No works found for this author.",
            modifier = Modifier.padding(padding),
        )
    }

    tagDialog?.let { t -> TagActionDialog(site = site, tag = t, onBrowse = { nav.browseTag(site, t) }, onDismiss = { tagDialog = null }) }
    if (blockDialog) BlockAuthorDialog(site = site, author = vm.title, onDismiss = { blockDialog = false })
}
