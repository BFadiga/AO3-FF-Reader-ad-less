package com.ao3reader.ui.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ao3reader.AppContainer
import com.ao3reader.data.model.Chapter
import com.ao3reader.data.model.WorkSummary
import com.ao3reader.data.prefs.AppSettings
import com.ao3reader.data.prefs.ReaderTheme
import com.ao3reader.ui.Navigator
import com.ao3reader.ui.components.appContainer
import com.ao3reader.ui.components.appViewModel
import com.ao3reader.data.model.Site
import com.ao3reader.data.model.WorkIds
import com.ao3reader.data.remote.ffn.FfnUrls
import com.ao3reader.ui.components.userMessage
import com.ao3reader.ui.components.verificationUrl
import com.ao3reader.ui.settings.ReaderSettingsPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ReaderViewModel(private val c: AppContainer, private val workId: Long, private val requestedChapter: Int) : ViewModel() {
    var work by mutableStateOf<WorkSummary?>(null); private set
    var chapters by mutableStateOf<List<Chapter>>(emptyList()); private set
    var chapterIndex by mutableStateOf(1); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set
    /** Fetching a chapter that didn't come with the work (FanFiction.net sends them one at a time). */
    var chapterLoading by mutableStateOf(false); private set
    var chapterError by mutableStateOf<String?>(null); private set
    var verifyUrl by mutableStateOf<String?>(null); private set

    /** Scroll position in the current chapter (0..1); restored whenever the page is (re)loaded. */
    var progress = 0f
        private set
    private var saveJob: Job? = null

    init { load() }

    fun load() {
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val entry = c.library.get(workId)
                val (summary, list) = c.library.chapters(workId)
                work = summary ?: entry?.toSummary()
                chapters = list
                val start = when {
                    requestedChapter > 0 -> requestedChapter
                    entry != null && entry.lastReadChapter > 0 -> entry.lastReadChapter
                    else -> 1
                }.coerceIn(1, list.size.coerceAtLeast(1))
                progress = if (entry != null && entry.lastReadChapter == start) entry.lastReadProgress else 0f
                chapterIndex = start
                save()
                ensureLoaded()
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                loading = false
            }
        }
    }

    val chapter: Chapter? get() = chapters.getOrNull(chapterIndex - 1)
    val hasNext get() = chapterIndex < chapters.size
    val hasPrevious get() = chapterIndex > 1

    fun goTo(index: Int) {
        if (index !in 1..chapters.size || index == chapterIndex) return
        chapterIndex = index
        progress = 0f
        save()
        ensureLoaded()
    }

    /** Fetches the current chapter's text if it isn't here yet. */
    fun ensureLoaded() {
        val c0 = chapter ?: return
        if (c0.contentHtml.isNotBlank() || chapterLoading) return
        chapterLoading = true
        chapterError = null
        verifyUrl = null
        val index = c0.index
        viewModelScope.launch {
            try {
                val text = c.library.chapterText(workId, index)
                chapters = chapters.map { if (it.index == index) it.copy(contentHtml = text) else it }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                chapterError = e.userMessage()
                verifyUrl = e.verificationUrl()
            } finally {
                chapterLoading = false
            }
        }
    }

    fun onScrolled(ratio: Float) {
        progress = ratio
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1_000)
            save()
        }
    }

    private fun save() {
        val w = work ?: return
        viewModelScope.launch { c.library.saveProgress(w, chapterIndex, progress) }
    }

    override fun onCleared() {
        // viewModelScope is already cancelled here, so the final save gets its own scope.
        val w = work ?: return
        val chapter = chapterIndex
        val p = progress
        CoroutineScope(Dispatchers.IO).launch { c.library.saveProgress(w, chapter, p) }
    }
}

private fun cssColor(color: Color): String = String.format("#%06X", 0xFFFFFF and color.toArgb())

