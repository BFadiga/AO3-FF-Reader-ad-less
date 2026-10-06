package com.ao3reader.data.remote.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Thrown when a site shows a "verify you are human" check that has to be done by hand. */
class VerificationNeededException(val url: String) :
    Exception("${siteOf(url)} wants to check that you're human. Tap Verify, complete the check, then come back.")

/** Keeps the real Chrome version from the WebView's user agent but drops the phone/WebView markers. */
internal fun desktopUserAgent(webView: String): String {
    val chrome = Regex("""Chrome/([\d.]+)""").find(webView)?.groupValues?.get(1) ?: "129.0.0.0"
    return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
}

private fun siteOf(url: String) = when {
    url.contains("wattpad.com") -> "Wattpad"
    url.contains("archiveofourown.org") -> "AO3"
    else -> "FanFiction.net"
}

/**
 * Loads pages in an off-screen WebView. Used for FanFiction.net, which sits behind Cloudflare's
 * bot check: a real browser engine passes it where a plain HTTP client is often turned away.
 */
class HiddenBrowser(private val context: Context) {
    private val lock = Mutex()
    private val main = Handler(Looper.getMainLooper())

    /**
     * The phone's own browser engine, presented as desktop Chrome. FanFiction.net sends phones to its
     * mobile site, whose pages (including sign-in) the app can't use, so every request, the hidden
     * WebView and the sign-in page all use this one identity (Cloudflare ties its clearance to it).
     */
    val userAgent: String by lazy { desktopUserAgent(WebSettings.getDefaultUserAgent(context)) }

    /** Returns the page's HTML once any Cloudflare check has cleared. */
    suspend fun load(url: String, timeoutMs: Long = 35_000): String = run(url, null, timeoutMs)

    /**
     * Loads [url], then runs [script] in the page. The script must eventually call
     * `Bridge.done(text)`; that text is returned.
     */
    suspend fun loadAndRun(url: String, script: String, timeoutMs: Long = 35_000): String = run(url, script, timeoutMs)

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun run(url: String, script: String?, timeoutMs: Long): String = lock.withLock {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val web = WebView(context)
                var finished = false
                fun finish(block: (CancellableContinuation<String>) -> Unit) {
                    if (finished) return
                    finished = true
                    main.removeCallbacksAndMessages(null)
                    main.post { web.stopLoading(); web.destroy() }
                    if (cont.isActive) block(cont)
                }
                web.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    blockNetworkImage = true
                    userAgentString = userAgent
                }
                web.addJavascriptInterface(object {
                    @JavascriptInterface
                    fun done(text: String?) {
                        main.post { finish { it.resume(text.orEmpty()) } }
                    }
                }, "Bridge")

                var scriptStarted = false
                // Poll the page: Cloudflare's check page reloads itself into the real page once passed.
                val poll = object : Runnable {
                    override fun run() {
                        if (finished) return
                        web.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                            val html = decodeJsString(raw)
                            when {
                                html.isBlank() || isChallenge(html) -> main.postDelayed(this, 700)
                                script == null -> finish { it.resume(html) }
                                !scriptStarted -> {
                                    scriptStarted = true
                                    web.evaluateJavascript(script, null)
                                }
                            }
                        }
                    }
                }
                web.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, loadedUrl: String?) {
                        if (!finished) {
                            main.removeCallbacks(poll)
                            main.postDelayed(poll, 300)
                        }
                    }
                }
                main.postDelayed({ finish { it.resumeWithException(VerificationNeededException(url)) } }, timeoutMs)
                cont.invokeOnCancellation { main.post { finish { } } }
                web.loadUrl(url)
            }
        }
    }

    companion object {
        /** Cloudflare's interstitial ("Just a moment...") rather than the page we asked for. */
        fun isChallenge(html: String): Boolean {
            val head = html.take(6_000)
            return head.contains("<title>Just a moment", true) ||
                head.contains("challenge-platform") && !html.contains("id=\"storytext\"") && !html.contains("z-list") ||
                head.contains("cf-browser-verification") ||
                head.contains("<title>Attention Required", true)
        }

        /** evaluateJavascript hands back a JSON-encoded string literal. */
        fun decodeJsString(raw: String?): String {
            if (raw == null || raw == "null") return ""
            return runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault("")
        }
    }
}
