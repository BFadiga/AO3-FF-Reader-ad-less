package com.ao3reader

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.ao3reader.data.remote.ffn.FfnUrls
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import com.ao3reader.work.ChapterCheckWorker
import com.ao3reader.work.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

class Ao3App : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        scope.launch { container.filters.seedDefaultsIfEmpty() }
        // Log back in quietly: confirm saved sign-ins still work and pick up follows made on the sites.
        scope.launch {
            val s = container.settings.settings.first()
            if (s.anySignedIn && System.currentTimeMillis() - s.lastSyncAt > 6 * 3_600_000L) {
                runCatching { container.accounts.sync() }
            }
        }
        // Offer a newer build of the app if GitHub has one.
        scope.launch {
            if (container.settings.settings.first().checkAppUpdates) container.appUpdater.check(quiet = true)
        }
        // Reschedule the background check whenever its settings change.
        scope.launch {
            container.settings.settings
                .distinctUntilChangedBy { Triple(it.notificationsEnabled, it.checkIntervalHours, it.wifiOnlyChecks) }
                .collect { ChapterCheckWorker.schedule(this@Ao3App, it) }
        }
    }

    /** Covers load with the same cookies and browser identity as the app's FanFiction.net requests. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            OkHttpClient.Builder()
                .cookieJar(container.cookies)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", container.browser.userAgent)
                            .header("Referer", FfnUrls.BASE + "/")
                            .build(),
                    )
                }
                .build()
        }
        .crossfade(true)
        .build()
}
