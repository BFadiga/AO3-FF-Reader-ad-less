package com.ao3reader.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ao3reader.AppContainer
import com.ao3reader.data.local.BlockKind
import com.ao3reader.data.model.FfnFandom
import com.ao3reader.data.model.FfnFilter
import com.ao3reader.data.model.FfnFilterOptions
import com.ao3reader.data.model.FfnGenres
import com.ao3reader.data.model.FfnMedia
import com.ao3reader.data.model.FfnOption
import com.ao3reader.data.model.FfnRating
import com.ao3reader.data.model.FfnSort
import com.ao3reader.data.model.FfnStatus
import com.ao3reader.data.model.Site
import com.ao3reader.data.prefs.AppSettings
import com.ao3reader.data.repo.BlockLists
import com.ao3reader.ui.Navigator
import com.ao3reader.ui.components.AuthorDialog
import com.ao3reader.ui.components.TagActionDialog
import com.ao3reader.ui.components.WorkList
import com.ao3reader.ui.components.WorkListState
import com.ao3reader.ui.components.appContainer
import com.ao3reader.ui.components.appViewModel
import com.ao3reader.ui.components.userMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FfnSearchViewModel(private val c: AppContainer) : ViewModel() {
    var filter by mutableStateOf(c.ffnLastFilter)
    var options by mutableStateOf(FfnFilterOptions())
        private set
    private var submitted = filter
    val list = WorkListState(viewModelScope) { page -> c.ffn.search(submitted, page) }

    init {
        // Searches requested from other screens (a fandom or tag tapped elsewhere).
        viewModelScope.launch {
            c.ffnSearchRequest.collect { request ->
                if (request != null) {
                    c.ffnSearchRequest.value = null
                    filter = request
                    search()
                }
            }
        }
    }

    fun setFandom(fandom: FfnFandom?) {
        // Characters belong to a fandom, so they don't carry over.
        filter = filter.copy(fandom = fandom, includeCharacters = emptyList(), excludeCharacters = emptyList())
        options = FfnFilterOptions()
        loadOptions()
    }

    fun loadOptions() {
        val path = filter.fandom?.path?.takeIf { it.isNotBlank() } ?: return
        c.ffn.cachedOptions(path)?.let { options = it; return }
        viewModelScope.launch { runCatching { c.ffn.filterOptions(path) }.onSuccess { options = it } }
    }

    /** Blocked FanFiction.net genres and characters are excluded automatically. */
    fun search() {
        viewModelScope.launch {
            val blocked = c.filters.blocked.first().filter { it.site == Site.FFN && it.kind == BlockKind.TAG }.map { it.value.lowercase() }
            val blockedGenres = FfnGenres.all.filter { it.label.lowercase() in blocked }
            val blockedChars = options.characters.filter { it.label.lowercase() in blocked }
            submitted = filter.copy(
                excludeGenres = (filter.excludeGenres + blockedGenres).distinct(),
                excludeCharacters = (filter.excludeCharacters + blockedChars).distinct(),
            )
            c.ffnLastFilter = filter
            list.refresh()
            if (options.characters.isEmpty()) loadOptions()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FfnSearch(nav: Navigator, settings: AppSettings, modifier: Modifier) {
    val vm = appViewModel { FfnSearchViewModel(it) }
    val container = appContainer()
    val blockList by container.filters.blockLists.collectAsStateWithLifecycle(BlockLists())
    val library by container.library.observeLibrary().collectAsStateWithLifecycle(emptyList())
    var advanced by remember { mutableStateOf(false) }
    var pickFandom by remember { mutableStateOf(false) }
    var tagDialog by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var authorDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
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
        onTagClick = { tagDialog = it to f.fandom?.name },
        onAuthorClick = { _, name, id -> authorDialog = name to id },
        onVerify = { nav.web(it) },
        emptyText = "No stories matched. Try fewer filters.",
        modifier = modifier,
        header = {
            item(key = "ffn-form") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = f.keywords,
                        onValueChange = { vm.filter = f.copy(keywords = it) },
                        label = { Text("Search FanFiction.net") },
                        placeholder = { Text(if (f.fandom != null) "Leave empty to browse the fandom" else "Words in title or summary…") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (f.keywords.isNotEmpty()) {
                                IconButton(onClick = { vm.filter = f.copy(keywords = "") }) { Icon(Icons.Default.Clear, contentDescription = "Clear") }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val fandom = f.fandom
                        if (fandom != null) {
                            InputChip(
                                selected = true,
                                onClick = { pickFandom = true },
                                label = { Text(fandom.name, maxLines = 1) },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = "Clear fandom", modifier = Modifier.size(18.dp).clickable { vm.setFandom(null) })
                                },
                            )
                        } else {
                            AssistChip(onClick = { pickFandom = true }, label = { Text("Choose a fandom") })
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { advanced = !advanced }) {
                            Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                            Text(if (advanced) "Hide filters" else "Filters" + activeCount(f).let { if (it > 0) " ($it)" else "" })
                        }
                    }
                    AnimatedVisibility(advanced) {
                        FfnFilterEditor(f, vm.options, onChange = { vm.filter = it })
                    }
                    if (!vm.list.started || advanced) {
                        Button(onClick = { runSearch() }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (f.keywords.isBlank() && f.fandom != null) "Browse ${f.fandom.name}" else "Search")
                        }
                    }
                }
            }
        },
    )

    if (pickFandom) {
        FfnFandomPicker(
            onPick = { vm.setFandom(it); pickFandom = false; if (vm.filter.keywords.isBlank()) runSearch() },
            onDismiss = { pickFandom = false },
        )
    }
    tagDialog?.let { (t, fandomName) ->
        TagActionDialog(
            site = Site.FFN, tag = t,
            onBrowse = { nav.browseTag(Site.FFN, t, fandomName) },
            onDismiss = { tagDialog = null },
        )
    }
    authorDialog?.let { (name, id) ->
        AuthorDialog(Site.FFN, name, onWorks = { nav.author(Site.FFN, id, name) }, onDismiss = { authorDialog = null })
    }
}

