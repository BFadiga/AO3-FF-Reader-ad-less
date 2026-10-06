package com.ao3reader.ui

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.ao3reader.AppContainer
import com.ao3reader.data.model.Ao3Media
import com.ao3reader.data.model.FfnFilter
import com.ao3reader.data.model.FfnGenres
import com.ao3reader.data.model.FfnMedia
import com.ao3reader.data.model.Site
import com.ao3reader.ui.author.AuthorScreen
import com.ao3reader.ui.categories.FfnFandomsScreen
import com.ao3reader.ui.login.LoginScreen
import com.ao3reader.ui.login.WebScreen
import com.ao3reader.ui.work.ReviewsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.ao3reader.ui.categories.CategoriesScreen
import com.ao3reader.ui.categories.FandomsScreen
import com.ao3reader.ui.components.appContainer
import com.ao3reader.ui.library.LibraryScreen
import com.ao3reader.ui.reader.ReaderScreen
import com.ao3reader.ui.search.SearchScreen
import com.ao3reader.ui.settings.BlockedScreen
import com.ao3reader.ui.settings.ReaderSettingsScreen
import com.ao3reader.ui.settings.SettingsScreen
import com.ao3reader.ui.tag.TagWorksScreen
import com.ao3reader.ui.work.WorkScreen

object Routes {
    const val CATEGORIES = "categories"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val BLOCKED = "settings/blocked"
    const val READER_SETTINGS = "settings/reader"

    fun tag(name: String) = "tag/${Uri.encode(name)}"
    fun fandoms(media: Ao3Media) = "fandoms/${media.name}"
    fun ffnFandoms(media: FfnMedia) = "ffn-fandoms/${media.name}"
    fun author(site: Site, id: String, name: String) = "author/${site.name}?id=${Uri.encode(id)}&name=${Uri.encode(name)}"
    fun login(site: Site) = "login/${site.name}"
    fun web(url: String) = "web?url=${Uri.encode(url)}"
    fun reviews(id: Long) = "reviews/$id"
    fun work(id: Long) = "work/$id"
    fun reader(id: Long, chapter: Int = 0) = "reader/$id?chapter=$chapter"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.CATEGORIES, "Categories", Icons.Default.Category),
    TopLevel(Routes.SEARCH, "Search", Icons.Default.Search),
    TopLevel(Routes.LIBRARY, "Library", Icons.AutoMirrored.Filled.LibraryBooks),
    TopLevel(Routes.SETTINGS, "Settings", Icons.Default.Settings),
)

/** Navigation actions shared by every screen. */
class Navigator(private val nav: NavHostController, private val container: AppContainer, private val scope: CoroutineScope) {
    fun back() = nav.popBackStack()
    fun tag(name: String) = nav.navigate(Routes.tag(name))
    fun fandoms(media: Ao3Media) = nav.navigate(Routes.fandoms(media))
    fun ffnFandoms(media: FfnMedia) = nav.navigate(Routes.ffnFandoms(media))
    fun author(site: Site, id: String, name: String) = nav.navigate(Routes.author(site, id, name))
    fun login(site: Site) = nav.navigate(Routes.login(site))
    fun web(url: String) = nav.navigate(Routes.web(url))
    fun reviews(id: Long) = nav.navigate(Routes.reviews(id))

    /** Switches to the Search tab on FanFiction.net and runs [filter]. */
    fun ffnSearch(filter: FfnFilter) {
        container.ffnSearchRequest.value = filter
        scope.launch { container.settings.update { it.copy(currentSite = Site.FFN) } }
        tab(Routes.SEARCH)
    }

    /**
     * Opens a tag's works. On AO3 that's the tag's own page; on FanFiction.net a genre, character or
     * fandom becomes a search filter, keeping the fandom being browsed when there is one.
     */
    fun browseTag(site: Site, tag: String, fandomHint: String? = null, isFandom: Boolean = false) {
        if (site == Site.AO3) return tag(tag)
        scope.launch {
            val genre = FfnGenres.byName(tag)
            val filter = when {
                isFandom -> runCatching { container.ffn.findFandom(tag) }.getOrNull()?.let { FfnFilter(fandom = it) }
                    ?: FfnFilter(keywords = tag)
                genre != null -> {
                    val current = container.ffnLastFilter
                    val hint = fandomHint?.let { runCatching { container.ffn.findFandom(it) }.getOrNull() }
                    current.copy(fandom = hint ?: current.fandom, includeGenres = (current.includeGenres + genre).distinct().take(2))
                }
                // A character: search for the name within the fandom (searching needs no fandom page).
                else -> FfnFilter(keywords = tag, fandom = fandomHint?.let { com.ao3reader.data.model.FfnFandom(it, "") })
            }
            ffnSearch(filter)
        }
    }

