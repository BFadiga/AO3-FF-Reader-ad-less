package com.ao3reader.data.remote

import com.ao3reader.data.model.Chapter
import com.ao3reader.data.model.ChapterRef
import com.ao3reader.data.model.FandomEntry
import com.ao3reader.data.model.FormData
import com.ao3reader.data.model.WorkActions
import com.ao3reader.data.model.WorkDetail
import com.ao3reader.data.model.WorkPage
import com.ao3reader.data.model.WorkSummary
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Ao3Exception(message: String) : Exception(message)

/** Turns AO3's public HTML pages into models. Pure JVM (no Android types) so it can be unit tested. */
object Ao3Parser {

    fun parseWorkList(html: String): WorkPage {
        val doc = Jsoup.parse(html, Ao3Urls.BASE)
        checkForErrors(doc)
        val works = doc.select("li.work.blurb, li.bookmark.blurb").mapNotNull { parseBlurb(it) }
        val pagination = doc.selectFirst("ol.pagination")
        val current = pagination?.selectFirst("li span.current, li.current")?.text()?.toIntOrNull() ?: 1
        val last = pagination?.select("li a")
            ?.mapNotNull { it.text().trim().toIntOrNull() }
            ?.maxOrNull()
            ?.coerceAtLeast(current) ?: current
        val heading = doc.selectFirst("#main h2.heading")?.text()?.trim()
        return WorkPage(works, current, last, heading)
    }

    private fun parseBlurb(blurb: Element): WorkSummary? {
        val titleLink = blurb.selectFirst("div.header h4.heading a[href^=/works/]") ?: return null
        val id = Ao3Urls.workIdFrom(titleLink.attr("href")) ?: return null
        val heading = blurb.selectFirst("div.header h4.heading")
        val authorLinks = heading?.select("a[rel=author]").orEmpty()
        val authors = authorLinks.map { it.text().trim() }
            .ifEmpty { if (heading?.text()?.contains("Anonymous") == true) listOf("Anonymous") else emptyList() }

        val required = blurb.selectFirst("ul.required-tags")
        val rating = required?.selectFirst("span.rating")?.attr("title")?.trim().orEmpty()
        val categories = required?.selectFirst("span.category")?.attr("title")
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() && it != "No category" }.orEmpty()
        val wipTitle = required?.selectFirst("span.iswip")?.attr("title").orEmpty()

        val tags = blurb.selectFirst("ul.tags")
        fun tagsOf(cls: String) = tags?.select("li.$cls a.tag")?.map { it.text().trim() }.orEmpty()

        val stats = blurb.selectFirst("dl.stats")
        val (posted, total) = parseChapters(stats?.selectFirst("dd.chapters")?.text())

