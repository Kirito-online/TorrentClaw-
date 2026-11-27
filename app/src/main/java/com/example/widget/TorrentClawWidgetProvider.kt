package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

class TorrentClawWidgetProvider : AppWidgetProvider() {

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    val launchIntent =
      Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse("https://torrentclaw.com/")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        101,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    for (appWidgetId in appWidgetIds) {
      val views =
        RemoteViews(context.packageName, R.layout.widget_torrentclaw).apply {
          setTextViewText(R.id.widget_count, "torrentclaw.com")
          setTextViewText(R.id.widget_body, context.getString(R.string.widget_empty))
          setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        }
      appWidgetManager.updateAppWidget(appWidgetId, views)
    }
  }
}