    fun tab(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    fun work(id: Long) = nav.navigate(Routes.work(id))
    fun reader(id: Long, chapter: Int = 0) = nav.navigate(Routes.reader(id, chapter))
    fun blocked() = nav.navigate(Routes.BLOCKED)
    fun readerSettings() = nav.navigate(Routes.READER_SETTINGS)
}

@Composable
fun AppNavigation(openWorkId: Long?, onOpenWorkHandled: () -> Unit) {
    val nav = rememberNavController()
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val navigator = remember(nav) { Navigator(nav, container, scope) }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottomBar = route == null || topLevel.any { it.route == route }
    val library by container.library.observeLibrary().collectAsStateWithLifecycle(emptyList())
    val updates = library.count { it.newChapters > 0 }

    LaunchedEffect(openWorkId) {
        if (openWorkId != null) {
            navigator.work(openWorkId)
            onOpenWorkHandled()
        }
    }

    Scaffold(
        // Each screen applies its own insets; this scaffold only reserves room for the bottom bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevel.forEach { item ->
                        NavigationBarItem(
                            selected = route == item.route,
                            onClick = { navigator.tab(item.route) },
                            icon = {
                                if (item.route == Routes.LIBRARY && updates > 0) {
                                    BadgedBox(badge = { Badge { Text(updates.toString()) } }) { Icon(item.icon, contentDescription = null) }
                                } else {
                                    Icon(item.icon, contentDescription = null)
                                }
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Routes.CATEGORIES) { CategoriesScreen(navigator) }
            composable(Routes.SEARCH) { SearchScreen(navigator) }
            composable(Routes.LIBRARY) { LibraryScreen(navigator) }
            composable(Routes.SETTINGS) { SettingsScreen(navigator) }
            composable(Routes.BLOCKED) { BlockedScreen(navigator) }
            composable(Routes.READER_SETTINGS) { ReaderSettingsScreen(navigator) }
            composable("tag/{name}", arguments = listOf(navArgument("name") { type = NavType.StringType })) {
                TagWorksScreen(Uri.decode(it.arguments?.getString("name").orEmpty()), navigator)
            }
            composable("fandoms/{media}") {
                val media = Ao3Media.valueOf(it.arguments?.getString("media") ?: Ao3Media.ANIME.name)
                FandomsScreen(media, navigator)
            }
            composable("ffn-fandoms/{media}") {
                val media = FfnMedia.valueOf(it.arguments?.getString("media") ?: FfnMedia.ANIME.name)
                FfnFandomsScreen(media, navigator)
            }
            composable(
                "author/{site}?id={id}&name={name}",
                arguments = listOf(
                    navArgument("site") { type = NavType.StringType },
                    navArgument("id") { type = NavType.StringType; defaultValue = "" },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                AuthorScreen(
                    site = Site.valueOf(it.arguments?.getString("site") ?: Site.AO3.name),
                    authorId = Uri.decode(it.arguments?.getString("id").orEmpty()),
                    name = Uri.decode(it.arguments?.getString("name").orEmpty()),
                    nav = navigator,
                )
            }
            composable("login/{site}") {
                LoginScreen(Site.valueOf(it.arguments?.getString("site") ?: Site.AO3.name), navigator)
            }
            composable("web?url={url}", arguments = listOf(navArgument("url") { type = NavType.StringType; defaultValue = "" })) {
                WebScreen(Uri.decode(it.arguments?.getString("url").orEmpty()), navigator)
            }
            composable("reviews/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                ReviewsScreen(it.arguments!!.getLong("id"), navigator)
            }
            composable("work/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                WorkScreen(it.arguments!!.getLong("id"), navigator)
            }
            composable(
                "reader/{id}?chapter={chapter}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("chapter") { type = NavType.IntType; defaultValue = 0 },
                ),
            ) {
                ReaderScreen(it.arguments!!.getLong("id"), it.arguments!!.getInt("chapter"), navigator)
            }
        }
    }
}
