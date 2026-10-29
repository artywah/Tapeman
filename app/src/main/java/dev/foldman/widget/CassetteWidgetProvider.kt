package dev.foldman.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * 2x1 AppWidget Provider that displays a stylized Compact Cassette with current track info & tape spools.
 */
class CassetteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        TapeWidgetManager.updateAll(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        TapeWidgetManager.updateAll(context)
    }
}
