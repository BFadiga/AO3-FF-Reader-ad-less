package com.ao3reader.wattpad

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import com.ao3reader.data.remote.wattpad.WattpadUrls

/** Prints which Wattpad listing addresses and sort/filter parameters exist. Skipped unless LIVE_WATTPAD=1. */
class WattpadSortLiveTest {
    private val http = OkHttpClient()

    @Test
    fun probe() {
        assumeTrue(System.getenv("LIVE_WATTPAD") == "1")
        val urls = listOf(
            WattpadUrls.search("#dragonball", 0, true, 50),
            WattpadUrls.search("#dragonball", 50, true, 50),
            WattpadUrls.search("#dragonball", 0, true, 50, completeOnly = true, updatedWithinDays = 365),
            WattpadUrls.hot("dragonball", 0),
            WattpadUrls.hot("dragonball", 20),
        )
        for (url in urls) {
            runCatching {
                http.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36").build()).execute().use { r ->
                    val body = r.body!!.string()
                    val summary = runCatching {
                        val o = JSONObject(body)
                        val s = o.optJSONArray("stories")
                        "total=${o.opt("total")} n=${s?.length()} " + (0 until minOf(s?.length() ?: 0, 6)).joinToString(" | ") { i ->
                            val x = s!!.getJSONObject(i); "${x.optString("id")} v=${x.optInt("voteCount")} r=${x.optInt("readCount")} c=${x.optBoolean("completed")} m=${x.optString("modifyDate").take(10)} p=${x.optInt("numParts")}"
                        }
                    }.getOrElse { body.take(150) }
                    println("WPPROBE ${r.code} ${url.substringAfter("wattpad.com").substringBefore("&fields")} -> $summary")
                }
            }.onFailure { println("WPPROBE failed $url: ${it.message}") }
        }
    }
}
