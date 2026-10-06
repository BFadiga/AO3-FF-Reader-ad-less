package com.ao3reader.data.repo

import com.ao3reader.data.model.Site
import com.ao3reader.data.model.WorkDetail
import com.ao3reader.data.model.WorkIds

/** Routes work-level requests to the site a work comes from. */
class WorksRepository(val ao3: Ao3Repository, val ffn: FfnRepository) {

    suspend fun fullWork(id: Long, forceRefresh: Boolean = false): WorkDetail = when (WorkIds.site(id)) {
        Site.AO3 -> ao3.fullWork(id, forceRefresh)
        Site.FFN -> ffn.fullWork(id, forceRefresh)
    }

    /** One chapter's text (AO3 already has every chapter from the full-work page). */
    suspend fun chapterText(id: Long, chapter: Int): String = when (WorkIds.site(id)) {
        Site.AO3 -> ao3.fullWork(id).chapters.getOrNull(chapter - 1)?.contentHtml.orEmpty()
        Site.FFN -> ffn.chapterText(id, chapter)
    }

    /** The work with every chapter's text, for downloading. */
    suspend fun completeWork(id: Long): WorkDetail {
        val work = fullWork(id, forceRefresh = true)
        if (WorkIds.site(id) == Site.AO3) return work
        val chapters = work.chapters.map { c ->
            if (c.contentHtml.isNotBlank()) c else c.copy(contentHtml = ffn.chapterText(id, c.index))
        }
        return work.copy(chapters = chapters)
    }
}
