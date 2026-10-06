package com.ao3reader.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ao3reader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** A build published on the project's GitHub releases page. */
data class AppRelease(val versionCode: Int, val name: String, val notes: String, val apkUrl: String, val sizeBytes: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    data class Ready(val release: AppRelease, val file: File, val needsPermission: Boolean = false) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Checks GitHub for a newer build of the app, downloads it and hands it to Android's installer.
 * Every push to main publishes a release tagged "build-<versionCode>" with the APK attached.
 */
class AppUpdater(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    /** Set when a check found a newer build the user hasn't dismissed yet; drives the prompt on start. */
    val prompt = MutableStateFlow<AppRelease?>(null)

    suspend fun check(quiet: Boolean = false): AppRelease? {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return null
        _state.value = UpdateState.Checking
        return try {
            val release = latest()
            if (release != null && release.versionCode > BuildConfig.VERSION_CODE) {
                _state.value = UpdateState.Available(release)
                if (quiet) prompt.value = release
                release
            } else {
                _state.value = UpdateState.UpToDate
                null
            }
        } catch (e: Exception) {
            _state.value = if (quiet) UpdateState.Idle else UpdateState.Failed(e.message ?: "Couldn't reach GitHub")
            null
        }
    }

    /** Downloads the build, then opens the installer. */
    suspend fun downloadAndInstall(release: AppRelease) {
        val current = _state.value
        if (current is UpdateState.Ready && current.release == release && current.file.exists()) {
            install(current.file)
            return
        }
        if (current is UpdateState.Downloading) return
        _state.value = UpdateState.Downloading(release, 0f)
        try {
            val file = download(release)
            _state.value = UpdateState.Ready(release, file)
            install(file)
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: "Download failed")
        }
    }

    private suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) error("GitHub answered ${response.code}")
            parseRelease(response.body!!.string())
        }
    }

    private suspend fun download(release: AppRelease): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "app-${release.versionCode}.apk")
        http.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) error("Download failed (${response.code})")
            val body = response.body!!
            val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastShown = 0f
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val p = read.toFloat() / total
                            if (p - lastShown >= 0.02f) {
                                lastShown = p
                                _state.value = UpdateState.Downloading(release, p.coerceAtMost(1f))
                            }
                        }
                    }
                }
            }
        }
        file
    }

    private fun install(file: File) {
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) {
            // Android asks once per app before it may install updates; the download stays ready meanwhile.
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            (_state.value as? UpdateState.Ready)?.let { _state.value = it.copy(needsPermission = true) }
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    companion object {
        const val REPO = "BFadiga/AO3-FF-Reader-ad-less"

        /** Reads a GitHub release; builds are tagged "build-<versionCode>". */
        fun parseRelease(json: String): AppRelease? {
            val o = JSONObject(json)
            val tag = o.optString("tag_name")
            val code = Regex("""(\d+)$""").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val assets = o.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    return AppRelease(
                        versionCode = code,
                        name = o.optString("name").ifBlank { tag },
                        notes = o.optString("body"),
                        apkUrl = a.getString("browser_download_url"),
                        sizeBytes = a.optLong("size"),
                    )
                }
            }
            return null
        }
    }
}
