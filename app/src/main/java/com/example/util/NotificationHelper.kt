package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationHelper {

  const val CHANNEL_SYSTEM = "torrentclaw_system"

  fun ensureChannels(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel =
      NotificationChannel(
        CHANNEL_SYSTEM,
        "TorrentClaw Downloads",
        NotificationManager.IMPORTANCE_LOW,
      )
      .apply { description = "Status notifications for .torrent downloads and links" }
    manager.createNotificationChannel(channel)
  }
}
