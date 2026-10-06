package com.ao3reader.data.remote.ffn

import com.ao3reader.data.model.Chapter
import com.ao3reader.data.model.FfnFandom
import com.ao3reader.data.model.FfnFilterOptions
import com.ao3reader.data.model.FfnGenres
import com.ao3reader.data.model.FfnOption
import com.ao3reader.data.model.Review
import com.ao3reader.data.model.ReviewPage
import com.ao3reader.data.model.WorkDetail
import com.ao3reader.data.model.WorkIds
import com.ao3reader.data.model.WorkPage
import com.ao3reader.data.model.WorkSummary
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.time.Instant
import java.time.ZoneOffset

class FfnException(message: String) : Exception(message)

/** Turns FanFiction.net's HTML into the app's models. Pure JVM so it can be unit tested. */
object FfnParser {

    /** Search results, fandom listings and author pages all use "z-list" story blurbs. */
    fun parseStoryList(html: String, requestedPage: Int = 1, fandomOfPage: String? = null): WorkPage {
        val doc = Jsoup.parse(html, FfnUrls.BASE)
        checkForErrors(doc)
        val works = doc.select("div.z-list").mapNotNull { parseBlurb(it, fandomOfPage) }.distinctBy { it.id }
        val last = lastPage(doc).coerceAtLeast(requestedPage)
        return WorkPage(works, requestedPage, last, heading = pageHeading(doc))
    }

    /** Stories written by the author whose page this is (not their favorites). */
    fun parseAuthorStories(html: String, userId: String): Pair<String, List<WorkSummary>> {
        val doc = Jsoup.parse(html, FfnUrls.BASE)
        checkForErrors(doc)
        val name = doc.title().substringBefore(" | ").substringBefore(" - FanFiction").trim()
            .ifBlank { doc.selectFirst("#content_wrapper_inner span[style*=bold]")?.text()?.trim().orEmpty() }
        val own = doc.select("div.z-list.mystories").ifEmpty { doc.select("#st_inside div.z-list, #st div.z-list") }
        val works = own.mapNotNull { parseBlurb(it, null) }
            .map { w -> if (w.authors.isEmpty()) w.copy(authors = listOf(name), authorIds = listOf(userId)) else w }
        return name to works
    }

    /** [pageFandom] is used when the blurb doesn't name its fandom (it doesn't on a fandom's own listing). */
    fun parseBlurb(el: Element, pageFandom: String?): WorkSummary? {
        val titleLink = el.selectFirst("a.stitle") ?: el.select("a[href^=/s/]").firstOrNull { it.text().isNotBlank() } ?: return null
        val storyId = FfnUrls.storyIdFrom(titleLink.attr("href")) ?: el.attr("data-storyid").toLongOrNull() ?: return null
        val authorLink = el.select("a[href^=/u/]").firstOrNull()
        val meta = el.selectFirst("div.z-padtop2, div.xgray") ?: return null
        val summaryEl = el.selectFirst("div.z-indent")?.clone()?.apply { select("div.z-padtop2, div.xgray").remove() }
        val m = parseMeta(meta, leadingFandom = true)
        val fandoms = m.fandoms.ifEmpty { listOfNotNull(pageFandom) }
        return WorkSummary(
            id = WorkIds.ffn(storyId),
            title = titleLink.ownText().trim().ifBlank { titleLink.text().trim() }.ifBlank { el.attr("data-title") },
            authors = listOfNotNull(authorLink?.text()?.trim()),
            authorIds = listOfNotNull(authorLink?.attr("href")?.let { FfnUrls.userIdFrom(it) }),
            fandoms = fandoms,
            rating = m.rating,
            characters = m.characters,
            freeforms = m.genres,
            summaryHtml = escape(summaryEl?.text()?.trim().orEmpty()),
            language = m.language,
            words = m.words,
            chaptersPosted = m.chapters,
            chaptersTotal = if (m.complete) m.chapters else null,
            kudos = m.favs,
            bookmarks = m.favs,
            follows = m.follows,
            comments = m.reviews,
            updated = m.updated ?: m.published.orEmpty(),
            complete = m.complete,
            coverUrl = coverOf(titleLink) ?: coverOf(el),
        )
    }

