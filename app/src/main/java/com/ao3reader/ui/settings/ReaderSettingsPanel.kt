package com.ao3reader.ui.settings

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ao3reader.data.prefs.ReaderFont
import com.ao3reader.data.prefs.ReaderSettings
import com.ao3reader.data.prefs.ReaderTheme
import com.ao3reader.data.prefs.TextAlign
import kotlin.math.roundToInt

/** Controls for every reader setting; used in the reader's sheet and in Settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderSettingsPanel(settings: ReaderSettings, onChange: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SliderRow("Font size", "${settings.fontSize}px", settings.fontSize.toFloat(), 12f..32f, steps = 19) { v ->
            onChange { it.copy(fontSize = v.roundToInt()) }
        }
        SliderRow("Line spacing", "%.1f".format(settings.lineSpacing), settings.lineSpacing, 1.0f..2.6f, steps = 15) { v ->
            onChange { it.copy(lineSpacing = (v * 10).roundToInt() / 10f) }
        }
        SliderRow("Paragraph spacing", "%.1f".format(settings.paragraphSpacing), settings.paragraphSpacing, 0f..2f, steps = 19) { v ->
            onChange { it.copy(paragraphSpacing = (v * 10).roundToInt() / 10f) }
        }
        SliderRow("Side margins", "${settings.margin}px", settings.margin.toFloat(), 0f..48f, steps = 11) { v ->
            onChange { it.copy(margin = v.roundToInt()) }
        }

        Label("Font")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReaderFont.entries.forEach { f ->
                FilterChip(selected = settings.font == f, onClick = { onChange { it.copy(font = f) } }, label = { Text(f.label) })
            }
        }
        Label("Text alignment")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextAlign.entries.forEach { a ->
                FilterChip(selected = settings.align == a, onClick = { onChange { it.copy(align = a) } }, label = { Text(a.label) })
            }
        }
        Label("Page color")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReaderTheme.entries.forEach { t ->
                FilterChip(selected = settings.theme == t, onClick = { onChange { it.copy(theme = t) } }, label = { Text(t.label) })
            }
        }
        SwitchRow("Show author's notes and summaries", settings.showAuthorNotes) { v -> onChange { it.copy(showAuthorNotes = v) } }
        SwitchRow(
            "Keep author's colors and fonts",
            settings.useWorkSkins,
            "Bold, italics and other emphasis are always shown as the author wrote them",
        ) { v -> onChange { it.copy(useWorkSkins = v) } }
        SwitchRow("Keep screen on while reading", settings.keepScreenOn) { v -> onChange { it.copy(keepScreenOn = v) } }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SliderRow(
    label: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(label)
            Text(value, style = MaterialTheme.typography.labelLarge)
        }
        Slider(value = current.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
