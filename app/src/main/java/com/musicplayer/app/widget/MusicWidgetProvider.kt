package com.musicplayer.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import com.musicplayer.app.service.PlaybackService

@UnstableApi
open class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Trigger update in PlaybackService
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.UPDATE_WIDGETS
        }
        context.startService(intent)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.UPDATE_WIDGETS
        }
        context.startService(intent)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Forward actions to PlaybackService
        if (intent.action?.startsWith("com.musicplayer.app.ACTION_") == true) {
            val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                action = intent.action
            }
            context.startService(serviceIntent)
        }
    }
    
    open fun getLayoutId(): Int = com.musicplayer.app.R.layout.widget_small
}
