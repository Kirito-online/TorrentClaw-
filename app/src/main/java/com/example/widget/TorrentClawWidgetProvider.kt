package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.TorrentClawDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TorrentClawWidgetProvider : AppWidgetProvider() {

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    updateWidgets(context, appWidgetManager, appWidgetIds)
  }

  companion object {
    fun requestUpdate(context: Context) {
      val manager = AppWidgetManager.getInstance(context)
      val component = ComponentName(context, TorrentClawWidgetProvider::class.java)
      val ids = manager.getAppWidgetIds(component)
      if (ids.isNotEmpty()) {
        updateWidgets(context, manager, ids)
      }
    }

    private fun updateWidgets(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray,
    ) {
      CoroutineScope(Dispatchers.IO).launch {
        val alerts =
          runCatching {
              TorrentClawDatabase.getInstance(context).contentAlertDao().getAlertsSnapshot()
            }
            .getOrDefault(emptyList())
        val activeAlerts = alerts.filter { it.enabled }
        val bodyText =
          if (activeAlerts.isEmpty()) {
            context.getString(R.string.widget_empty)
          } else {
            activeAlerts.take(4).joinToString("\n") { alert ->
              val badge = if (alert.newMatchesCount > 0) " (+${alert.newMatchesCount} new)" else ""
              "• ${alert.title} [${alert.preferredResolution}]$badge"
            }
          }
        val launchIntent =
          Intent(context, MainActivity::class.java).apply {
            putExtra("open_tab", "alerts")
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
              setTextViewText(R.id.widget_count, "${activeAlerts.size} active")
              setTextViewText(R.id.widget_body, bodyText)
              setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            }
          appWidgetManager.updateAppWidget(appWidgetId, views)
        }
      }
    }
  }
}
