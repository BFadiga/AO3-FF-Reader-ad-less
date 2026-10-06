package com.ao3reader.ui.login

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ao3reader.data.model.Site
import com.ao3reader.data.remote.Ao3Urls
import com.ao3reader.data.remote.ffn.FfnUrls
import com.ao3reader.data.remote.wattpad.WattpadUrls
import com.ao3reader.ui.Navigator
import com.ao3reader.ui.components.ScreenScaffold
import com.ao3reader.ui.components.appContainer
import kotlinx.coroutines.launch

/**
 * The site's own sign-in page in a WebView. The password goes straight from the page to the site;
 * the app only keeps the session cookie the site sets afterwards.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(site: Site, nav: Navigator) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var loading by remember { mutableStateOf(true) }
    var checking by remember { mutableStateOf(false) }
    // Only treat leaving a sign-in page as success once a sign-in page was actually shown.
    var sawLoginPage by remember { mutableStateOf(false) }
    val loginUrl = when (site) {
        Site.AO3 -> Ao3Urls.LOGIN
        Site.FFN -> FfnUrls.LOGIN
        Site.WATTPAD -> WattpadUrls.LOGIN
    }

    fun confirm() {
        if (checking) return
        checking = true
        scope.launch {
            val user = container.accounts.confirmSignIn(site)
            checking = false
            if (user != null) {
                container.appScope.launch { runCatching { container.accounts.sync() } }
                nav.back()
            } else {
                snackbar.showSnackbar("Not signed in yet. Finish signing in on the page, then tap Done.")
            }
        }
    }

    ScreenScaffold(
        title = "Sign in to ${site.label}",
        onBack = { nav.back() },
        snackbar = snackbar,
        actions = { TextButton(onClick = { confirm() }, enabled = !checking) { Text(if (checking) "Checking…" else "Done") } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading || checking) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                "You're on ${site.label}'s own sign-in page. " +
                    "Your password goes straight to the site; the app only keeps you signed in. Tap Done when you're in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        CookieManager.getInstance().setAcceptCookie(true)
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = container.browser.userAgent
                            // Desktop pages: start zoomed out to fit, pinch to zoom.
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    loading = false
                                    CookieManager.getInstance().flush()
                                    // Sites leave the login page once the sign-in worked.
                                    val onLoginPage = url == null || url.contains("login", true) || url.contains("session", true)
                                    if (onLoginPage) {
                                        sawLoginPage = true
                                    } else if (sawLoginPage) {
                                        confirm()
                                    }
                                }
                            }
                            loadUrl(loginUrl)
                        }
                    },
                )
            }
        }
    }
}