    /** A story page (/s/{id}/{chapter}/): metadata, the chapter list and the requested chapter's text. */
    fun parseStoryPage(html: String, storyId: Long, chapterNumber: Int): WorkDetail {
        val doc = Jsoup.parse(html, FfnUrls.BASE)
        checkForErrors(doc)
        val top = doc.selectFirst("#profile_top") ?: throw FfnException("Couldn't read this story from FanFiction.net.")
        val title = top.selectFirst("b.xcontrast_txt")?.text()?.trim().orEmpty()
        val authorLink = top.select("a[href^=/u/]").firstOrNull()
        val summary = top.select("div.xcontrast_txt").firstOrNull { it.selectFirst("span") == null }?.text()?.trim().orEmpty()
        val metaEl = top.selectFirst("span.xgray") ?: throw FfnException("Couldn't read this story's details.")
        val m = parseMeta(metaEl, leadingFandom = false)
        val crumbs = doc.select("#pre_story_links a")
        val fandomText = crumbs.lastOrNull()?.text()?.trim().orEmpty()
        val fandoms = when {
            fandomText.endsWith("Crossover") -> fandomText.removeSuffix("Crossover").trim().split(" + ").map { it.trim() }
            fandomText.isNotBlank() -> listOf(fandomText)
            else -> emptyList()
        }

        val options = doc.selectFirst("select#chap_select")?.select("option").orEmpty()
        val chapterTitles = options.map { o -> o.text().trim().replace(Regex("""^\d+\.\s*"""), "") }
        val count = maxOf(m.chapters, chapterTitles.size, 1)
        val text = doc.selectFirst("#storytext")?.let { cleanStoryText(it) }
            ?: throw FfnException("Couldn't find the story's text on the page.")
        val chapters = (1..count).map { n ->
            val chTitle = chapterTitles.getOrNull(n - 1)?.ifBlank { null } ?: if (count == 1) title else "Chapter $n"
            Chapter(index = n, id = null, title = chTitle, contentHtml = if (n == chapterNumber) text else "")
        }

        val work = WorkSummary(
            id = WorkIds.ffn(storyId),
            title = title,
            authors = listOfNotNull(authorLink?.text()?.trim()),
            authorIds = listOfNotNull(authorLink?.attr("href")?.let { FfnUrls.userIdFrom(it) }),
            fandoms = fandoms,
            rating = m.rating,
            characters = m.characters,
            freeforms = m.genres,
            summaryHtml = escape(summary),
            language = m.language,
            words = m.words,
            chaptersPosted = count,
            chaptersTotal = if (m.complete) count else null,
            kudos = m.favs,
            bookmarks = m.favs,
            follows = m.follows,
            comments = m.reviews,
            updated = m.updated ?: m.published.orEmpty(),
            complete = m.complete,
            coverUrl = coverOf(top)?.let { FfnUrls.largeCover(it) },
        )
        return WorkDetail(summary = work, published = m.published, notesHtml = null, chapters = chapters)
    }

    /** Just the chapter text from a story page. */
    fun parseChapterText(html: String): String {
        val doc = Jsoup.parse(html, FfnUrls.BASE)
        checkForErrors(doc)
        return doc.selectFirst("#storytext")?.let { cleanStoryText(it) }
            ?: throw FfnException("Couldn't find the chapter's text on the page.")
    }

    /** A media page such as /anime/: every fandom with its story count. */
    fun parseFandoms(html: String): List<FfnFandom> {
        val doc = Jsoup.parse(html, FfnUrls.BASE)
        checkForErrors(doc)
        val root = doc.selectFirst("#list_output") ?: doc.body()
        return root.select("a[href]").mapNotNull { a ->
            val href = a.attr("href")
            if (!href.startsWith("/") || href.count { it == '/' } < 3 || href.startsWith("/s/") || href.startsWith("/u/")) return@mapNotNull null
            val name = a.attr("title").ifBlank { a.text() }.trim()
            if (name.isBlank()) return@mapNotNull null
            val count = (a.nextElementSibling()?.takeIf { it.hasClass("gray") }?.text()
                ?: (a.nextSibling() as? TextNode)?.text()).orEmpty().trim().removeSurrounding("(", ")")
            FfnFandom(name, href, count)
        }.distinctBy { it.path }
    }

    /** The choices in a fandom page's filter form; characters differ per fandom. */
    fun parseFilterOptions(html: String): FfnFilterOptions {
        val doc = Jsoup.parse(html, FfnUrls.BASE)
        fun options(name: String) = doc.selectFirst("select[name=$name]")?.select("option").orEmpty()
            .map { FfnOption(it.attr("value"), it.text().trim()) }
            .filter { it.value.isNotBlank() && it.value != "0" && !it.label.contains(":") }
        val genres = options("genreid1").ifEmpty { FfnGenres.all }
        return FfnFilterOptions(
            characters = options("characterid1"),
            languages = options("languageid"),
            genres = genres,
        )
    }

