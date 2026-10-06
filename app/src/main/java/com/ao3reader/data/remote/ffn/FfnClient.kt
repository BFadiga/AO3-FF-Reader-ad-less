package com.ao3reader.data.remote.ffn

import com.ao3reader.data.remote.web.HiddenBrowser
import com.ao3reader.data.remote.web.VerificationNeededException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Fetches FanFiction.net pages. Tries a plain request first (fast); when Cloudflare's bot check
 * answers instead, the page is loaded in an off-screen WebView, which passes the check, and later
 * requests go straight to the WebView for a while.
 */
class FfnClient(
    private val browser: HiddenBrowser,
    cookies: CookieJar,
    private val minIntervalMs: Long = 1_500,
) {
    private val http = OkHttpClient.Builder()
        .cookieJar(cookies)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val gate = Mutex()
    private var lastRequestAt = 0L

    /** Until when plain requests are skipped because Cloudflare keeps challenging them. */
    @Volatile
    private var browserUntil = 0L

    suspend fun get(url: String): String {
        if (System.currentTimeMillis() < browserUntil) return viaBrowser(url)
        val direct = gate.withLock {
            val wait = lastRequestAt + minIntervalMs - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            try {
                withContext(Dispatchers.IO) { fetchDirect(url) }
            } finally {
                lastRequestAt = System.currentTimeMillis()
            }
        }
        if (direct != null) return direct
        browserUntil = System.currentTimeMillis() + 30 * 60_000L
        return viaBrowser(url)
    }

    /** Runs [script] on [url] in the WebView (same-origin, signed-in); see [HiddenBrowser.loadAndRun]. */
    suspend fun runOnPage(url: String, script: String): String = browser.loadAndRun(url, script)

    private suspend fun viaBrowser(url: String): String {
        val html = browser.load(url)
        if (HiddenBrowser.isChallenge(html)) throw VerificationNeededException(url)
        return html
    }

    /** Returns the page, or null when Cloudflare challenged the request. */
    private fun fetchDirect(url: String): String? {
        val request = Request.Builder()
            .url(url)
            // Must match the WebView's user agent: Cloudflare ties its clearance cookie to it.
            .header("User-Agent", browser.userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        http.newCall(request).execute().use { r ->
            val body = r.body?.string().orEmpty()
            val challenged = r.header("cf-mitigated") == "challenge" ||
                (r.code == 403 || r.code == 503) && r.header("Server").orEmpty().contains("cloudflare", true) ||
                HiddenBrowser.isChallenge(body)
            return when {
                challenged -> null
                r.isSuccessful -> body
                r.code == 404 -> throw FfnException("That page doesn't exist on FanFiction.net.")
                r.code == 429 -> throw FfnException("FanFiction.net is rate-limiting requests. Try again in a few minutes.")
                r.code in 500..599 -> throw FfnException("FanFiction.net is having trouble right now (error ${r.code}).")
                else -> throw IOException("FanFiction.net answered ${r.code}")
            }
        }
    }
}
