package com.ao3reader.data.repo

import com.ao3reader.data.model.Site
import com.ao3reader.data.model.WorkSummary
import com.ao3reader.data.model.site
import com.ao3reader.data.prefs.SettingsRepository
import com.ao3reader.data.remote.web.WebCookieJar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Site accounts. Signing in happens on the site's own login page inside the app (a WebView), so the
 * password never passes through the app's code; the session cookie is then shared with the app's
 * requests. Once signed in, follows and likes are imported from the site and pushed back to it.
 */
class AccountRepository(
    private val settings: SettingsRepository,
    private val works: WorksRepository,
    private val library: LibraryRepository,
    private val cookies: WebCookieJar,
) {
    data class SyncResult(val imported: Int, val details: Int, val errors: List<String>)

    private val syncLock = Mutex()

    /** Called when the login page navigates away; records the account if the sign-in worked. */
    suspend fun confirmSignIn(site: Site): String? {
        cookies.flush()
        val user = runCatching {
            when (site) {
                Site.AO3 -> works.ao3.signedInUser()
                Site.FFN -> works.ffn.signedInUser()
                Site.WATTPAD -> works.wattpad.signedInUser()
            }
        }.getOrNull() ?: return null
        settings.update { it.withUser(site, user) }
        return user
    }

    suspend fun signOut(site: Site) {
        cookies.clear(site.baseUrl)
        settings.update { it.withUser(site, null) }
    }

    /**
     * Imports follows and likes from every signed-in account, then fetches details for works that were
     * imported with only a title (at most [detailLimit] per run, since each is a page request).
     */
    suspend fun sync(detailLimit: Int = 60): SyncResult = syncLock.withLock {
        val s = settings.settings.first()
        var imported = 0
        val errors = mutableListOf<String>()
        s.ao3User?.takeIf { it.isNotBlank() }?.let { user ->
            runCatching { imported += library.import(works.ao3.subscriptions(user), follow = true, like = false) }
                .onFailure { errors += "AO3: ${it.message}" }
        }
        if (s.ffnUser != null) {
            runCatching { imported += library.import(works.ffn.followedStories(), follow = true, like = false) }
                .onFailure { errors += "FanFiction.net follows: ${it.message}" }
            runCatching { imported += library.import(works.ffn.favoriteStories(), follow = false, like = true) }
                .onFailure { errors += "FanFiction.net favorites: ${it.message}" }
        }
        s.wattpadUser?.let { user ->
            if (user.isBlank()) errors += "Wattpad: couldn't read your username, so your library wasn't imported."
            else runCatching { imported += library.import(works.wattpad.library(user), follow = true, like = false) }
                .onFailure { errors += "Wattpad: ${it.message}" }
        }
        var details = 0
        for (entry in library.needingMetadata().take(detailLimit)) {
            val fresh = runCatching {
                when (entry.site) {
                    Site.AO3 -> works.ao3.workMeta(entry.id)
                    Site.FFN -> works.ffn.fullWork(entry.id).summary
                    Site.WATTPAD -> works.wattpad.fullWork(entry.id).summary
                }
            }.getOrNull() ?: continue
            library.get(entry.id)?.let { library.upsert(it.withMetadata(fresh)) }
            details++
        }
        settings.update { it.copy(lastSyncAt = System.currentTimeMillis()) }
        SyncResult(imported, details, errors)
    }

    /**
     * Mirrors a follow/unfollow made in the app onto the site account. Returns a note for the user
     * when it couldn't be mirrored, or null.
     */
    suspend fun pushFollow(work: WorkSummary, follow: Boolean): String? {
        val s = settings.settings.first()
        if (!s.syncToSites) return null
        return when (work.site) {
            Site.AO3 -> {
                if (s.ao3User == null) return null
                runCatching { works.ao3.setSubscribed(work.id, follow) }
                    .fold({ if (follow) "Also subscribed on AO3." else "Also unsubscribed on AO3." }, { "Couldn't update AO3: ${it.message}" })
            }
            Site.FFN -> {
                if (s.ffnUser == null) return null
                if (!follow) return "Unfollowed here. FanFiction.net only lets you remove follows on its own site."
                runCatching { works.ffn.addToAccount(work.id, follow = true, favorite = false) }
                    .fold({ ok -> if (ok) "Also followed on FanFiction.net." else "Couldn't follow on FanFiction.net." }, { "Couldn't follow on FanFiction.net: ${it.message}" })
            }
            Site.WATTPAD -> {
                val user = s.wattpadUser?.takeIf { it.isNotBlank() } ?: return null
                runCatching { works.wattpad.setInLibrary(user, work.id, follow) }
                    .fold({ if (follow) "Also added to your Wattpad library." else "Also removed from your Wattpad library." }, { "Couldn't update Wattpad: ${it.message}" })
            }
        }
    }

    /**
     * Leaves kudos on AO3 (no account needed) or favorites on FanFiction.net (account needed),
     * and marks the work as liked. Returns a message for the user.
     */
    suspend fun like(work: WorkSummary): String {
        val s = settings.settings.first()
        val message = when (work.site) {
            Site.AO3 -> if (works.ao3.leaveKudos(work.id)) "Kudos left. Thanks for supporting the author!"
            else "You've already left kudos on this work."
            Site.FFN -> when {
                s.ffnUser == null -> "Liked here. Sign in to FanFiction.net in Settings to favorite it there too."
                works.ffn.addToAccount(work.id, follow = false, favorite = true) -> "Favorited on FanFiction.net. Thanks for supporting the author!"
                else -> "Liked here, but FanFiction.net didn't accept the favorite."
            }
            Site.WATTPAD -> when {
                s.wattpadUser == null -> "Liked here. Sign in to Wattpad in Settings to vote for it there too."
                runCatching { works.wattpad.vote(work.id) }.isSuccess -> "Voted on Wattpad. Thanks for supporting the author!"
                else -> "Liked here, but Wattpad didn't accept the vote."
            }
        }
        library.setLiked(work, true)
        return message
    }

    /** Only the app's own flag: kudos can't be taken back, and the other sites remove favorites/votes on their own pages. */
    suspend fun unlike(work: WorkSummary) = library.setLiked(work, false)
}