        return WorkSummary(
            id = id,
            title = titleLink.text().trim(),
            authors = authors,
            authorIds = authorLinks.map { it.attr("href") },
            fandoms = blurb.select("h5.fandoms a.tag").map { it.text().trim() },
            rating = rating,
            warnings = tagsOf("warnings"),
            categories = categories,
            relationships = tagsOf("relationships"),
            characters = tagsOf("characters"),
            freeforms = tagsOf("freeforms"),
            series = blurb.select("ul.series li a[href^=/series/]").map { a ->
                val part = a.parent()?.selectFirst("strong")?.text()
                if (part != null) "Part $part of ${a.text().trim()}" else a.text().trim()
            },
            summaryHtml = blurb.selectFirst("blockquote.summary")?.html().orEmpty(),
            language = stats?.selectFirst("dd.language")?.text()?.trim().orEmpty(),
            words = parseNumber(stats?.selectFirst("dd.words")?.text()),
            chaptersPosted = posted,
            chaptersTotal = total,
            kudos = parseNumber(stats?.selectFirst("dd.kudos")?.text()),
            hits = parseNumber(stats?.selectFirst("dd.hits")?.text()),
            bookmarks = parseNumber(stats?.selectFirst("dd.bookmarks")?.text()),
            comments = parseNumber(stats?.selectFirst("dd.comments")?.text()),
            updated = normalizeDate(blurb.selectFirst("p.datetime")?.text()),
            complete = when {
                wipTitle.contains("Complete", ignoreCase = true) -> true
                wipTitle.contains("Progress", ignoreCase = true) -> false
                else -> total != null && posted >= total
            },
        )
    }

    /** Parses a full work page (requested with view_full_work=true). */
    fun parseFullWork(html: String, workId: Long): WorkDetail {
        val doc = Jsoup.parse(html, Ao3Urls.BASE)
        checkForErrors(doc)
        val meta = doc.selectFirst("dl.work.meta")
            ?: throw Ao3Exception(lockedOrUnknown(doc))
        fun metaTags(cls: String) = meta.select("dd.$cls a.tag").map { it.text().trim() }

        val stats = meta.selectFirst("dl.stats")
        val (posted, total) = parseChapters(stats?.selectFirst("dd.chapters")?.text())
        val preface = doc.selectFirst("#workskin div.preface")
        val title = preface?.selectFirst("h2.title")?.text()?.trim().orEmpty()
        val byline = preface?.selectFirst("h3.byline")
        val authorLinks = byline?.select("a[rel=author]").orEmpty()
        val authors = authorLinks.map { it.text().trim() }
            .ifEmpty { byline?.text()?.trim()?.let { listOf(it) } ?: emptyList() }
        val statusLabel = stats?.selectFirst("dt.status")?.text().orEmpty()
        val updated = stats?.selectFirst("dd.status")?.text()?.trim()
            ?: stats?.selectFirst("dd.published")?.text()?.trim().orEmpty()

        val summary = WorkSummary(
            id = workId,
            title = title,
            authors = authors,
            authorIds = authorLinks.map { it.attr("href") },
            fandoms = metaTags("fandom"),
            rating = metaTags("rating").firstOrNull().orEmpty(),
            warnings = metaTags("warning"),
            categories = metaTags("category"),
            relationships = metaTags("relationship"),
            characters = metaTags("character"),
            freeforms = metaTags("freeform"),
            series = meta.select("dd.series span.position").map { it.text().trim() },
            summaryHtml = preface?.selectFirst("div.summary blockquote.userstuff")?.html().orEmpty(),
            language = meta.selectFirst("dd.language")?.text()?.trim().orEmpty(),
            words = parseNumber(stats?.selectFirst("dd.words")?.text()),
            chaptersPosted = posted,
            chaptersTotal = total,
            kudos = parseNumber(stats?.selectFirst("dd.kudos")?.text()),
            hits = parseNumber(stats?.selectFirst("dd.hits")?.text()),
            bookmarks = parseNumber(stats?.selectFirst("dd.bookmarks")?.text()),
            comments = parseNumber(stats?.selectFirst("dd.comments")?.text()),
            updated = updated,
            complete = statusLabel.startsWith("Completed") || (total != null && posted >= total),
        )

        val chaptersRoot = doc.selectFirst("#chapters")
            ?: throw Ao3Exception("Couldn't find the work's text on the page.")
        val chapterDivs = chaptersRoot.select("> div.chapter")
        val chapters = if (chapterDivs.isEmpty()) {
            // Single-chapter works put the text straight under #chapters.
            val body = chaptersRoot.selectFirst("div.userstuff") ?: chaptersRoot
            listOf(Chapter(1, null, title.ifBlank { "Chapter 1" }, cleanUserstuff(body)))
        } else {
            chapterDivs.mapIndexed { i, div ->
                val head = div.selectFirst("div.chapter.preface h3.title")
                val link = head?.selectFirst("a")
                Chapter(
                    index = i + 1,
                    id = link?.attr("href")?.let { Ao3Urls.chapterIdFrom(it) },
                    title = head?.text()?.trim()?.ifBlank { null } ?: "Chapter ${i + 1}",
                    contentHtml = div.selectFirst("div.userstuff[role=article], div.userstuff.module")
                        ?.let { cleanUserstuff(it) }.orEmpty(),
                    summaryHtml = div.selectFirst("div.chapter.preface div.summary blockquote.userstuff")?.html(),
                    notesHtml = div.selectFirst("div.chapter.preface div.notes blockquote.userstuff")?.html(),
                    endNotesHtml = div.selectFirst("div.end.notes blockquote.userstuff")?.html(),
                )
            }
        }
        return WorkDetail(
            summary = summary,
            published = stats?.selectFirst("dd.published")?.text()?.trim(),
            notesHtml = preface?.selectFirst("div.notes blockquote.userstuff")?.html(),
            chapters = chapters,
            actions = parseActions(doc),
        )
    }

    /** The kudos and subscribe forms on a work page, and whether they've already been used. */
    fun parseActions(doc: Document): WorkActions {
        val user = signedInUser(doc)
        val kudosForm = doc.selectFirst("form#new_kudo")
        val subscribeForm = doc.select("form").firstOrNull { it.attr("action").contains("/subscriptions") }
        val subscribed = subscribeForm != null && (
            subscribeForm.selectFirst("input[name=_method][value=delete]") != null ||
                subscribeForm.selectFirst("input[type=submit]")?.attr("value")?.contains("Unsubscribe", true) == true
            )
        val kudosGiven = user != null && doc.select("#kudos a[href]").any { it.attr("href").trimEnd('/') == "/users/$user" }
        return WorkActions(
            kudosForm = kudosForm?.let { formData(it) },
            subscribeForm = subscribeForm?.let { formData(it) },
            subscribed = subscribed,
            kudosGiven = kudosGiven,
            signedInAs = user,
        )
    }

    /** The username in the page header, or null when signed out. */
    fun signedInUser(doc: Document): String? {
        val link = doc.selectFirst("#greeting a.dropdown-toggle[href^=/users/], #header ul.user a.dropdown-toggle[href^=/users/]")
            ?: return null
        return link.attr("href").removePrefix("/users/").substringBefore("/").ifBlank { null }
    }

    fun signedInUser(html: String): String? = signedInUser(Jsoup.parse(html, Ao3Urls.BASE))

    private fun formData(form: Element): FormData = FormData(
        action = form.attr("action"),
        fields = form.select("input[name]")
            .filter { it.attr("type") != "submit" }
            .map { it.attr("name") to it.attr("value") },
    )

    /** Parses /users/{name}/subscriptions?type=works: titles and authors only, plus paging. */
    fun parseSubscriptions(html: String): WorkPage {
        val doc = Jsoup.parse(html, Ao3Urls.BASE)
        checkForErrors(doc)
        val works = doc.select("dl.subscription dt, table.subscription td, #main dt").mapNotNull { dt ->
            val a = dt.selectFirst("a[href^=/works/]") ?: return@mapNotNull null
            val id = Ao3Urls.workIdFrom(a.attr("href")) ?: return@mapNotNull null
            val authorLinks = dt.select("a[rel=author], a[href^=/users/]")
            WorkSummary(
                id = id,
                title = a.text().trim(),
                authors = authorLinks.map { it.text().trim() },
                authorIds = authorLinks.map { it.attr("href") },
            )
        }.distinctBy { it.id }
        val pagination = doc.selectFirst("ol.pagination")
        val current = pagination?.selectFirst("li span.current, li.current")?.text()?.toIntOrNull() ?: 1
        val last = pagination?.select("li a")?.mapNotNull { it.text().trim().toIntOrNull() }?.maxOrNull()?.coerceAtLeast(current) ?: current
        return WorkPage(works, current, last)
    }

    /** Parses /works/{id}/navigate: the chapter list with posting dates. */
    fun parseChapterIndex(html: String): List<ChapterRef> {
        val doc = Jsoup.parse(html, Ao3Urls.BASE)
        checkForErrors(doc)
        return doc.select("ol.chapter.index li").mapIndexed { i, li ->
            val a = li.selectFirst("a")
            ChapterRef(
                index = i + 1,
                id = a?.attr("href")?.let { Ao3Urls.chapterIdFrom(it) },
                title = a?.text()?.trim().orEmpty(),
                date = li.selectFirst("span.datetime")?.text()?.trim()?.removeSurrounding("(", ")"),
            )
        }
    }

    /** Parses /media/{name}/fandoms. */
    fun parseFandomIndex(html: String): List<FandomEntry> {
        val doc = Jsoup.parse(html, Ao3Urls.BASE)
        checkForErrors(doc)
        return doc.select("ol.fandom.index li.letter ul.tags li, ul.tags.index li").mapNotNull { li ->
            val name = li.selectFirst("a.tag")?.text()?.trim() ?: return@mapNotNull null
            val count = Regex("""\(([\d,]+)\)\s*$""").find(li.text())?.groupValues?.get(1)
            FandomEntry(name, parseNumber(count))
        }.distinctBy { it.name }
    }

    /** "3/10" -> (3, 10); "3/?" -> (3, null). */
    fun parseChapters(text: String?): Pair<Int, Int?> {
        val parts = text?.split("/") ?: return 1 to null
        val posted = parseNumber(parts.getOrNull(0)).coerceAtLeast(1)
        val total = parts.getOrNull(1)?.trim()?.replace(",", "")?.toIntOrNull()
        return posted to total
    }

    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /** Listings show "05 Oct 2026" while work pages show "2026-10-05"; store everything as the latter. */
    fun normalizeDate(text: String?): String {
        val t = text?.trim().orEmpty()
        val m = Regex("""^(\d{1,2}) ([A-Za-z]{3}) (\d{4})$""").find(t) ?: return t
        val (day, mon, year) = m.destructured
        val month = months.indexOf(mon.lowercase()) + 1
        if (month == 0) return t
        return "%s-%02d-%02d".format(year, month, day.toInt())
    }

    fun parseNumber(text: String?): Int =
        text?.filter { it.isDigit() }?.toIntOrNull() ?: 0

    /** Drops the screen-reader "Chapter Text" landmark AO3 puts inside each chapter body. */
    private fun cleanUserstuff(el: Element): String {
        val copy = el.clone()
        copy.select("h3.landmark").remove()
        return copy.html()
    }

    private fun checkForErrors(doc: Document) {
        val title = doc.title()
        val bodyText = doc.body()?.text().orEmpty()
        when {
            title.contains("Retry later", true) || bodyText.startsWith("Retry later") ->
                throw Ao3Exception("AO3 is asking us to slow down. Try again in a minute.")
            title.contains("404") || doc.selectFirst("h2.heading")?.text()?.contains("Error 404") == true ->
                throw Ao3Exception("That page doesn't exist on AO3 (it may have been deleted).")
            doc.selectFirst("#main.error-502, #main.error-503") != null || title.contains("Maintenance", true) ->
                throw Ao3Exception("AO3 is down for maintenance right now.")
        }
    }

    private fun lockedOrUnknown(doc: Document): String {
        val text = doc.body()?.text().orEmpty()
        return when {
            text.contains("only available to registered users", true) ||
                doc.selectFirst("form#new_user_session_small, form#new_user") != null ->
                "This work is only visible to logged-in AO3 users."
            text.contains("adult content", true) -> "AO3 showed an adult-content warning instead of the work."
            else -> "Couldn't read this work from AO3."
        }
    }
}
