package com.ao3reader.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import kotlinx.coroutines.flow.first
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ao3reader.Ao3App
import com.ao3reader.data.prefs.AppSettings
import java.util.concurrent.TimeUnit

class ChapterCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as Ao3App).container
        // Pick up follows added on the sites themselves before checking for new chapters.
        val s = container.settings.settings.first()
        if (s.anySignedIn) runCatching { container.accounts.sync(detailLimit = 20) }
        val result = container.updateChecker.checkAll(notify = true)
        return if (result.offline) Result.retry() else Result.success()
    }

    companion object {
        private const val NAME = "chapter-check"

        fun schedule(context: Context, settings: AppSettings) {
            val wm = WorkManager.getInstance(context)
            if (!settings.notificationsEnabled) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (settings.wifiOnlyChecks) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<ChapterCheckWorker>(
                settings.checkIntervalHours.toLong().coerceAtLeast(1), TimeUnit.HOURS,
            ).setConstraints(constraints).build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
