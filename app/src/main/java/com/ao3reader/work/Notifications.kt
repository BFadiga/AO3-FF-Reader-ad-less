package com.ao3reader.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ao3reader.MainActivity
import com.ao3reader.R
import com.ao3reader.data.local.LibraryWork

object Notifications {
    const val CHANNEL_CHAPTERS = "new_chapters"
    const val EXTRA_WORK_ID = "work_id"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_CHAPTERS, "New chapters", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "When a work you follow posts a new chapter"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showNewChapters(context: Context, work: LibraryWork, added: Int, latestTitle: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WORK_ID, work.id)
        }
        val pending = PendingIntent.getActivity(
            context, work.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (added == 1) "New chapter: $latestTitle" else "$added new chapters, latest: $latestTitle"
        val notification = NotificationCompat.Builder(context, CHANNEL_CHAPTERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(work.title)
            .setContentText(text)
            .setSubText(work.authors.joinToString())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(work.id.hashCode(), notification)
    }
}
