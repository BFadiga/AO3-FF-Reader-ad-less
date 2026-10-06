package com.ao3reader.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Person
import com.ao3reader.data.local.BlockKind
import com.ao3reader.data.model.FfnGenres
import com.ao3reader.data.model.Site
import com.ao3reader.data.repo.FilterRepository
import kotlinx.coroutines.launch

/**
 * Actions for a tag: browse its works, pin it to that site's Categories, or block it on that site.
 * [fandomPath] is a FanFiction.net fandom's address when the tag is known to be one.
 */
@Composable
fun TagActionDialog(
    site: Site,
    tag: String,
    onBrowse: () -> Unit,
    onDismiss: () -> Unit,
    suggestedSection: String = FilterRepository.SECTION_OTHER,
    fandomPath: String? = null,
    isFandom: Boolean = false,
) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val favorites by container.filters.favorites(site).collectAsStateWithLifecycle(emptyList())
    val isFavorite = favorites.any { it.name == tag }
    val target = when {
        site == Site.AO3 -> tag
        fandomPath != null -> fandomPath
        FfnGenres.byName(tag) != null -> "genre:" + FfnGenres.byName(tag)!!.value
        isFandom -> "fandom:$tag"
        else -> "char:$tag"
    }
    val sections = if (site == Site.AO3) FilterRepository.SECTIONS else FilterRepository.FFN_SECTIONS
    var pickingSection by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tag) },
        text = {
            Column {
                if (!pickingSection) {
                    DialogRow("Browse works", Icons.AutoMirrored.Filled.List) { onDismiss(); onBrowse() }
                    if (isFavorite) {
                        DialogRow("Remove from favorites", Icons.Default.Star) {
                            scope.launch { container.filters.removeFavorite(site, tag) }
                            onDismiss()
                        }
                    } else {
                        DialogRow("Add to favorites…", Icons.Default.StarBorder) { pickingSection = true }
                    }
                    DialogRow("Block this tag on ${site.shortLabel}", Icons.Default.Block, tint = MaterialTheme.colorScheme.error) {
                        scope.launch { container.filters.block(site, BlockKind.TAG, tag) }
                        onDismiss()
                    }
                } else {
                    Text("Show it under:", style = MaterialTheme.typography.labelLarge)
                    sections.forEach { section ->
                        DialogRow(section + if (section == suggestedSection) " (suggested)" else "", null) {
                            scope.launch { container.filters.addFavorite(site, tag, section, target) }
                            onDismiss()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun BlockAuthorDialog(site: Site, author: String, onDismiss: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block $author?") },
        text = { Text("Their works will be hidden from search results and listings on ${site.label}. You can undo this in Settings.") },
        confirmButton = {
            TextButton(onClick = {
                scope.launch { container.filters.block(site, BlockKind.AUTHOR, author) }
                onDismiss()
            }) { Text("Block") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Tapping an author's name: see everything they've written, or block them. */
@Composable
fun AuthorDialog(site: Site, author: String, onWorks: () -> Unit, onDismiss: () -> Unit) {
    var confirmBlock by remember { mutableStateOf(false) }
    if (confirmBlock) {
        BlockAuthorDialog(site, author, onDismiss)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(author) },
        text = {
            Column {
                DialogRow("See their works", Icons.Default.Person) { onDismiss(); onWorks() }
                DialogRow("Block this author", Icons.Default.Block, tint = MaterialTheme.colorScheme.error) { confirmBlock = true }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun DialogRow(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(text, color = tint) },
        leadingContent = { if (icon != null) Icon(icon, contentDescription = null, tint = tint) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 0.dp),
    )
    HorizontalDivider()
}
