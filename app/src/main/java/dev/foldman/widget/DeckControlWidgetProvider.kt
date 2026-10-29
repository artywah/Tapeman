package dev.foldman.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * 4x2 AppWidget Provider that displays the Sony Walkman WM-RX707 deck console
 * with the cassette player and interactive transport buttons (Prev, Play/Pause, Next).
 */
class DeckControlWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        TapeWidgetManager.updateAll(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        TapeWidgetManager.updateAll(context)
    }
}
