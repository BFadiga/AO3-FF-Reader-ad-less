package com.ao3reader.ui.categories

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ao3reader.data.local.FavoriteTag
import com.ao3reader.data.model.Ao3Media
import com.ao3reader.data.model.FfnFandom
import com.ao3reader.data.model.FfnFilter
import com.ao3reader.data.model.FfnGenres
import com.ao3reader.data.model.FfnMedia
import com.ao3reader.data.model.Site
import com.ao3reader.data.prefs.AppSettings
import com.ao3reader.ui.components.SiteSwitch
import com.ao3reader.data.repo.FilterRepository
import com.ao3reader.ui.Navigator
import com.ao3reader.ui.components.ScreenScaffold
import com.ao3reader.ui.components.TagActionDialog
import com.ao3reader.ui.components.TagSearchField
import com.ao3reader.ui.components.appContainer

private val genreTags = listOf(
    "Romance", "Drama", "Angst", "Fluff", "Hurt/Comfort", "Humor", "Action/Adventure", "Mystery",
    "Horror", "Tragedy", "Slice of Life", "Alternate Universe", "Canon Divergence", "Slow Burn",
    "Enemies to Lovers", "Friends to Lovers", "Found Family", "Fix-It", "Time Travel", "Reincarnation",
    "Isekai", "Smut", "Crack", "Whump",
)

private val povTags = listOf(
    "POV Male Character", "POV Female Character", "POV First Person", "POV Second Person",
    "POV Third Person", "POV Alternating", "POV Multiple", "Reader-Insert",
)

private val relationshipCategories = listOf("F/M", "M/M", "F/F", "Gen", "Multi", "Other")

private fun mediaIcon(media: Ao3Media): ImageVector = when (media) {
    Ao3Media.ANIME -> Icons.Default.Animation
    Ao3Media.BOOKS -> Icons.Default.Book
    Ao3Media.CARTOONS -> Icons.AutoMirrored.Filled.MenuBook
    Ao3Media.CELEBRITIES -> Icons.Default.Person
    Ao3Media.MOVIES -> Icons.Default.Movie
    Ao3Media.MUSIC -> Icons.Default.MusicNote
    Ao3Media.THEATER -> Icons.Default.TheaterComedy
    Ao3Media.TV -> Icons.Default.Tv
    Ao3Media.GAMES -> Icons.Default.SportsEsports
    Ao3Media.OTHER -> Icons.Default.Widgets
    Ao3Media.UNCATEGORIZED -> Icons.Default.QuestionMark
}

