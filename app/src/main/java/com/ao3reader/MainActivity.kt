package com.ao3reader

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ao3reader.data.model.WorkIds
import com.ao3reader.data.prefs.AppSettings
import com.ao3reader.data.remote.Ao3Urls
import com.ao3reader.data.remote.ffn.FfnUrls
import com.ao3reader.data.remote.wattpad.WattpadUrls
import com.ao3reader.ui.AppNavigation
import com.ao3reader.ui.theme.Ao3Theme
import com.ao3reader.work.Notifications

class MainActivity : ComponentActivity() {
    private var openWorkId by mutableStateOf<Long?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && savedInstanceState == null) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val container = (application as Ao3App).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            Ao3Theme(settings) {
                AppNavigation(openWorkId = openWorkId, onOpenWorkHandled = { openWorkId = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Opens a work from a new-chapter notification or a shared AO3 / FanFiction.net link. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val fromNotification = intent.getLongExtra(Notifications.EXTRA_WORK_ID, -1L)
        val fromLink = intent.data?.toString()?.let { url ->
            when {
                url.contains("fanfiction.net") -> FfnUrls.storyIdFrom(url)?.let { WorkIds.ffn(it) }
                url.contains("wattpad.com") -> WattpadUrls.storyIdFrom(url)?.let { WorkIds.wattpad(it) }
                else -> Ao3Urls.workIdFrom(url)
            }
        }
        openWorkId = fromNotification.takeIf { it > 0 } ?: fromLink
    }
}
