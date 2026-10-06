package com.ao3reader.data.local

import android.content.Context
import com.ao3reader.data.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Offline copies of works, stored as files so long chapters don't hit SQLite row limits:
 * files/works/{id}/chapters.json holds titles and notes; {n}.html holds each chapter body.
 */
class DownloadStore(context: Context) {
    private val root = File(context.filesDir, "works")

    private fun dir(workId: Long) = File(root, workId.toString())

    suspend fun save(workId: Long, chapters: List<Chapter>) = withContext(Dispatchers.IO) {
        val d = dir(workId).apply { mkdirs() }
        val index = JSONArray()
        chapters.forEach { c ->
            File(d, "${c.index}.html").writeText(c.contentHtml)
            index.put(
                JSONObject()
                    .put("index", c.index)
                    .put("id", c.id ?: JSONObject.NULL)
                    .put("title", c.title)
                    .put("summary", c.summaryHtml ?: JSONObject.NULL)
                    .put("notes", c.notesHtml ?: JSONObject.NULL)
                    .put("endNotes", c.endNotesHtml ?: JSONObject.NULL),
            )
        }
        File(d, "chapters.json").writeText(index.toString())
    }

    suspend fun load(workId: Long): List<Chapter>? = withContext(Dispatchers.IO) {
        val d = dir(workId)
        val indexFile = File(d, "chapters.json")
        if (!indexFile.exists()) return@withContext null
        val arr = JSONArray(indexFile.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val n = o.getInt("index")
            Chapter(
                index = n,
                id = if (o.isNull("id")) null else o.getLong("id"),
                title = o.getString("title"),
                contentHtml = File(d, "$n.html").takeIf { it.exists() }?.readText().orEmpty(),
                summaryHtml = o.optStringOrNull("summary"),
                notesHtml = o.optStringOrNull("notes"),
                endNotesHtml = o.optStringOrNull("endNotes"),
            )
        }
    }

    suspend fun delete(workId: Long) = withContext(Dispatchers.IO) { dir(workId).deleteRecursively() }

    suspend fun deleteAll() = withContext(Dispatchers.IO) { root.deleteRecursively() }

    suspend fun totalBytes(): Long = withContext(Dispatchers.IO) {
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key)
}
