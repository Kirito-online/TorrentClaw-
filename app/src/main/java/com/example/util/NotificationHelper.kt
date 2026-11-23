package com.example.util

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
import com.example.MainActivity
import com.example.R

object NotificationHelper {

  const val CHANNEL_NEW_RELEASES = "torrentclaw_new_releases"
  const val CHANNEL_TRUESPEC = "torrentclaw_truespec"
  const val CHANNEL_SYSTEM = "torrentclaw_system"

  fun ensureChannels(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channels =
      listOf(
        NotificationChannel(
            CHANNEL_NEW_RELEASES,
            "New Release Alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
          )
          .apply { description = "Notifications when followed movies or TV shows get matching torrents" },
        NotificationChannel(
            CHANNEL_TRUESPEC,
            "TrueSpec Verification",
            NotificationManager.IMPORTANCE_LOW,
          )
          .apply { description = "Updates when queued TrueSpec file inspections complete" },
        NotificationChannel(
            CHANNEL_SYSTEM,
            "Account & Sync",
            NotificationManager.IMPORTANCE_LOW,
          )
          .apply { description = "API rate limit and service status notifications" },
      )
    manager.createNotificationChannels(channels)
  }

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun postReleaseAlert(
    context: Context,
    mediaId: String,
    title: String,
    matchSummary: String,
  ) {
    if (!hasNotificationPermission(context)) return
    ensureChannels(context)
    val intent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("open_media_id", mediaId)
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        mediaId.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val notification =
      NotificationCompat.Builder(context, CHANNEL_NEW_RELEASES)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("New Release: $title")
        .setContentText(matchSummary)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()
    runCatching { NotificationManagerCompat.from(context).notify(mediaId.hashCode(), notification) }
  }

  fun postTrueSpecUpdate(context: Context, infoHash: String, releaseName: String, statusText: String) {
    if (!hasNotificationPermission(context)) return
    ensureChannels(context)
    val notification =
      NotificationCompat.Builder(context, CHANNEL_TRUESPEC)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("TrueSpec: $statusText")
        .setContentText(releaseName)
        .setAutoCancel(true)
        .build()
    runCatching { NotificationManagerCompat.from(context).notify(infoHash.hashCode(), notification) }
  }
}
