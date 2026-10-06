package com.ao3reader.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.delay

/**
 * A text field that suggests tags as you type: AO3's tag autocomplete by default, or [suggest]
 * (e.g. FanFiction.net's genres and a fandom's characters).
 */
@Composable
fun TagSearchField(
    label: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    clearOnPick: Boolean = true,
    suggest: (suspend (String) -> List<String>)? = null,
) {
    val container = appContainer()
    var text by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(text) {
        if (text.trim().length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(400) // debounce typing
        val term = text.trim()
        suggestions = runCatching { suggest?.invoke(term) ?: container.ao3.autocompleteTags(term) }.getOrDefault(emptyList()).take(8)
    }

    Column(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (text.isNotBlank()) {
                    onPick(text.trim())
                    suggestions = emptyList()
                    if (clearOnPick) text = ""
                }
            }),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (text.isNotEmpty()) IconButton(onClick = { text = "" }) { Icon(Icons.Default.Clear, contentDescription = "Clear") }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (suggestions.isNotEmpty()) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                // Always offer the exact text too, in case AO3 has no suggestion for it.
                (listOf(text.trim()) + suggestions).distinct().forEach { s ->
                    ListItem(
                        headlineContent = { Text(s) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            onPick(s)
                            suggestions = emptyList()
                            text = if (clearOnPick) "" else s
                        },
                    )
                }
            }
        }
    }
}