@Composable
private fun readerColors(settings: AppSettings): ReaderColors {
    val scheme = MaterialTheme.colorScheme
    val accent = cssColor(scheme.primary)
    return when (settings.reader.theme) {
        ReaderTheme.APP -> ReaderColors(cssColor(scheme.background), cssColor(scheme.onBackground), cssColor(scheme.outlineVariant), accent)
        ReaderTheme.BLACK -> ReaderColors("#000000", "#D4D4D4", "#333333", accent)
        ReaderTheme.DARK -> ReaderColors("#1E1E1E", "#DADADA", "#3A3A3A", accent)
        ReaderTheme.SEPIA -> ReaderColors("#F4ECD8", "#5B4636", "#D8C8A8", "#8B4513")
        ReaderTheme.LIGHT -> ReaderColors("#FFFFFF", "#222222", "#DDDDDD", accent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderScreen(workId: Long, startChapter: Int, nav: Navigator) {
    val vm = appViewModel { ReaderViewModel(it, workId, startChapter) }
    val container = appContainer()
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()
    var barsVisible by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    val colors = readerColors(settings)
    val bg = remember(colors.background) { Color(AndroidColor.parseColor(colors.background)) }

    val chapter = vm.chapter
    val html = chapter?.takeIf { it.contentHtml.isNotBlank() }?.let {
        ReaderHtml.build(
            chapter = it,
            workTitle = vm.work?.title.orEmpty(),
            settings = settings.reader,
            colors = colors,
            hasNext = vm.hasNext,
            isFirst = vm.chapterIndex == 1,
            workSummaryHtml = vm.work?.summaryHtml,
        )
    }
    val currentVm by rememberUpdatedState(vm)

    BackHandler(enabled = showSettings || showChapters) {
        showSettings = false
        showChapters = false
    }

    Box(Modifier.fillMaxSize().background(bg)) {
        when {
            vm.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            vm.error != null -> Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(vm.error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.load() }) { Text("Retry") }
                TextButton(onClick = { nav.back() }) { Text("Back") }
            }
            html == null && vm.chapterError != null -> Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(vm.chapterError!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                vm.verifyUrl?.let { url -> TextButton(onClick = { nav.web(url) }) { Text("Verify") } }
                Button(onClick = { vm.ensureLoaded() }) { Text("Retry") }
            }
            html == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            html != null -> {
                val loaded = remember { arrayOf<String?>(null) }
                AndroidView(
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setBackgroundColor(AndroidColor.TRANSPARENT)
                            // `this.` because the composable's own `settings` (app settings) would shadow WebView.settings.
                            this.settings.javaScriptEnabled = true
                            this.settings.builtInZoomControls = false
                            isVerticalScrollBarEnabled = true
                            val main = Handler(Looper.getMainLooper())
                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun onScroll(ratio: Float) {
                                    main.post { currentVm.onScrolled(ratio) }
                                }

                                @JavascriptInterface
                                fun next() {
                                    main.post { currentVm.goTo(currentVm.chapterIndex + 1) }
                                }

                                /** Tapping the top or bottom fifth turns the page; the middle toggles the bars. */
                                @JavascriptInterface
                                fun onTap(y: Float) {
                                    main.post {
                                        when {
                                            y < 0.2f -> pageUp(false)
                                            y > 0.8f -> pageDown(false)
                                            else -> barsVisible = !barsVisible
                                        }
                                    }
                                }
                            }, "Reader")
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                                    return true
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    val r = currentVm.progress
                                    if (r > 0f) view.evaluateJavascript("window.restoreScroll($r)", null)
                                }
                            }
                        }
                    },
                    update = { web ->
                        web.keepScreenOn = settings.reader.keepScreenOn
                        if (loaded[0] != html) {
                            loaded[0] = html
                            val base = WorkIds.site(workId).baseUrl + "/"
                            web.loadDataWithBaseURL(base, html, "text/html", "utf-8", null)
                        }
                    },
                )
            }
        }

        // Top bar
        AnimatedVisibility(barsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f), tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Column(Modifier.weight(1f)) {
                        Text(vm.work?.title.orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(chapter?.title.orEmpty(), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showChapters = true }) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Chapters") }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.TextFields, contentDescription = "Reading settings") }
                }
            }
        }

        // Bottom bar
        AnimatedVisibility(barsVisible && vm.chapters.size > 1, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f), tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { vm.goTo(vm.chapterIndex - 1) }, enabled = vm.hasPrevious) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null)
                        Text("Previous")
                    }
                    Text(
                        "${vm.chapterIndex} / ${vm.chapters.size}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable { showChapters = true }.padding(8.dp),
                    )
                    TextButton(onClick = { vm.goTo(vm.chapterIndex + 1) }, enabled = vm.hasNext) {
                        Text("Next")
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                    }
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                Text("Reading settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                ReaderSettingsPanel(settings.reader) { transform ->
                    scope.launch { container.settings.updateReader(transform) }
                }
            }
        }
    }

    if (showChapters) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (vm.chapterIndex - 3).coerceAtLeast(0))
        AlertDialog(
            onDismissRequest = { showChapters = false },
            title = { Text("Chapters") },
            text = {
                LazyColumn(state = listState) {
                    items(vm.chapters, key = { it.index }) { c ->
                        val current = c.index == vm.chapterIndex
                        ListItem(
                            headlineContent = {
                                Text(c.title, fontWeight = if (current) FontWeight.Bold else null,
                                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { vm.goTo(c.index); showChapters = false },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChapters = false }) { Text("Close") } },
        )
    }
}
