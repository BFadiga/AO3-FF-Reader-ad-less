package com.ao3reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import com.ao3reader.data.local.BlockKind
import com.ao3reader.data.model.FfnGenres
import com.ao3reader.data.model.Site
import com.ao3reader.data.prefs.AppSettings
import com.ao3reader.ui.Navigator
import com.ao3reader.ui.components.ScreenScaffold
import com.ao3reader.ui.components.TagSearchField
import com.ao3reader.ui.components.appContainer
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BlockedScreen(nav: Navigator) {
    val container = appContainer()
    val all by container.filters.blocked.collectAsStateWithLifecycle(emptyList())
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()
    var author by remember { mutableStateOf("") }
    var site by remember(settings.currentSite) { mutableStateOf(settings.currentSite) }
    val blocked = all.filter { it.site == site }
    val tags = blocked.filter { it.kind == BlockKind.TAG }
    val authors = blocked.filter { it.kind == BlockKind.AUTHOR }

    ScreenScaffold(title = "Blocked tags & authors", onBack = { nav.back() }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Site.entries.forEachIndexed { i, s ->
                        SegmentedButton(
                            selected = site == s,
                            onClick = { site = s },
                            shape = SegmentedButtonDefaults.itemShape(i, Site.entries.size),
                        ) { Text(s.label) }
                    }
                }
            }
            item {
                Text(
                    if (site == Site.AO3) {
                        "Works carrying a blocked tag (including ratings like \"Explicit\" or warnings like \"Major Character Death\") " +
                            "or written by a blocked author are hidden from search and tag listings."
                    } else {
                        "Stories with a blocked genre, character or rating (K, K+, T, M), or by a blocked author, are hidden. " +
                            "Blocked genres and characters are also left out of FanFiction.net searches automatically."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Text("Tags", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
            item {
                if (site == Site.AO3) {
                    TagSearchField("Block a tag", onPick = { t -> scope.launch { container.filters.block(site, BlockKind.TAG, t) } })
                } else {
                    TagSearchField(
                        "Block a genre, character or rating",
                        onPick = { t -> scope.launch { container.filters.block(site, BlockKind.TAG, t) } },
                        suggest = { term ->
                            (FfnGenres.all.map { it.label } + listOf("K", "K+", "T", "M")).filter { it.contains(term, ignoreCase = true) }
                        },
                    )
                }
            }
            if (tags.isEmpty()) item { Empty("No blocked tags.") }
            items(tags, key = { "t-" + it.id }) { b ->
                ListItem(
                    headlineContent = { Text(b.value) },
                    trailingContent = {
                        IconButton(onClick = { scope.launch { container.filters.unblock(site, BlockKind.TAG, b.value) } }) {
                            Icon(Icons.Default.Close, contentDescription = "Unblock")
                        }
                    },
                )
            }

            item { Text("Authors", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp)) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = author, onValueChange = { author = it }, singleLine = true,
                        label = { Text("Username or pseud") }, modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val name = author.trim()
                        if (name.isNotEmpty()) scope.launch { container.filters.block(site, BlockKind.AUTHOR, name) }
                        author = ""
                    }) { Text("Block") }
                }
            }
            if (authors.isEmpty()) item { Empty("No blocked authors.") }
            items(authors, key = { "a-" + it.id }) { b ->
                ListItem(
                    headlineContent = { Text(b.value) },
                    trailingContent = {
                        IconButton(onClick = { scope.launch { container.filters.unblock(site, BlockKind.AUTHOR, b.value) } }) {
                            Icon(Icons.Default.Close, contentDescription = "Unblock")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
}
