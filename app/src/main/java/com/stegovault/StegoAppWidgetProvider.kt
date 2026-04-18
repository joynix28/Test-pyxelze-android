package com.stegovault

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class StegoAppWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_stego)

            // Setup intents to launch app
            val encryptIntent = Intent(context, MainActivity::class.java).apply {
                action = "ACTION_ENCRYPT"
            }
            val encryptPendingIntent = PendingIntent.getActivity(
                context, 0, encryptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_encrypt, encryptPendingIntent)

            val decryptIntent = Intent(context, MainActivity::class.java).apply {
                action = "ACTION_DECRYPT"
            }
            val decryptPendingIntent = PendingIntent.getActivity(
                context, 1, decryptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_decrypt, decryptPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