@Composable
fun CategoriesScreen(nav: Navigator) {
    val container = appContainer()
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val site = settings.currentSite
    ScreenScaffold(title = "Categories", isTab = true, actions = { SiteSwitch(site) }) { padding ->
        when (site) {
            Site.AO3 -> Ao3Categories(nav, Modifier.padding(padding))
            Site.FFN -> FfnCategories(nav, Modifier.padding(padding))
            Site.WATTPAD -> WattpadCategories(nav, Modifier.padding(padding))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Ao3Categories(nav: Navigator, modifier: Modifier) {
    val container = appContainer()
    val favorites by container.filters.favorites(Site.AO3).collectAsStateWithLifecycle(emptyList())
    var dialogTag by remember { mutableStateOf<Pair<String, String>?>(null) }

    run {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                TagSearchField(
                    label = "Find any tag, fandom or character",
                    onPick = { dialogTag = it to FilterRepository.SECTION_OTHER },
                )
            }

            item {
                SectionTitle("Favorites", Icons.Default.Star)
                if (favorites.isEmpty()) {
                    Text(
                        "Tap any tag and choose \"Add to favorites\" to pin it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            favorites.groupBy { it.section }.forEach { (section, tags) ->
                item(key = "fav-$section") {
                    Column {
                        Text(section, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        TagChips(
                            tags = tags.map { it.name },
                            highlighted = true,
                            onClick = { nav.tag(it) },
                            onLongClick = { dialogTag = it to section },
                        )
                    }
                }
            }

            item {
                SectionTitle("Browse by medium", null)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 2,
                ) {
                    Ao3Media.entries.forEach { media ->
                        Card(
                            onClick = { nav.fandoms(media) },
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Column(Modifier.padding(12.dp).fillMaxWidth()) {
                                Icon(mediaIcon(media), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(6.dp))
                                Text(media.label, style = MaterialTheme.typography.labelLarge, maxLines = 2)
                            }
                        }
                    }
                }
            }

            item {
                SectionTitle("Genres & tropes", null)
                Hint()
                TagChips(genreTags, onClick = { nav.tag(it) }, onLongClick = { dialogTag = it to FilterRepository.SECTION_GENRES })
            }
            item {
                SectionTitle("Point of view", null)
                Hint()
                TagChips(povTags, onClick = { nav.tag(it) }, onLongClick = { dialogTag = it to FilterRepository.SECTION_POV })
            }
            item {
                SectionTitle("Relationship categories", null)
                Hint()
                TagChips(relationshipCategories, onClick = { nav.tag(it) }, onLongClick = { dialogTag = it to FilterRepository.SECTION_OTHER })
            }
        }
    }

    dialogTag?.let { (tag, section) ->
        TagActionDialog(
            site = Site.AO3,
            tag = tag,
            suggestedSection = section,
            onBrowse = { nav.tag(tag) },
            onDismiss = { dialogTag = null },
        )
    }
}

private fun ffnMediaIcon(media: FfnMedia): ImageVector = when (media) {
    FfnMedia.ANIME -> Icons.Default.Animation
    FfnMedia.BOOKS -> Icons.Default.Book
    FfnMedia.CARTOONS -> Icons.AutoMirrored.Filled.MenuBook
    FfnMedia.COMICS -> Icons.AutoMirrored.Filled.MenuBook
    FfnMedia.GAMES -> Icons.Default.SportsEsports
    FfnMedia.MISC -> Icons.Default.Widgets
    FfnMedia.MOVIES -> Icons.Default.Movie
    FfnMedia.PLAYS -> Icons.Default.TheaterComedy
    FfnMedia.TV -> Icons.Default.Tv
}

/** FanFiction.net's own fandoms, genres and favorites, kept apart from AO3's. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FfnCategories(nav: Navigator, modifier: Modifier) {
    val container = appContainer()
    val favorites by container.filters.favorites(Site.FFN).collectAsStateWithLifecycle(emptyList())
    var dialogTag by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun open(fav: FavoriteTag) {
        when {
            fav.target.startsWith("/") -> nav.ffnSearch(FfnFilter(fandom = FfnFandom(fav.name, fav.target)))
            fav.target.startsWith("fandom:") -> nav.browseTag(Site.FFN, fav.name, isFandom = true)
            else -> nav.browseTag(Site.FFN, fav.name)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            SectionTitle("Favorites", Icons.Default.Star)
            if (favorites.isEmpty()) {
                Text(
                    "Star a fandom, or long-press a genre or character, to pin it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        favorites.groupBy { it.section }.forEach { (section, tags) ->
            item(key = "ffn-fav-$section") {
                Column {
                    Text(section, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    TagChips(
                        tags = tags.map { it.name },
                        highlighted = true,
                        onClick = { name -> tags.firstOrNull { it.name == name }?.let { open(it) } },
                        onLongClick = { dialogTag = it to section },
                    )
                }
            }
        }
        item {
            SectionTitle("Browse fandoms by medium", null)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2,
            ) {
                FfnMedia.entries.forEach { media ->
                    Card(
                        onClick = { nav.ffnFandoms(media) },
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Column(Modifier.padding(12.dp).fillMaxWidth()) {
                            Icon(ffnMediaIcon(media), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                            Text(media.label, style = MaterialTheme.typography.labelLarge, maxLines = 2)
                        }
                    }
                }
            }
        }
        item {
            SectionTitle("Genres", null)
            Hint()
            TagChips(
                FfnGenres.all.map { it.label }.sorted(),
                onClick = { nav.browseTag(Site.FFN, it) },
                onLongClick = { dialogTag = it to FilterRepository.SECTION_GENRES },
            )
        }
    }

    dialogTag?.let { (tag, section) ->
        val fav = favorites.firstOrNull { it.name == tag }
        TagActionDialog(
            site = Site.FFN,
            tag = tag,
            suggestedSection = section,
            fandomPath = fav?.target?.takeIf { it.startsWith("/") },
            isFandom = fav?.target?.startsWith("fandom:") == true,
            onBrowse = { fav?.let { open(it) } ?: nav.browseTag(Site.FFN, tag) },
            onDismiss = { dialogTag = null },
        )
    }
}

/** Wattpad's popular genres (which it treats as tags) plus Wattpad favorites, kept apart from the other sites. */
private val WATTPAD_GENRES = listOf(
    "action", "adventure", "chicklit", "fanfiction", "fantasy", "historicalfiction", "horror", "humor", "lgbt",
    "mystery", "newadult", "paranormal", "poetry", "romance", "sciencefiction", "shortstory", "teenfiction",
    "thriller", "vampire", "werewolf",
)

@Composable
private fun WattpadCategories(nav: Navigator, modifier: Modifier) {
    val container = appContainer()
    val favorites by container.filters.favorites(Site.WATTPAD).collectAsStateWithLifecycle(emptyList())
    var dialogTag by remember { mutableStateOf<Pair<String, String>?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            SectionTitle("Favorites", Icons.Default.Star)
            if (favorites.isEmpty()) {
                Text(
                    "Long-press a tag here or on a story to pin it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        favorites.groupBy { it.section }.forEach { (section, tags) ->
            item(key = "wp-fav-$section") {
                Column {
                    Text(section, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    TagChips(
                        tags = tags.map { it.name },
                        highlighted = true,
                        onClick = { nav.browseTag(Site.WATTPAD, it) },
                        onLongClick = { dialogTag = it to section },
                    )
                }
            }
        }
        item {
            SectionTitle("Genres", null)
            Hint()
            TagChips(
                WATTPAD_GENRES,
                onClick = { nav.browseTag(Site.WATTPAD, it) },
                onLongClick = { dialogTag = it to FilterRepository.SECTION_GENRES },
            )
        }
    }

    dialogTag?.let { (tag, section) ->
        TagActionDialog(
            site = Site.WATTPAD,
            tag = tag,
            suggestedSection = section,
            onBrowse = { nav.browseTag(Site.WATTPAD, tag) },
            onDismiss = { dialogTag = null },
        )
    }
}

@Composable
private fun Hint() {
    Text(
        "Tap to browse · long-press to favorite or block",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun SectionTitle(text: String, icon: ImageVector?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TagChips(
    tags: List<String>,
    onClick: (String) -> Unit,
    onLongClick: (String) -> Unit,
    highlighted: Boolean = false,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { tag ->
            // A plain surface rather than AssistChip so long-press works.
            Text(
                tag,
                style = MaterialTheme.typography.labelLarge,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .combinedClickable(onClick = { onClick(tag) }, onLongClick = { onLongClick(tag) })
                    .background(
                        if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

