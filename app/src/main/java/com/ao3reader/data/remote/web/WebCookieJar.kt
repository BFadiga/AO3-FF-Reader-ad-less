package com.ao3reader.data.remote.web

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Shares cookies between OkHttp and the app's WebViews, so signing in on the in-app login page
 * (a WebView) also signs in the app's own requests, and Cloudflare clearance obtained in a WebView
 * carries over.
 */
class WebCookieJar : CookieJar {
    private val manager: CookieManager by lazy { CookieManager.getInstance().apply { setAcceptCookie(true) } }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { manager.setCookie(url.toString(), it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        manager.getCookie(url.toString())
            ?.split(";")
            ?.mapNotNull { Cookie.parse(url, it.trim()) }
            .orEmpty()

    fun has(url: String, name: String): Boolean =
        manager.getCookie(url)?.split(";")?.any { it.trim().startsWith("$name=") } == true

    /** Signs out of a site by dropping every cookie it set. */
    fun clear(baseUrl: String) {
        val host = baseUrl.substringAfter("://").substringBefore("/")
        val domains = listOf(host, host.removePrefix("www."), "." + host.removePrefix("www."))
        manager.getCookie(baseUrl)?.split(";")?.forEach { raw ->
            val name = raw.substringBefore("=").trim()
            domains.forEach { d -> manager.setCookie(baseUrl, "$name=; Max-Age=0; Path=/; Domain=$d") }
            manager.setCookie(baseUrl, "$name=; Max-Age=0; Path=/")
        }
        manager.flush()
    }

    fun flush() = manager.flush()
}