    /** Followed (/alert/story.php) or favorited (/favorites/story.php) stories of the signed-in user. */
    fun parseAccountList(html: String, requestedPage: Int): WorkPage {
        val doc = Jsoup.parse(html, FfnUrls.BASE)
        if (isLoginPage(doc)) throw FfnException("You're signed out of FanFiction.net. Sign in again in Settings.")
        checkForErrors(doc)
        // These pages use either z-list blurbs or a table, depending on the view.
        val blurbs = doc.select("div.z-list").mapNotNull { parseBlurb(it, null) }
        val rows = if (blurbs.isNotEmpty()) blurbs else doc.select("tr").mapNotNull { tr ->
            val a = tr.select("a[href^=/s/]").firstOrNull { it.text().isNotBlank() } ?: return@mapNotNull null
            val id = FfnUrls.storyIdFrom(a.attr("href")) ?: return@mapNotNull null
            val author = tr.selectFirst("a[href^=/u/]")
            WorkSummary(
                id = WorkIds.ffn(id),
                title = a.text().trim(),
                authors = listOfNotNull(author?.text()?.trim()),
                authorIds = listOfNotNull(author?.attr("href")?.let { FfnUrls.userIdFrom(it) }),
            )
        }
        return WorkPage(rows.distinctBy { it.id }, requestedPage, lastPage(doc).coerceAtLeast(requestedPage))
    }

    fun isLoginPage(html: String): Boolean = isLoginPage(Jsoup.parse(html, FfnUrls.BASE))

    private fun isLoginPage(doc: Document): Boolean =
        doc.selectFirst("input[type=password]") != null && doc.select("div.z-list, #gui_table1i").isEmpty()

    /** The signed-in username shown in the site header, if any. */
    fun signedInUser(html: String): String? {
        val doc = Jsoup.parse(html, FfnUrls.BASE)
        if (isLoginPage(doc)) return null
        return doc.selectFirst("#name_login a, .menulink a[href^=/u/], #zmenu a[href^=/u/]")?.text()?.trim()?.ifBlank { null }
    }

    fun parseReviews(html: String, requestedPage: Int): ReviewPage {
        val doc = Jsoup.parse(html, FfnUrls.BASE)
        checkForErrors(doc)
        val cells = doc.select("#gui_table1i td, table#gui_table1i td").ifEmpty { doc.select("div.review") }
        val reviews = cells.mapNotNull { td ->
            val body = td.selectFirst("div")?.text()?.trim().orEmpty()
            if (body.isBlank()) return@mapNotNull null
            val meta = td.selectFirst("small")?.text()?.trim().orEmpty()
            val author = td.selectFirst("a[href^=/u/]")?.text()?.trim()
                ?: td.ownText().trim().substringBefore(" chapter").ifBlank { "Guest" }
            Review(author = author, meta = meta, text = body)
        }
        return ReviewPage(reviews, requestedPage, lastPage(doc).coerceAtLeast(requestedPage))
    }

    // ---- helpers ----

    internal data class Meta(
        val fandoms: List<String> = emptyList(),
        val rating: String = "",
        val language: String = "",
        val genres: List<String> = emptyList(),
        val characters: List<String> = emptyList(),
        val chapters: Int = 1,
        val words: Int = 0,
        val reviews: Int = 0,
        val favs: Int = 0,
        val follows: Int = 0,
        val updated: String? = null,
        val published: String? = null,
        val complete: Boolean = false,
    )

    private val genreNames = FfnGenres.all.map { it.label.lowercase() }.toSet()

