package com.ao3reader.data.repo

import com.ao3reader.data.local.AppDatabase
import com.ao3reader.data.local.DownloadStore
import com.ao3reader.data.local.LibraryWork
import com.ao3reader.data.local.UpdateEvent
import com.ao3reader.data.model.Chapter
import com.ao3reader.data.model.WorkSummary
import kotlinx.coroutines.flow.Flow

/** The user's followed and downloaded works. */
class LibraryRepository(
    private val db: AppDatabase,
    private val downloads: DownloadStore,
    private val works: WorksRepository,
) {
    private val dao = db.library()

    fun observeLibrary(): Flow<List<LibraryWork>> = dao.observeAll()
    fun observeWork(id: Long): Flow<LibraryWork?> = dao.observe(id)

    private val updateDao = db.updates()
    fun observeUpdates(): Flow<List<UpdateEvent>> = updateDao.observeAll()
    fun observeUnseenUpdates(): Flow<Int> = updateDao.observeUnseen()
    suspend fun markUpdatesSeen() = updateDao.markAllSeen()
    suspend fun clearUpdates() = updateDao.clear()

    suspend fun recordUpdate(event: UpdateEvent) {
        updateDao.insert(event)
        updateDao.deleteOlderThan(System.currentTimeMillis() - 90L * 24 * 3_600_000)
    }

    /** Pins a work to the top of the library (following it if it wasn't already). */
    suspend fun setPinned(work: WorkSummary, pinned: Boolean) {
        val existing = dao.get(work.id)
        when {
            existing != null -> dao.upsert(existing.copy(pinned = pinned, followed = existing.followed || pinned))
            pinned -> dao.upsert(LibraryWork.from(work, followed = true).copy(pinned = true))
        }
    }

    suspend fun follow(work: WorkSummary) {
        val existing = dao.get(work.id)
        dao.upsert(existing?.withMetadata(work)?.copy(followed = true) ?: LibraryWork.from(work, followed = true))
    }

    suspend fun unfollow(id: Long) {
        val existing = dao.get(id) ?: return
        if (existing.isDownloaded || existing.liked) dao.upsert(existing.copy(followed = false, newChapters = 0, pinned = false)) else dao.delete(id)
    }

    /** Kudos left / favorited. Liked works show under the library's "Liked" filter. */
    suspend fun setLiked(work: WorkSummary, liked: Boolean) {
        val existing = dao.get(work.id)
        when {
            existing != null -> {
                val updated = existing.withMetadata(work).copy(liked = liked)
                if (!updated.followed && !updated.isDownloaded && !liked && updated.lastReadChapter == 0) dao.delete(work.id)
                else dao.upsert(updated)
            }
            liked -> dao.upsert(LibraryWork.from(work, followed = false).copy(liked = true))
        }
    }

    /**
     * Adds works found on a site account (follows or favorites) without touching ones already here
     * beyond setting the flag. Returns how many were new to the library.
     */
    suspend fun import(found: List<WorkSummary>, follow: Boolean, like: Boolean): Int {
        var added = 0
        for (w in found) {
            val existing = dao.get(w.id)
            if (existing == null) {
                dao.upsert(LibraryWork.from(w, followed = follow).copy(liked = like))
                added++
            } else {
                val updated = existing.copy(followed = existing.followed || follow, liked = existing.liked || like)
                if (updated != existing) dao.upsert(updated)
            }
        }
        return added
    }

    /** Library entries that were imported with only a title and still need their details. */
    suspend fun needingMetadata(): List<LibraryWork> = dao.all().filter { it.words == 0 }

    /** Downloads every posted chapter. Returns the number of chapters saved. */
    suspend fun download(id: Long): Int {
        val work = works.completeWork(id)
        downloads.save(id, work.chapters)
        val base = dao.get(id)?.withMetadata(work.summary) ?: LibraryWork.from(work.summary, followed = false)
        dao.upsert(base.copy(downloadedChapters = work.chapters.size, downloadedAt = System.currentTimeMillis()))
        return work.chapters.size
    }

    suspend fun deleteDownload(id: Long) {
        downloads.delete(id)
        val existing = dao.get(id) ?: return
        if (existing.followed || existing.liked) dao.upsert(existing.copy(downloadedChapters = 0, downloadedAt = null)) else dao.delete(id)
    }

    suspend fun deleteAllDownloads() {
        downloads.deleteAll()
        dao.clearAllDownloads()
        dao.pruneOrphans()
    }

    suspend fun downloadsSize(): Long = downloads.totalBytes()

    /** Remembers where the reader is; also clears the "new chapters" badge. */
    suspend fun saveProgress(work: WorkSummary, chapter: Int, progress: Float) {
        if (dao.get(work.id) == null) return // Only works in the library keep a reading position.
        dao.saveProgress(work.id, chapter, progress)
    }

    /**
     * Chapters for reading: the offline copy when there is one, otherwise AO3.
     * Falls back to the offline copy if AO3 can't be reached.
     */
    suspend fun chapters(id: Long, preferOffline: Boolean = true): Pair<WorkSummary?, List<Chapter>> {
        val entry = dao.get(id)
        val offline = if (entry?.isDownloaded == true) downloads.load(id) else null
        if (preferOffline && offline != null && offline.size >= (entry?.chaptersPosted ?: 0)) {
            return entry?.toSummary() to offline
        }
        return try {
            val work = works.fullWork(id)
            if (entry != null) dao.upsert(entry.withMetadata(work.summary))
            // Chapters already saved offline fill in ones the site hasn't sent yet (FanFiction.net loads them one by one).
            val merged = work.chapters.map { c ->
                if (c.contentHtml.isNotBlank()) c else offline?.firstOrNull { it.index == c.index }?.let { c.copy(contentHtml = it.contentHtml) } ?: c
            }
            work.summary to merged
        } catch (e: Exception) {
            if (offline != null) entry?.toSummary() to offline else throw e
        }
    }

    /** One chapter's text, for chapters that weren't sent with the work (FanFiction.net). */
    suspend fun chapterText(id: Long, index: Int): String =
        works.chapterText(id, index).ifBlank { throw IllegalStateException("That chapter is empty.") }

    suspend fun get(id: Long) = dao.get(id)
    suspend fun upsert(work: LibraryWork) = dao.upsert(work)
    suspend fun followed() = dao.followed()
}
