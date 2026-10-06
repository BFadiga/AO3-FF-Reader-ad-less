package com.ao3reader.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ao3reader.data.model.SortColumn
import com.ao3reader.data.model.WorkFilter
import com.ao3reader.data.prefs.AppSettings
import com.ao3reader.data.repo.Ao3Repository
import com.ao3reader.data.model.Site
import com.ao3reader.data.repo.BlockLists
import com.ao3reader.ui.components.AuthorDialog
import com.ao3reader.ui.components.SiteSwitch
import com.ao3reader.ui.Navigator
import com.ao3reader.ui.components.FilterEditor
import com.ao3reader.ui.components.ScreenScaffold
import com.ao3reader.ui.components.TagActionDialog
import com.ao3reader.ui.components.WorkList
import com.ao3reader.ui.components.WorkListState
import com.ao3reader.ui.components.appContainer
import com.ao3reader.ui.components.appViewModel

class SearchViewModel(private val ao3: Ao3Repository) : ViewModel() {
    var filter by mutableStateOf(WorkFilter(sort = SortColumn.BEST_MATCH))
    private var submitted = filter
    val list = WorkListState(viewModelScope) { page -> ao3.search(submitted, page) }

    fun search() {
        submitted = filter
        list.refresh()
    }
}

@Composable
fun SearchScreen(nav: Navigator) {
    val container = appContainer()
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val site = settings.currentSite
    ScreenScaffold(
        title = "Search",
        isTab = true,
        actions = { SiteSwitch(site) },
    ) { padding ->
        when (site) {
            Site.AO3 -> Ao3Search(nav, settings, Modifier.padding(padding))
            Site.FFN -> FfnSearch(nav, settings, Modifier.padding(padding))
            Site.WATTPAD -> WattpadSearch(nav, settings, Modifier.padding(padding))
        }
    }
}

@Composable
private fun Ao3Search(nav: Navigator, settings: AppSettings, modifier: Modifier) {
    val vm = appViewModel { SearchViewModel(it.ao3) }
    val container = appContainer()
    val blockList by container.filters.blockLists.collectAsStateWithLifecycle(BlockLists())
    val library by container.library.observeLibrary().collectAsStateWithLifecycle(emptyList())
    var advanced by remember { mutableStateOf(false) }
    var tagDialog by remember { mutableStateOf<String?>(null) }
    var authorDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    val focus = LocalFocusManager.current

    fun runSearch() {
        focus.clearFocus()
        advanced = false
        vm.search()
    }

    WorkList(
        state = vm.list,
        settings = settings,
        blockList = blockList,
        libraryIds = library.map { it.id }.toSet(),
        onWorkClick = { nav.work(it.id) },
        onTagClick = { tagDialog = it },
        onAuthorClick = { _, name, id -> authorDialog = name to id },
        emptyText = "No works matched. Try fewer filters.",
        modifier = modifier,
        header = {
            item(key = "search-form") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = vm.filter.query,
                        onValueChange = { vm.filter = vm.filter.copy(query = it) },
                        label = { Text("Search AO3") },
                        placeholder = { Text("Words, titles, characters…") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (vm.filter.query.isNotEmpty()) {
                                IconButton(onClick = { vm.filter = vm.filter.copy(query = "") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { advanced = !advanced }) {
                            Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                            Text(if (advanced) "Hide filters" else "More filters")
                        }
                        if (!vm.list.started || advanced) {
                            Button(onClick = { runSearch() }, modifier = Modifier.weight(1f)) { Text("Search") }
                        }
                    }
                    AnimatedVisibility(advanced) {
                        FilterEditor(vm.filter, { vm.filter = it }, searchFields = true)
                    }
                }
            }
        },
    )

    tagDialog?.let { t -> TagActionDialog(site = Site.AO3, tag = t, onBrowse = { nav.tag(t) }, onDismiss = { tagDialog = null }) }
    authorDialog?.let { (name, id) ->
        AuthorDialog(Site.AO3, name, onWorks = { nav.author(Site.AO3, id, name) }, onDismiss = { authorDialog = null })
    }
}
