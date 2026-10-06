package com.ao3reader.wattpad

import com.ao3reader.data.model.Site
import com.ao3reader.data.model.WorkIds
import com.ao3reader.data.remote.wattpad.WattpadParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WattpadParserTest {
    private val story = """
        {"id":"123456789","title":" Saiyan Hearts ","user":{"name":"writer01"},"description":"Line one\nline two\n\nNext <b>para</b>",
         "cover":"https://img.wattpad.com/cover/123456789-256.jpg","completed":false,"mature":true,"numParts":2,
         "readCount":15000,"voteCount":900,"commentCount":120,"tags":["dragonballz","romance"],
         "modifyDate":"2026-09-01T10:00:00Z","createDate":"2025-01-01T10:00:00Z","language":{"name":"English"},
         "parts":[{"id":111,"title":"Prologue"},{"id":222,"title":""}]}
    """.trimIndent()

    @Test
    fun idsStayApartFromOtherSites() {
        val id = WorkIds.wattpad(123456789)
        assertEquals(Site.WATTPAD, WorkIds.site(id))
        assertEquals(123456789L, WorkIds.remote(id))
        assertEquals(Site.AO3, WorkIds.site(70_000_000))
        assertEquals(Site.FFN, WorkIds.site(WorkIds.ffn(14_000_000)))
    }

    @Test
    fun readsStoryAndParts() {
        val w = WattpadParser.parseStoryDetail(story)
        val s = w.summary
        assertEquals("Saiyan Hearts", s.title)
        assertEquals(listOf("writer01"), s.authors)
        assertEquals("Mature", s.rating)
        assertEquals(listOf("dragonballz", "romance"), s.freeforms)
        assertEquals(900, s.kudos)
        assertEquals(15000, s.hits)
        assertEquals("2026-09-01", s.updated)
        assertFalse(s.complete)
        assertEquals(2, w.chapters.size)
        assertEquals(222L, w.chapters[1].id)
        assertEquals("Part 2", w.chapters[1].title)
        assertTrue(s.summaryHtml.contains("&lt;b&gt;"))
        assertTrue(s.summaryHtml.contains("line one".replaceFirstChar { it.uppercase() } + "<br>line two"))
    }

    @Test
    fun readsSearchPageAndUsername() {
        val page = WattpadParser.parseStoryList("""{"stories":[$story],"total":45}""", 1, 20)
        assertEquals(1, page.works.size)
        assertEquals(3, page.totalPages)
        assertEquals("writer01", WattpadParser.usernameFromPage("""<script>window.wattpad = {"currentUser":{"id":1,"username":"writer01"}}</script>"""))
    }

    @Test
    fun keepsFormattingInChapterText() {
        val html = WattpadParser.cleanPartHtml("""<p data-p-id="a"><b>Bold</b> and <i>italic</i> <a href="/x">link</a></p><script>x()</script>""")
        assertTrue(html.contains("<b>Bold</b>"))
        assertTrue(html.contains("<i>italic</i>"))
        assertFalse(html.contains("script"))
        assertFalse(html.contains("<a"))
    }
}
