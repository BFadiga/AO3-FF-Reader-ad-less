package com.ao3reader.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.ao3reader.AppContainer
import com.ao3reader.data.local.BlockKind
import com.ao3reader.data.model.Site
import com.ao3reader.data.model.WattpadFilter
import com.ao3reader.data.model.WattpadLength
import com.ao3reader.data.model.WattpadSort
import com.ao3reader.data.prefs.AppSettings
import com.ao3reader.data.repo.BlockLists
import com.ao3reader.ui.Navigator
import com.ao3reader.ui.components.AuthorDialog
import com.ao3reader.ui.components.TagActionDialog
import com.ao3reader.ui.components.TagSearchField
import com.ao3reader.ui.components.WorkList
import com.ao3reader.ui.components.WorkListState
import com.ao3reader.ui.components.appContainer
import com.ao3reader.ui.components.appViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Wattpad tags are single lowercase words ("enemiestolovers"). */
fun wattpadTag(text: String): String = text.trim().removePrefix("#").lowercase().replace(Regex("""\s+"""), "")

private val UPDATED_CHOICES = listOf("Any time" to null, "Past week" to 7, "Past month" to 30, "Past year" to 365)

class WattpadSearchViewModel(private val c: AppContainer) : ViewModel() {
    var filter by mutableStateOf(WattpadFilter())
    private var submitted = filter
    val list = WorkListState(viewModelScope) { page -> c.wattpad.search(submitted, page) }

    init {
        viewModelScope.launch {
            c.wattpadSearchRequest.collect { request ->
                if (request != null) {
                    c.wattpadSearchRequest.value = null
                    filter = request
                    search()
                }
            }
        }
    }

    /** Blocked Wattpad tags are excluded automatically. */
    fun search() {
        viewModelScope.launch {
            val blocked = c.filters.blocked.first().filter { it.site == Site.WATTPAD && it.kind == BlockKind.TAG }.map { it.value }
            submitted = filter.copy(excludeTags = (filter.excludeTags + blocked).distinct())
            list.refresh()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WattpadSearch(nav: Navigator, settings: AppSettings, modifier: Modifier) {
    val vm = appViewModel { WattpadSearchViewModel(it) }
    val container = appContainer()
    val blockList by container.filters.blockLists.collectAsStateWithLifecycle(BlockLists())
    val library by container.library.observeLibrary().collectAsStateWithLifecycle(emptyList())
    val favorites by container.filters.favorites(Site.WATTPAD).collectAsStateWithLifecycle(emptyList())
    var advanced by remember { mutableStateOf(false) }
    var tagDialog by remember { mutableStateOf<String?>(null) }
    var authorDialog by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val f = vm.filter

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
        onAuthorClick = { _, name, _ -> authorDialog = name },
        onVerify = { nav.web(it) },
        emptyText = "No stories matched. Try fewer tags.",
        modifier = modifier,
        header = {
            item(key = "wattpad-form") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = f.query,
                        onValueChange = { vm.filter = f.copy(query = it) },
                        label = { Text("Search Wattpad") },
                        placeholder = { Text("Title, author or words…") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (f.query.isNotEmpty()) {
                                IconButton(onClick = { vm.filter = f.copy(query = "") }) { Icon(Icons.Default.Clear, contentDescription = "Clear") }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (f.includeTags.isNotEmpty() || f.excludeTags.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            f.includeTags.forEach { t ->
                                InputChip(
                                    selected = true,
                                    onClick = { vm.filter = f.copy(includeTags = f.includeTags - t) },
                                    label = { Text("#$t") },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(18.dp)) },
                                )
                            }
                            f.excludeTags.forEach { t ->
                                InputChip(
                                    selected = false,
                                    onClick = { vm.filter = f.copy(excludeTags = f.excludeTags - t) },
                                    label = { Text("not #$t", color = MaterialTheme.colorScheme.error) },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(18.dp)) },
                                )
                            }
                        }
                    }
                    // One tap adds a pinned tag from Categories.
                    if (favorites.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            favorites.map { wattpadTag(it.name) }.filter { it !in f.includeTags }.take(12).forEach { t ->
                                FilterChip(selected = false, onClick = { vm.filter = f.copy(includeTags = f.includeTags + t) }, label = { Text("+ #$t") })
                            }
                        }
                    }
                    // Sorting is always visible and re-runs a search that's already showing.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WattpadSort.entries.forEach { s ->
                            FilterChip(
                                selected = f.sort == s,
                                onClick = {
                                    vm.filter = vm.filter.copy(sort = s)
                                    if (vm.list.started && !vm.filter.isEmpty) runSearch()
                                },
                                label = { Text(s.label) },
                            )
                        }
                    }
                    if (f.sort == WattpadSort.HOT) {
                        Text(
                            "Hot shows Wattpad's trending list for the first tag (or your search words as a tag).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (f.sort != WattpadSort.BEST_MATCH) {
                        Text(
                            "Wattpad can't sort its search itself, so each page sorts the next 100 best matches.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { advanced = !advanced }) {
                            Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                            Text(if (advanced) "Hide filters" else "Tags & filters")
                        }
                    }
                    AnimatedVisibility(advanced) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TagSearchField("Must have tag", onPick = { t ->
                                vm.filter = vm.filter.copy(includeTags = (vm.filter.includeTags + wattpadTag(t)).distinct())
                            }, suggest = { term -> listOf(wattpadTag(term)) })
                            TagSearchField("Leave out tag", onPick = { t ->
                                vm.filter = vm.filter.copy(excludeTags = (vm.filter.excludeTags + wattpadTag(t)).distinct())
                            }, suggest = { term -> listOf(wattpadTag(term)) })
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Include mature stories", Modifier.weight(1f))
                                Switch(checked = f.mature, onCheckedChange = { vm.filter = vm.filter.copy(mature = it) })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Completed stories only", Modifier.weight(1f))
                                Switch(checked = f.completeOnly, onCheckedChange = { vm.filter = vm.filter.copy(completeOnly = it) })
                            }
                            Text("Updated", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                UPDATED_CHOICES.forEach { (label, days) ->
                                    FilterChip(
                                        selected = f.updatedWithinDays == days,
                                        onClick = { vm.filter = vm.filter.copy(updatedWithinDays = days) },
                                        label = { Text(label) },
                                    )
                                }
                            }
                            Text("Length", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                WattpadLength.entries.forEach { l ->
                                    FilterChip(
                                        selected = f.length == l,
                                        onClick = { vm.filter = vm.filter.copy(length = l) },
                                        label = { Text(l.label) },
                                    )
                                }
                            }
                        }
                    }
                    if (!vm.list.started || advanced) {
                        Button(onClick = { runSearch() }, modifier = Modifier.fillMaxWidth(), enabled = !f.isEmpty) { Text("Search") }
                    }
                }
            }
        },
    )

    tagDialog?.let { t ->
        TagActionDialog(site = Site.WATTPAD, tag = t, onBrowse = { nav.browseTag(Site.WATTPAD, t) }, onDismiss = { tagDialog = null })
    }
    authorDialog?.let { name ->
        AuthorDialog(Site.WATTPAD, name, onWorks = { nav.author(Site.WATTPAD, name, name) }, onDismiss = { authorDialog = null })
    }
}
