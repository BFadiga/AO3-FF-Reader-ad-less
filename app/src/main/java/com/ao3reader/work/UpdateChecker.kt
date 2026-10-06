package com.ao3reader.work

import android.content.Context
import com.ao3reader.data.prefs.SettingsRepository
import com.ao3reader.data.local.LibraryWork
import com.ao3reader.data.model.Site
import com.ao3reader.data.repo.LibraryRepository
import com.ao3reader.data.repo.WorksRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.IOException

/** Checks followed works for new chapters; used by the background worker and pull-to-refresh. */
class UpdateChecker(
    private val context: Context,
    private val works: WorksRepository,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
) {
    data class Result(val checked: Int, val updatedWorks: Int, val failed: Int, val offline: Boolean)

    suspend fun checkAll(notify: Boolean): Result {
        val prefs = settings.settings.first()
        val followed = library.followed()
        var updated = 0
        var failed = 0
        for (work in followed) {
            try {
                val (count, latestTitle, date) = latest(work)
                val now = System.currentTimeMillis()
                if (count > work.chaptersPosted) {
                    val added = count - work.chaptersPosted
                    val fresh = work.copy(
                        chaptersPosted = count,
                        newChapters = work.newChapters + added,
                        updated = date ?: work.updated,
                        lastCheckedAt = now,
                    )
                    library.upsert(fresh)
                    if (prefs.autoDownloadUpdates && work.isDownloaded) runCatching { library.download(work.id) }
                    if (notify && prefs.notificationsEnabled) {
                        Notifications.showNewChapters(context, fresh, added, latestTitle)
                    }
                    updated++
                } else {
                    library.upsert(work.copy(lastCheckedAt = now))
                }
            } catch (e: IOException) {
                return Result(followed.size, updated, failed, offline = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++ // Deleted, locked, rate-limited or a site check; try again next round.
            }
        }
        return Result(followed.size, updated, failed, offline = false)
    }

    /** Chapter count, latest chapter title and update date, from whichever site the work is on. */
    private suspend fun latest(work: LibraryWork): Triple<Int, String, String?> = when (work.site) {
        Site.AO3 -> {
            val chapters = works.ao3.chapterIndex(work.id)
            Triple(chapters.size, chapters.lastOrNull()?.title.orEmpty(), chapters.lastOrNull()?.date)
        }
        Site.FFN -> {
            val w = works.ffn.fullWork(work.id, forceRefresh = true)
            Triple(w.chapters.size, w.chapters.lastOrNull()?.title.orEmpty(), w.summary.updated.ifBlank { null })
        }
    }
}
