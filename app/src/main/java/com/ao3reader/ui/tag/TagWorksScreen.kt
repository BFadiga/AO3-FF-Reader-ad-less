package com.ao3reader.ui.tag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ao3reader.data.model.WorkFilter
import com.ao3reader.data.prefs.AppSettings
import com.ao3reader.data.repo.Ao3Repository
import com.ao3reader.data.model.Site
import com.ao3reader.data.repo.BlockLists
import com.ao3reader.ui.components.AuthorDialog
import com.ao3reader.ui.Navigator
import com.ao3reader.ui.components.FilterEditor
import com.ao3reader.ui.components.ScreenScaffold
import com.ao3reader.ui.components.TagActionDialog
import com.ao3reader.ui.components.WorkList
import com.ao3reader.ui.components.WorkListState
import com.ao3reader.ui.components.appContainer
import com.ao3reader.ui.components.appViewModel

class TagWorksViewModel(private val ao3: Ao3Repository, val tag: String) : ViewModel() {
    var filter by mutableStateOf(WorkFilter())
        private set
    val list = WorkListState(viewModelScope) { page -> ao3.tagWorks(tag, filter, page) }

    init { list.loadMore() }

    fun apply(newFilter: WorkFilter) {
        filter = newFilter
        list.refresh()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagWorksScreen(tag: String, nav: Navigator) {
    val vm = appViewModel { TagWorksViewModel(it.ao3, tag) }
    val container = appContainer()
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val blockList by container.filters.blockLists.collectAsStateWithLifecycle(BlockLists())
    val favorites by container.filters.favorites(Site.AO3).collectAsStateWithLifecycle(emptyList())
    var authorDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    val library by container.library.observeLibrary().collectAsStateWithLifecycle(emptyList())
    var showFilters by remember { mutableStateOf(false) }
    var tagDialog by remember { mutableStateOf<String?>(null) }
    val isFavorite = favorites.any { it.name == tag }

    ScreenScaffold(
        title = tag,
        onBack = { nav.back() },
        actions = {
            IconButton(onClick = { tagDialog = tag }) {
                Icon(if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = "Favorite or block")
            }
            IconButton(onClick = { showFilters = true }) { Icon(Icons.Default.FilterList, contentDescription = "Filters") }
        },
    ) { padding ->
        WorkList(
            state = vm.list,
            settings = settings,
            blockList = blockList,
            libraryIds = library.map { it.id }.toSet(),
            onWorkClick = { nav.work(it.id) },
            onTagClick = { tagDialog = it },
            onAuthorClick = { _, name, id -> authorDialog = name to id },
            modifier = Modifier.padding(padding),
        )
    }

    if (showFilters) {
        var draft by remember { mutableStateOf(vm.filter) }
        ModalBottomSheet(onDismissRequest = { showFilters = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Text("Filter works in $tag", style = MaterialTheme.typography.titleMedium)
                FilterEditor(draft, { draft = it }, searchFields = false, modifier = Modifier.padding(top = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                    OutlinedButton(onClick = { draft = WorkFilter() }) { Text("Reset") }
                    Button(onClick = { vm.apply(draft); showFilters = false }, modifier = Modifier.fillMaxWidth()) { Text("Apply") }
                }
            }
        }
    }

    tagDialog?.let { t ->
        TagActionDialog(site = Site.AO3, tag = t, onBrowse = { if (t != tag) nav.tag(t) }, onDismiss = { tagDialog = null })
    }
    authorDialog?.let { (name, id) ->
        AuthorDialog(Site.AO3, name, onWorks = { nav.author(Site.AO3, id, name) }, onDismiss = { authorDialog = null })
    }
}
