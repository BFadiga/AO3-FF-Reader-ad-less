package com.ao3reader.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ao3reader.data.model.Site
import kotlinx.coroutines.launch

/** The AO3 / FanFiction.net switch at the top right of Search and Categories. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteSwitch(current: Site, modifier: Modifier = Modifier) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    SingleChoiceSegmentedButtonRow(modifier.padding(end = 8.dp).height(36.dp)) {
        Site.entries.forEachIndexed { i, site ->
            SegmentedButton(
                selected = current == site,
                onClick = { scope.launch { container.settings.update { it.copy(currentSite = site) } } },
                shape = SegmentedButtonDefaults.itemShape(i, Site.entries.size),
                icon = {},
                modifier = Modifier.widthIn(min = 64.dp),
            ) {
                Text(site.shortLabel, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}
