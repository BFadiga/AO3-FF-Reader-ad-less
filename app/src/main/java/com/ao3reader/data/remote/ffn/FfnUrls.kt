package com.ao3reader.data.remote.ffn

import com.ao3reader.data.model.FfnFilter
import com.ao3reader.data.model.FfnStatus
import java.net.URLEncoder

object FfnUrls {
    const val BASE = "https://www.fanfiction.net"
    const val LOGIN = "$BASE/login.php"
    const val FOLLOWS = "$BASE/alert/story.php"
    const val FAVORITES = "$BASE/favorites/story.php"

    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")

    /** A chapter of a story (1-based). */
    fun chapter(storyId: Long, chapter: Int = 1) = "$BASE/s/$storyId/$chapter/"

    fun reviews(storyId: Long, page: Int) = "$BASE/r/$storyId/0/$page/"

    fun author(userId: String) = "$BASE/u/$userId/"

    fun media(path: String) = "$BASE/$path/"

    fun crossoverMedia(path: String) = "$BASE/crossovers/$path/"

    fun absolute(pathOrUrl: String): String = when {
        pathOrUrl.startsWith("http") -> pathOrUrl
        pathOrUrl.startsWith("//") -> "https:$pathOrUrl"
        else -> BASE + "/" + pathOrUrl.trimStart('/')
    }

    /** A cover at FanFiction.net's larger size (listings link the 75px thumbnail). */
    fun largeCover(url: String): String = url.replace(Regex("""/image/(\d+)/\d+/?"""), "/image/$1/180/")

    /**
     * Stories in a fandom, filtered with FanFiction.net's own filter form. Parameters mirror the
     * form's short names: srt (sort), r (rating), s (status), lan (language), g1/g2 (genres),
     * _g1 (excluded genre), c1-c4 (characters), _c1/_c2 (excluded characters), p (page).
     */
    fun fandomStories(fandomPath: String, f: FfnFilter, page: Int): String {
        val p = mutableListOf("srt" to f.sort.value, "r" to f.rating.value)
        if (f.status != FfnStatus.ANY) p += "s" to f.status.value
        if (f.languageId.isNotBlank()) p += "lan" to f.languageId
        f.includeGenres.take(2).forEachIndexed { i, g -> p += "g${i + 1}" to g.value }
        f.excludeGenres.firstOrNull()?.let { p += "_g1" to it.value }
        f.includeCharacters.take(4).forEachIndexed { i, c -> p += "c${i + 1}" to c.value }
        f.excludeCharacters.take(2).forEachIndexed { i, c -> p += "_c${i + 1}" to c.value }
        p += "p" to page.toString()
        return absolute(fandomPath).trimEnd('/') + "/?&" + p.joinToString("&") { (k, v) -> "$k=${q(v)}" }
    }

    /** Keyword search across the whole site. */
    fun search(keywords: String, page: Int) =
        "$BASE/search/?keywords=${q(keywords.trim())}&ready=1&type=story&ppage=$page"

    fun storyIdFrom(url: String): Long? = Regex("""/s/(\d+)""").find(url)?.groupValues?.get(1)?.toLongOrNull()

    fun userIdFrom(url: String): String? = Regex("""/u/(\d+)""").find(url)?.groupValues?.get(1)
}
