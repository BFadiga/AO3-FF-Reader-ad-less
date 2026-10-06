package com.ao3reader.data.remote.wattpad

import java.net.URLEncoder

object WattpadUrls {
    const val BASE = "https://www.wattpad.com"
    const val LOGIN = "$BASE/login"
    const val PAGE_SIZE = 20

    /** Fields asked for on every story listing, so cards have covers, stats and tags. */
    private const val STORY_FIELDS =
        "id,title,user(name,avatar),description,cover,completed,mature,numParts,readCount,voteCount," +
            "commentCount,tags,modifyDate,createDate,language(name)"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun search(query: String, offset: Int, mature: Boolean): String =
        "$BASE/v4/search/stories?query=${enc(query)}&mature=$mature&limit=$PAGE_SIZE&offset=$offset" +
            "&fields=${enc("stories($STORY_FIELDS),total")}"

    fun story(id: Long): String =
        "$BASE/api/v3/stories/$id?fields=${enc("$STORY_FIELDS,parts(id,title,createDate,modifyDate)")}"

    /** A chapter ("part") as HTML paragraphs. */
    fun partText(partId: Long): String = "$BASE/apiv2/storytext?id=$partId"

    fun comments(partId: Long): String = "$BASE/v5/comments/namespaces/parts/resources/$partId/comments?limit=50"

    fun authorStories(username: String, offset: Int): String =
        "$BASE/api/v3/users/${enc(username)}/stories/published?limit=50&offset=$offset" +
            "&fields=${enc("stories($STORY_FIELDS),total")}"

    fun library(username: String, offset: Int): String =
        "$BASE/api/v3/users/${enc(username)}/library?limit=100&offset=$offset" +
            "&fields=${enc("stories($STORY_FIELDS),total")}"

    fun libraryAdd(username: String): String = "$BASE/api/v3/users/${enc(username)}/library"
    fun libraryItem(username: String, storyId: Long): String = "$BASE/api/v3/users/${enc(username)}/library/$storyId"
    fun vote(storyId: Long, partId: Long): String = "$BASE/api/v3/stories/$storyId/parts/$partId/votes"

    const val CURRENT_USER = "$BASE/api/v3/users/me?fields=username"
    const val HOME = "$BASE/home"

    fun storyPage(id: Long): String = "$BASE/story/$id"
    fun profile(username: String): String = "$BASE/user/${enc(username)}"

    fun storyIdFrom(url: String): Long? =
        Regex("""wattpad\.com/story/(\d+)""").find(url)?.groupValues?.get(1)?.toLongOrNull()
}
