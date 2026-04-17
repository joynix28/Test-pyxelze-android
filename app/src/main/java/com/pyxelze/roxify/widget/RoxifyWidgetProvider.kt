package com.pyxelze.roxify.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.pyxelze.roxify.MainActivity
import com.pyxelze.roxify.R

class RoxifyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Encrypt Action
        val encryptIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("WIDGET_ACTION", "ENCRYPT")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingEncrypt = PendingIntent.getActivity(
            context, 0, encryptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_encrypt, pendingEncrypt)

        // Decrypt Action
        val decryptIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("WIDGET_ACTION", "DECRYPT")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingDecrypt = PendingIntent.getActivity(
            context, 1, decryptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_decrypt, pendingDecrypt)

        // Scan Action
        val scanIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("WIDGET_ACTION", "SCAN")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingScan = PendingIntent.getActivity(
            context, 2, scanIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_scan, pendingScan)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
