package com.ao3reader.ffnlive

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Prints where FanFiction.net's sign-in links point today. Skipped unless LIVE_FFN=1. */
class FfnLoginLiveTest {
    private val http = OkHttpClient.Builder().followRedirects(true).build()
    private val agents = mapOf(
        "mobile" to "Mozilla/5.0 (Linux; Android 14; Pixel 8; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/129.0 Mobile Safari/537.36",
        "desktop" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Safari/537.36",
    )

    @Test
    fun loginLinks() {
        assumeTrue(System.getenv("LIVE_FFN") == "1")
        val urls = listOf(
            "https://www.fanfiction.net/", "https://m.fanfiction.net/", "https://www.fanfiction.net/login.php",
            "https://m.fanfiction.net/login.php", "https://www.fanfiction.net/account/login.php", "https://www.fanfiction.net/signin",
        )
        for ((kind, ua) in agents) for (url in urls) {
            runCatching {
                http.newCall(Request.Builder().url(url).header("User-Agent", ua).build()).execute().use { r ->
                    val body = r.body!!.string()
                    val doc = Jsoup.parse(body, r.request.url.toString())
                    val links = doc.select("a, form, button, [onclick]").mapNotNull { e ->
                        val text = e.text().trim()
                        val target = e.absUrl("href").ifBlank { e.absUrl("action") }.ifBlank { e.attr("onclick") }
                        if ((text + target).contains(Regex("log ?in|sign ?in|login", RegexOption.IGNORE_CASE))) "'${text.take(30)}' -> ${target.take(150)}" else null
                    }.distinct().take(12)
                    println("FFNPROBE $kind $url -> ${r.code} final=${r.request.url} title='${doc.title()}' cf=${r.header("cf-mitigated")} links=$links")
                }
            }.onFailure { println("FFNPROBE $kind $url failed: ${it.message}") }
        }
    }
}
