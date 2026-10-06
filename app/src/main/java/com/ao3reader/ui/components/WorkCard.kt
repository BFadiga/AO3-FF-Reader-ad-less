package com.ao3reader.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ao3reader.data.model.WorkSummary
import com.ao3reader.data.prefs.AppSettings
import com.ao3reader.data.prefs.ListDensity
import androidx.compose.material3.OutlinedButton
import com.ao3reader.data.model.Site
import com.ao3reader.data.model.site
import com.ao3reader.data.repo.BlockLists
import java.text.NumberFormat

private val numberFormat = NumberFormat.getIntegerInstance()
fun Int.formatted(): String = numberFormat.format(this)

/** Colors for AO3's ratings and FanFiction.net's (K, K+, T, M). */
fun ratingColor(rating: String): Color = when (rating) {
    "General Audiences", "K" -> Color(0xFF6A9F3A)
    "K+" -> Color(0xFF8BAF3A)
    "Teen And Up Audiences", "T" -> Color(0xFFD8A31D)
    "Mature" -> Color(0xFFE07B22)
    "M" -> Color(0xFFE0592A)
    "Explicit" -> Color(0xFFC62828)
    else -> Color(0xFF8A8A8A)
}

fun ratingShort(rating: String): String = when (rating) {
    "General Audiences" -> "G"
    "Teen And Up Audiences" -> "T"
    "Mature" -> "M"
    "Explicit" -> "E"
    "K", "K+", "T", "M" -> rating
    else -> "?"
}

@Composable
fun RatingBadge(rating: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ratingColor(rating)),
        contentAlignment = Alignment.Center,
    ) {
        Text(ratingShort(rating), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun HtmlText(html: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    val text = remember(html) { runCatching { AnnotatedString.fromHtml(html.trim()) }.getOrElse { AnnotatedString(html) } }
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun WorkCard(
    work: WorkSummary,
    settings: AppSettings,
    onClick: () -> Unit,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    badge: String? = null,
    onAuthorClick: ((name: String, id: String) -> Unit)? = null,
) {
    val compact = settings.density == ListDensity.COMPACT
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(if (compact) 10.dp else 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (work.coverUrl != null) {
                    Cover(work.coverUrl, work.title, work.rating, Modifier.size(width = 44.dp, height = 60.dp))
                } else {
                    RatingBadge(work.rating)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        work.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "by " + work.authors.joinToString().ifBlank { "Anonymous" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onAuthorClick != null && work.authors.isNotEmpty() && work.authors.first() != "Anonymous") {
                            Modifier.combinedClickable(onClick = {
                                onAuthorClick(work.authors.first(), work.authorIds.firstOrNull().orEmpty())
                            })
                        } else Modifier,
                    )
                }
                if (badge != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            if (work.fandoms.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    work.fandoms.joinToString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (settings.showTags) {
                val tags = (work.warnings.filter { it != "No Archive Warnings Apply" } +
                    work.relationships + work.characters + work.freeforms).take(settings.maxTagsShown)
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        tags.forEach { tag ->
                            val isWarning = tag in work.warnings
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .combinedClickable(onClick = { onTagClick(tag) })
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
            if (settings.showSummaries && work.summaryHtml.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                HtmlText(work.summaryHtml, maxLines = if (compact) 3 else 6)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                StatText("${work.words.formatted()} words")
                StatText("Ch ${work.chaptersLabel}")
                if (work.kudos > 0) StatText("♥ ${work.kudos.formatted()}")
                if (work.site != Site.AO3 && work.comments > 0) StatText("✎ ${work.comments.formatted()}")
                if (work.complete) StatText("✓ Complete")
                Spacer(Modifier.weight(1f))
                StatText(work.updated)
            }
        }
    }
}

@Composable
private fun StatText(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}

/** Stand-in for a work hidden by the block list; can be revealed with a tap. */
@Composable
fun BlockedWorkCard(reason: String, onShow: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Hidden · $reason",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onShow) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Show")
            }
        }
    }
}

/**
 * A paged, filtered list of works with load-more on scroll.
 * [header] is placed above the results inside the same scrolling list.
 */
@Composable
fun WorkList(
    state: WorkListState,
    settings: AppSettings,
    blockList: BlockLists,
    onWorkClick: (WorkSummary) -> Unit,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    libraryIds: Set<Long> = emptySet(),
    emptyText: String = "No works found.",
    onAuthorClick: ((WorkSummary, String, String) -> Unit)? = null,
    onVerify: ((String) -> Unit)? = null,
    header: (LazyListScope.() -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    var revealed by remember { mutableStateOf(setOf<Long>()) }
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.works.size, state.started) {
        if (nearEnd && state.started && state.canLoadMore) state.loadMore()
    }

    val visible = state.works.mapNotNull { w ->
        val reason = if (w.id in revealed) null else blockList.reasonFor(w)
        when {
            reason == null -> w to null
            settings.hideBlockedCompletely -> null
            else -> w to reason
        }
    }
    val hiddenCount = state.works.size - visible.size

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(if (settings.density == ListDensity.COMPACT) 6.dp else 10.dp),
    ) {
        header?.invoke(this)
        state.heading?.let { h ->
            item(key = "heading") {
                Text(
                    h + if (hiddenCount > 0) " · $hiddenCount hidden by your filters" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(visible, key = { it.first.id }) { (work, reason) ->
            if (reason != null) {
                BlockedWorkCard(reason) { revealed = revealed + work.id }
            } else {
                WorkCard(
                    work = work,
                    settings = settings,
                    onClick = { onWorkClick(work) },
                    onTagClick = onTagClick,
                    badge = if (work.id in libraryIds) "Library" else null,
                    onAuthorClick = onAuthorClick?.let { cb -> { name, id -> cb(work, name, id) } },
                )
            }
        }
        item(key = "footer") {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                when {
                    state.loading -> CircularProgressIndicator()
                    state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val verify = state.verifyUrl
                            if (verify != null && onVerify != null) OutlinedButton(onClick = { onVerify(verify) }) { Text("Verify") }
                            Button(onClick = { state.retry() }) { Text("Retry") }
                        }
                    }
                    state.started && state.works.isEmpty() -> Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.started && !state.canLoadMore && state.works.isNotEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text("End of results", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
