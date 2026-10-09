package com.ao3reader.ui.login

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ao3reader.ui.Navigator
import com.ao3reader.ui.components.ScreenScaffold
import com.ao3reader.ui.components.appContainer

/**
 * A site page shown in the app: used to pass FanFiction.net's "are you human" check by hand, and for
 * the few things only the site's own page can do (writing a review, removing a follow there).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(url: String, nav: Navigator) {
    val container = appContainer()
    var loading by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("") }

    ScreenScaffold(
        title = title.ifBlank { "FanFiction.net" },
        onBack = { nav.back() },
        actions = { TextButton(onClick = { nav.back() }) { Text("Done") } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                "If you see a \"verify you are human\" check, complete it, then tap Done.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = container.browser.userAgentFor(url)
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, loaded: String?) {
                                loading = false
                                title = view.title.orEmpty()
                                CookieManager.getInstance().flush()
                            }
                        }
                        loadUrl(url)
                    }
                },
            )
        }
    }
}
