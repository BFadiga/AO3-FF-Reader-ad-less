package com.ao3reader.wattpad

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Prints which Wattpad listing addresses and sort/filter parameters exist. Skipped unless LIVE_WATTPAD=1. */
class WattpadSortProbeTest {
    private val http = OkHttpClient()

    @Test
    fun probe() {
        assumeTrue(System.getenv("LIVE_WATTPAD") == "1")
        val f = "stories(id,title,voteCount,readCount,completed,mature,numParts,modifyDate),total,nextUrl"
        val urls = listOf(
            "https://www.wattpad.com/v4/search/stories?query=dragonball&limit=5&fields=$f",
            "https://www.wattpad.com/v4/search/stories?query=dragonball&limit=5&sort=votes&fields=$f",
            "https://www.wattpad.com/v4/search/stories?query=dragonball&limit=5&updateYoungerThan=30&fields=$f",
            "https://www.wattpad.com/v4/search/stories?query=dragonball&limit=5&filter=complete&fields=$f",
            "https://www.wattpad.com/v4/search/stories?query=dragonball&limit=5&language=1&fields=$f",
            "https://www.wattpad.com/api/v3/stories?query=%23dragonball&filter=hot&limit=5&fields=$f",
            "https://www.wattpad.com/api/v3/stories?query=%23dragonball&filter=new&limit=5&fields=$f",
            "https://www.wattpad.com/api/v3/stories?query=%23dragonball&filter=popular&limit=5&fields=$f",
            "https://www.wattpad.com/api/v3/stories?query=%23dragonball&filter=top&limit=5&fields=$f",
            "https://www.wattpad.com/v5/hotlist?tags=dragonball&language=1&limit=5&fields=$f",
            "https://www.wattpad.com/v5/newlist?tags=dragonball&language=1&limit=5&fields=$f",
            "https://www.wattpad.com/v4/browse/stories?tags=dragonball&sort=votes&limit=5&fields=$f",
        )
        for (url in urls) {
            runCatching {
                http.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36").build()).execute().use { r ->
                    val body = r.body!!.string()
                    val summary = runCatching {
                        val o = JSONObject(body)
                        val s = o.optJSONArray("stories")
                        "total=${o.opt("total")} " + (0 until (s?.length() ?: 0)).joinToString(" | ") { i ->
                            val x = s!!.getJSONObject(i); "${x.optString("id")} v=${x.optInt("voteCount")} r=${x.optInt("readCount")} c=${x.optBoolean("completed")} m=${x.optString("modifyDate").take(10)}"
                        }
                    }.getOrElse { body.take(150) }
                    println("WPPROBE ${r.code} ${url.substringAfter("wattpad.com").substringBefore("&fields")} -> $summary")
                }
            }.onFailure { println("WPPROBE failed $url: ${it.message}") }
        }
    }
}
