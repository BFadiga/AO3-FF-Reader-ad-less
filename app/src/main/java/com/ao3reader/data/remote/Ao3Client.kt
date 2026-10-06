package com.ao3reader.data.remote

import com.ao3reader.data.model.FormData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Fetches AO3 pages politely: one request at a time, at least [minIntervalMs] apart,
 * and backs off when AO3 answers 429 (it rate-limits aggressively).
 */
class Ao3Client(
    private val minIntervalMs: Long = 1_000,
    /** The app passes a jar shared with its WebViews so signing in on the login page signs in here too. */
    private val cookies: CookieJar = MemoryCookieJar(),
) {

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = this@Ao3Client.cookies.saveFromResponse(url, cookies)

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            // Accept AO3's Terms of Service banner up front so it isn't injected into pages.
            val tos = Cookie.Builder().domain(url.host).path("/").name("accepted_tos").value("20241119").build()
            return cookies.loadForRequest(url).filter { it.name != "accepted_tos" } + tos
        }
    }

    private val http = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val gate = Mutex()
    private var lastRequestAt = 0L

    suspend fun get(url: String): String = fetch(url)

    /**
     * Submits a form scraped from a page (kudos, subscribe). Returns the HTTP status; AO3 answers
     * these with a redirect or 201 on success and 422 when e.g. kudos were already left.
     */
    suspend fun submit(form: FormData, referer: String): Int = withContext(Dispatchers.IO) {
        val url = if (form.action.startsWith("http")) form.action else Ao3Urls.BASE + form.action
        val body = FormBody.Builder().apply { form.fields.forEach { (k, v) -> add(k, v) } }.build()
        val response = gate.withLock {
            val wait = lastRequestAt + minIntervalMs - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            try {
                noRedirects.newCall(
                    Request.Builder()
                        .url(url)
                        .post(body)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", referer)
                        .header("Accept", "text/html,application/json")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .build(),
                ).execute()
            } finally {
                lastRequestAt = System.currentTimeMillis()
            }
        }
        response.use { it.code }
    }

    private val noRedirects by lazy { http.newBuilder().followRedirects(false).build() }

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        var attempt = 0
        while (true) {
            val response = gate.withLock {
                val wait = lastRequestAt + minIntervalMs - System.currentTimeMillis()
                if (wait > 0) delay(wait)
                try {
                    http.newCall(
                        Request.Builder()
                            .url(url)
                            .header("User-Agent", USER_AGENT)
                            .header("Accept", "text/html,application/json")
                            .build(),
                    ).execute()
                } finally {
                    lastRequestAt = System.currentTimeMillis()
                }
            }
            response.use { r ->
                when {
                    r.isSuccessful -> return@withContext r.body?.string().orEmpty()
                    r.code == 429 && attempt < 3 -> {
                        val retryAfter = r.header("Retry-After")?.toLongOrNull()?.coerceIn(1, 120) ?: (10L shl attempt)
                        attempt++
                        delay(retryAfter * 1_000)
                    }
                    r.code == 429 -> throw Ao3Exception("AO3 is rate-limiting requests. Try again in a few minutes.")
                    // AO3's Cloudflare edge intermittently answers 520-529 (often 525); a retry usually works.
                    r.code in 520..529 && attempt < 3 -> {
                        attempt++
                        delay(2_000L * attempt)
                    }
                    r.code == 404 -> throw Ao3Exception("That page doesn't exist on AO3 (it may have been deleted).")
                    r.code in 500..599 -> throw Ao3Exception("AO3 is having trouble right now (error ${r.code}).")
                    else -> throw IOException("AO3 answered ${r.code}")
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        ""
    }

    /** AO3's tag autocomplete returns [{"id": "...", "name": "..."}]. */
    suspend fun autocomplete(url: String): List<String> {
        val body = get(url)
        return runCatching {
            val arr = JSONArray(body)
            (0 until arr.length()).map { arr.getJSONObject(it).optString("name") }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val USER_AGENT = "AO3Reader-Android/0.1 (personal, non-commercial reader app)"
    }
}

/** Cookies kept in memory only; used by tests and as the default. */
class MemoryCookieJar : CookieJar {
    private val store = mutableMapOf<String, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { store[it.name] = it }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        store.values.removeAll { it.expiresAt < now }
        return store.values.filter { it.matches(url) }
    }
}
