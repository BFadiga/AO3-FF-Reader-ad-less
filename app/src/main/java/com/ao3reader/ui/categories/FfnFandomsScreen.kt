package com.ao3reader.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ao3reader.data.model.FfnFandom
import com.ao3reader.data.model.FfnFilter
import com.ao3reader.data.model.FfnMedia
import com.ao3reader.data.repo.FfnRepository
import com.ao3reader.data.model.Site
import com.ao3reader.data.repo.Ao3Repository
import com.ao3reader.data.repo.FilterRepository
import com.ao3reader.ui.Navigator
import com.ao3reader.ui.components.ScreenScaffold
import com.ao3reader.ui.components.appContainer
import com.ao3reader.ui.components.appViewModel
import com.ao3reader.ui.components.userMessage
import kotlinx.coroutines.launch

class FfnFandomsViewModel(private val ffn: FfnRepository, private val media: FfnMedia) : ViewModel() {
    var fandoms by mutableStateOf<List<FfnFandom>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    init { load() }

    fun load() {
        loading = true
        error = null
        viewModelScope.launch {
            try {
                fandoms = ffn.fandoms(media.path)
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                loading = false
            }
        }
    }
}

/** Every FanFiction.net fandom in a medium; tap one to browse its stories, star it to keep it in Categories. */
@Composable
fun FfnFandomsScreen(media: FfnMedia, nav: Navigator) {
    val vm = appViewModel(key = "ffn-" + media.name) { FfnFandomsViewModel(it.ffn, media) }
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val favorites by container.filters.favorites(Site.FFN).collectAsStateWithLifecycle(emptyList())
    val favoriteNames = favorites.map { it.name }.toSet()
    var query by remember { mutableStateOf("") }
    var byPopularity by remember { mutableStateOf(true) }

    val shown = vm.fandoms
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .let { list -> if (byPopularity) list.sortedByDescending { storyCount(it.count) } else list.sortedBy { it.name.lowercase() } }

    ScreenScaffold(title = media.label, onBack = { nav.back() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Filter fandoms") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = byPopularity, onClick = { byPopularity = true }, label = { Text("Most stories") })
                FilterChip(selected = !byPopularity, onClick = { byPopularity = false }, label = { Text("A–Z") })
            }
            when {
                vm.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                vm.error != null -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(vm.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.load() }) { Text("Retry") }
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.path }) { fandom ->
                        val fav = fandom.name in favoriteNames
                        ListItem(
                            headlineContent = { Text(fandom.name) },
                            supportingContent = { if (fandom.count.isNotBlank()) Text("${fandom.count} stories") },
                            trailingContent = {
                                IconButton(onClick = {
                                    scope.launch {
                                        if (fav) container.filters.removeFavorite(Site.FFN, fandom.name)
                                        else container.filters.addFavorite(Site.FFN, fandom.name, FilterRepository.SECTION_FANDOMS, fandom.path)
                                    }
                                }) {
                                    Icon(
                                        if (fav) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = if (fav) "Remove from favorites" else "Add to favorites",
                                        tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            modifier = Modifier.clickable { nav.ffnSearch(FfnFilter(fandom = fandom)) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** "15.2K" -> 15200. */
private fun storyCount(text: String): Double {
    val t = text.trim().uppercase().replace(",", "")
    val n = t.trimEnd('K', 'M').toDoubleOrNull() ?: return 0.0
    return when {
        t.endsWith("K") -> n * 1_000
        t.endsWith("M") -> n * 1_000_000
        else -> n
    }
}