private fun activeCount(f: FfnFilter): Int = listOf(
    f.rating != FfnRating.ALL, f.status != FfnStatus.ANY, f.languageId.isNotBlank(), f.sort != FfnSort.UPDATED,
    f.minWords != null || f.maxWords != null,
).count { it } + f.includeGenres.size + f.excludeGenres.size + f.includeCharacters.size + f.excludeCharacters.size

/**
 * FanFiction.net's filters, made simpler than the site's dropdowns: every genre (and each chosen
 * character) is one chip you tap to cycle between include, exclude and off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FfnFilterEditor(f: FfnFilter, options: FfnFilterOptions, onChange: (FfnFilter) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("Genres · tap to include, again to exclude")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.genres.sortedBy { it.label }.forEach { g ->
                TriChip(
                    label = g.label,
                    included = g in f.includeGenres,
                    excluded = g in f.excludeGenres,
                    onClick = {
                        onChange(
                            when {
                                g in f.includeGenres -> f.copy(includeGenres = f.includeGenres - g, excludeGenres = f.excludeGenres + g)
                                g in f.excludeGenres -> f.copy(excludeGenres = f.excludeGenres - g)
                                f.includeGenres.size >= 2 -> f.copy(includeGenres = f.includeGenres.drop(1) + g)
                                else -> f.copy(includeGenres = f.includeGenres + g)
                            },
                        )
                    },
                )
            }
        }
        if (f.includeGenres.size >= 2) Hint("FanFiction.net allows two included genres at a time.")

        if (f.fandom != null && f.fandom.path.isNotBlank()) {
            Label("Characters")
            val chosen = (f.includeCharacters + f.excludeCharacters).distinct()
            if (chosen.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chosen.forEach { ch ->
                        TriChip(
                            label = ch.label,
                            included = ch in f.includeCharacters,
                            excluded = ch in f.excludeCharacters,
                            onClick = {
                                onChange(
                                    when (ch) {
                                        in f.includeCharacters -> f.copy(includeCharacters = f.includeCharacters - ch, excludeCharacters = (f.excludeCharacters + ch).takeLast(2))
                                        else -> f.copy(excludeCharacters = f.excludeCharacters - ch)
                                    },
                                )
                            },
                        )
                    }
                }
            }
            if (options.characters.isEmpty()) {
                Hint("Characters appear after the fandom's first page loads.")
            } else {
                CharacterPicker(options.characters) { ch ->
                    if (ch !in f.includeCharacters) onChange(f.copy(includeCharacters = (f.includeCharacters + ch).takeLast(4)))
                }
            }
        }

        Label("Rating")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FfnRating.entries.forEach { r ->
                FilterChip(selected = f.rating == r, onClick = { onChange(f.copy(rating = r)) }, label = { Text(r.label) })
            }
        }
        Label("Status")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FfnStatus.entries.forEach { st ->
                FilterChip(selected = f.status == st, onClick = { onChange(f.copy(status = st)) }, label = { Text(st.label) })
            }
        }
        Label("Sort by")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FfnSort.entries.forEach { so ->
                FilterChip(selected = f.sort == so, onClick = { onChange(f.copy(sort = so)) }, label = { Text(so.label) })
            }
        }
        if (options.languages.isNotEmpty()) {
            Label("Language")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = f.languageId.isBlank(), onClick = { onChange(f.copy(languageId = "")) }, label = { Text("Any") })
                options.languages.take(12).forEach { l ->
                    FilterChip(selected = f.languageId == l.value, onClick = { onChange(f.copy(languageId = l.value)) }, label = { Text(l.label) })
                }
            }
        }
        Label("Word count")
        Row {
            OutlinedTextField(
                value = f.minWords?.toString().orEmpty(),
                onValueChange = { v -> onChange(f.copy(minWords = v.filter(Char::isDigit).toIntOrNull())) },
                label = { Text("From") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = f.maxWords?.toString().orEmpty(),
                onValueChange = { v -> onChange(f.copy(maxWords = v.filter(Char::isDigit).toIntOrNull())) },
                label = { Text("To") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        TextButton(onClick = { onChange(FfnFilter(keywords = f.keywords, fandom = f.fandom)) }) { Text("Reset filters") }
        Hint("Tags blocked for FanFiction.net in Settings are left out automatically.")
        Spacer(Modifier.height(4.dp))
    }
}

/** A chip that shows ✓ when included and ✕ when excluded. */
@Composable
private fun TriChip(label: String, included: Boolean, excluded: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = included || excluded,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = when {
            included -> ({ Icon(Icons.Default.Check, contentDescription = "Included", modifier = Modifier.size(16.dp)) })
            excluded -> ({ Icon(Icons.Default.Close, contentDescription = "Excluded", modifier = Modifier.size(16.dp)) })
            else -> null
        },
        colors = if (excluded) {
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else FilterChipDefaults.filterChipColors(),
    )
}

