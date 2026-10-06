package com.ao3reader.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ao3reader.data.model.CompletionFilter
import com.ao3reader.data.model.Rating
import com.ao3reader.data.model.SortColumn
import com.ao3reader.data.model.WorkFilter

/** Editor for [WorkFilter]. [searchFields] adds the title/author/included-tag fields used by Search. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterEditor(
    filter: WorkFilter,
    onChange: (WorkFilter) -> Unit,
    searchFields: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (searchFields) {
            OutlinedTextField(
                value = filter.title, onValueChange = { onChange(filter.copy(title = it)) },
                label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = filter.author, onValueChange = { onChange(filter.copy(author = it)) },
                label = { Text("Author") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Label("Must include tags")
            TagList(filter.includeTags) { onChange(filter.copy(includeTags = filter.includeTags - it)) }
            TagSearchField("Add a tag to include", onPick = { onChange(filter.copy(includeTags = (filter.includeTags + it).distinct())) })
        } else {
            OutlinedTextField(
                value = filter.query, onValueChange = { onChange(filter.copy(query = it)) },
                label = { Text("Search within results") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Label("Also include tags")
            TagList(filter.includeTags) { onChange(filter.copy(includeTags = filter.includeTags - it)) }
            TagSearchField("Add a tag to include", onPick = { onChange(filter.copy(includeTags = (filter.includeTags + it).distinct())) })
        }

        Label("Exclude tags")
        TagList(filter.excludeTags) { onChange(filter.copy(excludeTags = filter.excludeTags - it)) }
        TagSearchField("Add a tag to exclude", onPick = { onChange(filter.copy(excludeTags = (filter.excludeTags + it).distinct())) })

        Label("Rating")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = filter.rating == null, onClick = { onChange(filter.copy(rating = null)) }, label = { Text("Any") })
            Rating.entries.forEach { r ->
                FilterChip(selected = filter.rating == r, onClick = { onChange(filter.copy(rating = r)) }, label = { Text(r.label) })
            }
        }

        Label("Status")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompletionFilter.entries.forEach { c ->
                FilterChip(selected = filter.completion == c, onClick = { onChange(filter.copy(completion = c)) }, label = { Text(c.label) })
            }
        }

        Label("Crossovers")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(null to "Include", false to "Exclude", true to "Only crossovers").forEach { (v, label) ->
                FilterChip(selected = filter.crossovers == v, onClick = { onChange(filter.copy(crossovers = v)) }, label = { Text(label) })
            }
        }

        Label("Sort by")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SortColumn.entries
                .filter { searchFields || it != SortColumn.BEST_MATCH }
                .forEach { s ->
                    FilterChip(selected = filter.sort == s, onClick = { onChange(filter.copy(sort = s)) }, label = { Text(s.label) })
                }
        }

        Label("Word count")
        Row {
            OutlinedTextField(
                value = filter.minWords?.toString().orEmpty(),
                onValueChange = { v -> onChange(filter.copy(minWords = v.filter(Char::isDigit).toIntOrNull())) },
                label = { Text("From") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = filter.maxWords?.toString().orEmpty(),
                onValueChange = { v -> onChange(filter.copy(maxWords = v.filter(Char::isDigit).toIntOrNull())) },
                label = { Text("To") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        OutlinedTextField(
            value = filter.language,
            onValueChange = { onChange(filter.copy(language = it.trim().lowercase())) },
            label = { Text("Language code (e.g. en, es, pt-BR)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagList(tags: List<String>, onRemove: (String) -> Unit) {
    if (tags.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { tag ->
            InputChip(
                selected = false,
                onClick = { onRemove(tag) },
                label = { Text(tag) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove") },
            )
        }
    }
}