    /**
     * Parses " - "-separated metadata such as
     * "Dragon Ball Z - Rated: T - English - Romance/Drama - Chapters: 5 - Words: 12,345 - ... - Goku, Vegeta - Complete".
     * [leadingFandom] means the line may start with the fandom (search results, author pages).
     */
    internal fun parseMeta(el: Element, leadingFandom: Boolean): Meta {
        val copy = el.clone()
        copy.select("span[data-xutime]").forEach { span ->
            val iso = span.attr("data-xutime").toLongOrNull()?.let {
                Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
            }
            span.replaceWith(TextNode(iso ?: span.text()))
        }
        val tokens = copy.text().split(" - ").map { it.trim() }.filter { it.isNotEmpty() }
        var m = Meta()
        val ratingIndex = tokens.indexOfFirst { it.startsWith("Rated:") }
        if (leadingFandom && ratingIndex > 0) {
            val lead = tokens.subList(0, ratingIndex).filter { it != "Crossover" }
            m = m.copy(fandoms = lead.flatMap { it.split(" & ") }.map { it.trim() }.filter { it.isNotEmpty() })
        }
        tokens.forEachIndexed { i, t ->
            when {
                t.startsWith("Rated:") -> m = m.copy(rating = t.removePrefix("Rated:").replace("Fiction", "").trim())
                t.startsWith("Chapters:") -> m = m.copy(chapters = number(t).coerceAtLeast(1))
                t.startsWith("Words:") -> m = m.copy(words = number(t))
                t.startsWith("Reviews:") -> m = m.copy(reviews = number(t))
                t.startsWith("Favs:") -> m = m.copy(favs = number(t))
                t.startsWith("Follows:") -> m = m.copy(follows = number(t))
                t.startsWith("Updated:") -> m = m.copy(updated = t.removePrefix("Updated:").trim())
                t.startsWith("Published:") -> m = m.copy(published = t.removePrefix("Published:").trim())
                t == "Complete" || t == "Status: Complete" -> m = m.copy(complete = true)
                t.contains(":") || i < ratingIndex || (leadingFandom && ratingIndex < 0 && i == 0) -> Unit
                ratingIndex >= 0 && i == ratingIndex + 1 && m.language.isEmpty() -> m = m.copy(language = t)
                isGenres(t) && m.genres.isEmpty() -> m = m.copy(genres = splitGenres(t))
                else -> m = m.copy(characters = (m.characters + splitCharacters(t)).distinct())
            }
        }
        return m
    }

    private fun isGenres(t: String) = splitGenres(t).let { g -> g.isNotEmpty() && g.all { it.lowercase() in genreNames } }

    /** "Hurt/Comfort/Romance" -> [Hurt/Comfort, Romance]. */
    private fun splitGenres(t: String): List<String> =
        t.replace("Hurt/Comfort", "Hurt\u0000Comfort").split("/").map { it.replace('\u0000', '/').trim() }.filter { it.isNotEmpty() }

    /** "[Goku, Chi-Chi] Vegeta, Bulma" -> [Goku, Chi-Chi, Vegeta, Bulma]. */
    private fun splitCharacters(t: String): List<String> =
        t.replace("[", ",").replace("]", ",").split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private fun number(t: String) = t.substringAfter(":").filter { it.isDigit() }.toIntOrNull() ?: 0

    private fun coverOf(el: Element): String? {
        val img = el.selectFirst("img.cimage") ?: return null
        val src = img.attr("data-original").ifBlank { img.attr("src") }
        if (src.isBlank() || src.contains("/static/images/")) return null
        return FfnUrls.absolute(src)
    }

    private fun pageHeading(doc: Document): String? =
        doc.selectFirst("#content_wrapper_inner center b, #content_wrapper_inner > div > b")?.text()?.trim()?.ifBlank { null }

    /** Highest page number linked from the pagination ("&p=", "&ppage=" or "/r/id/0/N/"). */
    private fun lastPage(doc: Document): Int {
        val pattern = Regex("""[?&](?:p|ppage)=(\d+)|/r/\d+/\d+/(\d+)/""")
        return doc.select("center a[href], #content_wrapper_inner a[href]").mapNotNull { a ->
            pattern.find(a.attr("href"))?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toIntOrNull()
        }.maxOrNull() ?: 1
    }

    /** Drops FanFiction.net's ads and copy-protection wrappers from a chapter body. */
    private fun cleanStoryText(el: Element): String {
        val copy = el.clone()
        copy.select("script, style, ins, iframe, .adsbygoogle, [id^=div-gpt], [class*=ad-]").remove()
        return copy.html()
    }

    private fun checkForErrors(doc: Document) {
        val warning = doc.selectFirst("span.gui_warning, div.gui_warning")?.text().orEmpty()
        when {
            warning.contains("Story Not Found", true) ->
                throw FfnException("That story doesn't exist on FanFiction.net (it may have been deleted).")
            warning.contains("Chapter not found", true) -> throw FfnException("That chapter doesn't exist.")
            doc.title().contains("Just a moment", true) -> throw FfnException("FanFiction.net is checking the connection. Try again.")
        }
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