@Composable
private fun CharacterPicker(characters: List<FfnOption>, onPick: (FfnOption) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column {
        OutlinedTextField(
            value = text, onValueChange = { text = it }, singleLine = true,
            label = { Text("Add a character") }, modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        )
        if (text.trim().length >= 1) {
            characters.filter { it.label.contains(text.trim(), ignoreCase = true) }.take(6).forEach { ch ->
                ListItem(
                    headlineContent = { Text(ch.label) },
                    modifier = Modifier.clickable { onPick(ch); text = "" },
                )
            }
        }
    }
}

/** Choose a FanFiction.net fandom: favorites first, then any medium's full list, filtered by typing. */
@Composable
fun FfnFandomPicker(onPick: (FfnFandom) -> Unit, onDismiss: () -> Unit) {
    val container = appContainer()
    val favorites by container.filters.favorites(Site.FFN).collectAsStateWithLifecycle(emptyList())
    var media by remember { mutableStateOf<FfnMedia?>(null) }
    var query by remember { mutableStateOf("") }
    var fandoms by remember { mutableStateOf<List<FfnFandom>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(media) {
        val m = media ?: return@LaunchedEffect
        loading = true
        error = null
        fandoms = runCatching { container.ffn.fandoms(m.path) }.onFailure { error = it.userMessage() }.getOrDefault(emptyList())
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a fandom") },
        text = {
            Column {
                val favFandoms = favorites.filter { it.target.startsWith("/") }
                if (media == null) {
                    if (favFandoms.isNotEmpty()) {
                        Label("Favorites")
                        favFandoms.forEach { fav ->
                            ListItem(
                                headlineContent = { Text(fav.name) },
                                leadingContent = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                modifier = Modifier.clickable { onPick(FfnFandom(fav.name, fav.target)) },
                            )
                        }
                    }
                    Label("Browse by medium")
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(FfnMedia.entries) { m ->
                            ListItem(headlineContent = { Text(m.label) }, modifier = Modifier.clickable { media = m })
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { media = null; query = "" }) { Text("‹ ${media!!.label}") }
                    }
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        label = { Text("Filter fandoms") }, modifier = Modifier.fillMaxWidth(),
                    )
                    when {
                        loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                        else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                            items(fandoms.filter { query.isBlank() || it.name.contains(query.trim(), true) }.take(300), key = { it.path }) { fd ->
                                ListItem(
                                    headlineContent = { Text(fd.name) },
                                    supportingContent = { if (fd.count.isNotBlank()) Text(fd.count) },
                                    modifier = Modifier.clickable { onPick(fd) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
