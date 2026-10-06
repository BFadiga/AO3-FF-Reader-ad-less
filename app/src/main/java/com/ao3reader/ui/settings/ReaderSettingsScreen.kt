package com.ao3reader.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ao3reader.data.prefs.AppSettings
import com.ao3reader.data.prefs.ReaderFont
import com.ao3reader.data.prefs.ReaderTheme
import com.ao3reader.data.prefs.TextAlign
import com.ao3reader.ui.Navigator
import com.ao3reader.ui.components.ScreenScaffold
import com.ao3reader.ui.components.appContainer
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextAlign as ComposeTextAlign

@Composable
fun ReaderSettingsScreen(nav: Navigator) {
    val container = appContainer()
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()
    val r = settings.reader

    ScreenScaffold(title = "Reader", onBack = { nav.back() }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            // Live preview of the current settings.
            val (bg, fg) = when (r.theme) {
                ReaderTheme.APP -> MaterialTheme.colorScheme.background to MaterialTheme.colorScheme.onBackground
                ReaderTheme.BLACK -> Color.Black to Color(0xFFD4D4D4)
                ReaderTheme.DARK -> Color(0xFF1E1E1E) to Color(0xFFDADADA)
                ReaderTheme.SEPIA -> Color(0xFFF4ECD8) to Color(0xFF5B4636)
                ReaderTheme.LIGHT -> Color.White to Color(0xFF222222)
            }
            Surface(color = bg, shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Text(
                    buildAnnotatedString {
                        append("The archive hummed quietly as the next chapter loaded. ")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("She settled in") }
                        append(", adjusted the lamp, and ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("began to read.") }
                    },
                    modifier = Modifier.padding(horizontal = r.margin.dp.coerceAtLeast(8.dp), vertical = 16.dp),
                    style = TextStyle(
                        color = fg,
                        fontSize = r.fontSize.sp,
                        lineHeight = r.lineSpacing.em,
                        fontFamily = when (r.font) {
                            ReaderFont.SERIF -> FontFamily.Serif
                            ReaderFont.MONO -> FontFamily.Monospace
                            else -> FontFamily.SansSerif
                        },
                        textAlign = when (r.align) {
                            TextAlign.LEFT -> ComposeTextAlign.Start
                            TextAlign.JUSTIFY -> ComposeTextAlign.Justify
                            TextAlign.CENTER -> ComposeTextAlign.Center
                            TextAlign.RIGHT -> ComposeTextAlign.End
                        },
                    ),
                )
            }
            Spacer(Modifier.height(16.dp))
            ReaderSettingsPanel(r) { transform -> scope.launch { container.settings.updateReader(transform) } }
        }
    }
}
