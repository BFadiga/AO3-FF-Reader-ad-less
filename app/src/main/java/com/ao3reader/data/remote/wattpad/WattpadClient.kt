package com.ao3reader.data.remote.wattpad

import com.ao3reader.data.remote.web.HiddenBrowser
import com.ao3reader.data.remote.web.VerificationNeededException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.TimeUnit

class WattpadException(message: String) : IOException(message)

/**
 * Talks to Wattpad's JSON API with the app's shared cookies (so the in-app sign-in applies). If a bot
 * check answers instead, the request goes through the off-screen WebView like FanFiction.net's.
 */
class WattpadClient(
    private val browser: HiddenBrowser,
    cookies: CookieJar,
    private val minIntervalMs: Long = 400,
) {
    private val http = OkHttpClient.Builder()
        .cookieJar(cookies)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gate = Mutex()
    private var lastRequestAt = 0L

    suspend fun get(url: String): String = send(url) { it.get() }

    suspend fun post(url: String, form: Map<String, String> = emptyMap()): String =
        send(url) { it.post(FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()) }

    suspend fun delete(url: String): String = send(url) { it.delete() }

    private suspend fun send(url: String, method: (Request.Builder) -> Request.Builder): String {
        val direct = gate.withLock {
            val wait = lastRequestAt + minIntervalMs - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            try {
                withContext(Dispatchers.IO) { fetch(url, method) }
            } finally {
                lastRequestAt = System.currentTimeMillis()
            }
        }
        if (direct != null) return direct
        // Only reads can go through the WebView; it shows JSON as the page's text.
        val page = browser.load(url)
        if (HiddenBrowser.isChallenge(page)) throw VerificationNeededException(url)
        return Jsoup.parse(page).body().text()
    }

    private fun fetch(url: String, method: (Request.Builder) -> Request.Builder): String? {
        val request = method(
            Request.Builder()
                .url(url)
                .header("User-Agent", browser.userAgent)
                .header("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("X-Requested-With", "XMLHttpRequest"),
        ).build()
        http.newCall(request).execute().use { r ->
            val body = r.body?.string().orEmpty()
            val challenged = r.header("cf-mitigated") == "challenge" || HiddenBrowser.isChallenge(body)
            return when {
                challenged && request.method == "GET" -> null
                r.isSuccessful -> body
                r.code == 401 || r.code == 403 -> throw WattpadException("Wattpad needs you signed in for that. Sign in under Settings.")
                r.code == 404 -> throw WattpadException("That isn't on Wattpad (it may have been deleted).")
                r.code == 429 -> throw WattpadException("Wattpad is rate-limiting requests. Try again in a few minutes.")
                r.code in 500..599 -> throw WattpadException("Wattpad is having trouble right now (error ${r.code}).")
                else -> throw WattpadException("Wattpad answered ${r.code}")
            }
        }
    }
}
