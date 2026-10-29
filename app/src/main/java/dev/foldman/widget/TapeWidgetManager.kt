package dev.foldman.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.content.edit
import dev.foldman.MainActivity
import dev.foldman.R
import dev.foldman.playback.Artwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object TapeWidgetManager {

    const val ACTION_PLAY_PAUSE = "dev.foldman.action.PLAY_PAUSE"
    const val ACTION_PREV = "dev.foldman.action.PREV"
    const val ACTION_NEXT = "dev.foldman.action.NEXT"

    private const val PREFS_NAME = "tapeman_widget_state"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_ALBUM = "album"
    private const val KEY_IS_PLAYING = "is_playing"
    private const val KEY_SOURCE = "source"
    private const val KEY_PROGRESS = "progress"
    private const val KEY_POSITION_MS = "position_ms"
    private const val KEY_DURATION_MS = "duration_ms"
    private const val KEY_HAS_PREV = "has_prev"
    private const val KEY_HAS_NEXT = "has_next"
    private const val KEY_SHELL_ID = "shell_id"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var cachedArtwork: Bitmap? = null

    /**
     * Updates all widget instances using the latest saved or memory state.
     */
    fun updateAll(context: Context) {
        scope.launch {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val cassetteComponent = ComponentName(context, CassetteWidgetProvider::class.java)
            val deckComponent = ComponentName(context, DeckControlWidgetProvider::class.java)

            val cassetteIds = appWidgetManager.getAppWidgetIds(cassetteComponent)
            val deckIds = appWidgetManager.getAppWidgetIds(deckComponent)

            if (cassetteIds.isEmpty() && deckIds.isEmpty()) return@launch

            val data = loadWidgetState(context)
            val art = cachedArtwork ?: loadCachedArtFile(context)

            val cassetteBmp = withContext(Dispatchers.Default) {
                CassetteBitmapRenderer.render(
                    widthPx = 760,
                    heightPx = 486,
                    title = data.title,
                    artist = data.artist,
                    album = data.album,
                    artwork = art,
                    isPlaying = data.isPlaying,
                    progress = data.progress,
                    source = data.source,
                    shellId = data.shellId,
                )
            }

            // Update 2x1 Cassette widgets
            for (id in cassetteIds) {
                val views = build2x1Views(context, cassetteBmp)
                appWidgetManager.updateAppWidget(id, views)
            }

            // Update 4x2 Walkman Deck widgets
            for (id in deckIds) {
                val views = build4x2Views(context, data, cassetteBmp)
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }

    /**
     * Called by [dev.foldman.deck.DeckViewModel] when deck state changes.
     */
    fun notifyUiState(
        context: Context,
        title: String?,
        artist: String?,
        album: String?,
        isPlaying: Boolean,
        source: String,
        artwork: Bitmap?,
        hasPrevious: Boolean,
        hasNext: Boolean,
        progress: Float,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
        shellId: String = "tdk_maxg",
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prevTitle = prefs.getString(KEY_TITLE, null)
        val prevArtist = prefs.getString(KEY_ARTIST, null)
        val prevPlaying = prefs.getBoolean(KEY_IS_PLAYING, false)
        val prevPos = prefs.getLong(KEY_POSITION_MS, 0L)

        val cleanTitle = title?.trim().orEmpty()
        val cleanArtist = artist?.trim().orEmpty()

        // Debounce if nothing substantial changed (only update every 3s if just progress)
        val posDiff = kotlin.math.abs(positionMs - prevPos)
        if (cleanTitle == prevTitle && cleanArtist == prevArtist && isPlaying == prevPlaying && cachedArtwork == artwork && posDiff < 3000) {
            return
        }

        cachedArtwork = artwork
        if (artwork != null) {
            saveCachedArtFile(context, artwork)
        }

        saveWidgetState(
            context = context,
            title = cleanTitle,
            artist = cleanArtist,
            album = album?.trim().orEmpty(),
            isPlaying = isPlaying,
            source = source,
            progress = progress,
            positionMs = positionMs,
            durationMs = durationMs,
            hasPrev = hasPrevious,
            hasNext = hasNext,
            shellId = shellId,
        )

        updateAll(context)
    }

    /**
     * Called by [dev.foldman.playback.PlaybackService] when player events occur.
     */
    fun notifyPlaybackState(
        context: Context,
        title: String?,
        artist: String?,
        album: String?,
        isPlaying: Boolean,
        source: String,
        artworkUri: String?,
        artworkData: ByteArray?,
        hasPrevious: Boolean,
        hasNext: Boolean,
        progress: Float = 0f,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
    ) {
        scope.launch {
            val art = withContext(Dispatchers.IO) {
                when {
                    artworkData != null -> Artwork.decode(artworkData)
                    artworkUri != null -> Artwork.load(context, Uri.parse(artworkUri))
                    else -> null
                }
            }
            if (art != null) {
                cachedArtwork = art
                saveCachedArtFile(context, art)
            }

            saveWidgetState(
                context = context,
                title = title?.trim().orEmpty(),
                artist = artist?.trim().orEmpty(),
                album = album?.trim().orEmpty(),
                isPlaying = isPlaying,
                source = source,
                progress = progress,
                positionMs = positionMs,
                durationMs = durationMs,
                hasPrev = hasPrevious,
                hasNext = hasNext,
            )

            updateAll(context)
        }
    }

    /**
     * Immediately toggles the play state in the widget UI for zero-latency tactile feedback.
     */
    fun updatePlayState(context: Context, isPlaying: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_IS_PLAYING, isPlaying) }
        updateAll(context)
    }

    private fun build2x1Views(context: Context, cassetteBmp: Bitmap): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_cassette_2x1)
        views.setImageViewBitmap(R.id.widget_cassette_image, cassetteBmp)

        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            201,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_2x1_root, appPendingIntent)

        return views
    }

    private fun build4x2Views(context: Context, data: WidgetData, cassetteBmp: Bitmap): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_deck_4x2)

        // Cassette Graphic
        views.setImageViewBitmap(R.id.widget_deck_cassette_image, cassetteBmp)

        // Metadata Text
        val titleText = if (data.title.isNotBlank()) data.title else if (data.isPlaying) "Playing..." else "Tapeman"
        val artistText = if (data.artist.isNotBlank()) data.artist else "Walkman Stereo Deck"
        views.setTextViewText(R.id.widget_deck_title, titleText)
        views.setTextViewText(R.id.widget_deck_artist, artistText)

        // Mechanical Tape Counter
        val counterVal = (data.progress * 100).toInt().coerceIn(0, 999)
        val counterText = String.format("%03d", counterVal).map { "$it" }.joinToString(" ")
        views.setTextViewText(R.id.widget_deck_counter, counterText)

        // Track Progress Bar & Timestamps
        val progressPermille = (data.progress * 1000).toInt().coerceIn(0, 1000)
        views.setProgressBar(R.id.widget_progress_bar, 1000, progressPermille, false)
        views.setTextViewText(R.id.widget_time_current, formatDuration(data.positionMs))
        val totalText = if (data.durationMs > 0) formatDuration(data.durationMs) else "--:--"
        views.setTextViewText(R.id.widget_time_total, totalText)

        // Brand / Source Header
        val brandText = if (data.source.isNotBlank() && data.source != "TAPEMAN") {
            "SONY · ${data.source.uppercase()}"
        } else {
            "SONY · WM-RX707"
        }
        views.setTextViewText(R.id.widget_deck_brand, brandText)

        // Status LED
        val ledRes = if (data.isPlaying) R.drawable.ic_widget_led_green else R.drawable.ic_widget_led_amber
        views.setImageViewResource(R.id.widget_status_led, ledRes)

        // Play/Pause button icon
        val playIconRes = if (data.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_btn_play_pause, playIconRes)

        // Click on Cassette or Title launches app
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            401,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_cassette_container, appPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_deck_title, appPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_deck_artist, appPendingIntent)

        // Transport Button PendingIntents
        views.setOnClickPendingIntent(
            R.id.widget_btn_prev,
            createActionPendingIntent(context, ACTION_PREV, 402),
        )
        views.setOnClickPendingIntent(
            R.id.widget_btn_play_pause,
            createActionPendingIntent(context, ACTION_PLAY_PAUSE, 403),
        )
        views.setOnClickPendingIntent(
            R.id.widget_btn_next,
            createActionPendingIntent(context, ACTION_NEXT, 404),
        )

        return views
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "$min:${if (sec < 10) "0$sec" else sec.toString()}"
    }

    private fun createActionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    data class WidgetData(
        val title: String,
        val artist: String,
        val album: String,
        val isPlaying: Boolean,
        val source: String,
        val progress: Float,
        val positionMs: Long,
        val durationMs: Long,
        val hasPrev: Boolean,
        val hasNext: Boolean,
        val shellId: String = "tdk_maxg",
    )

    private fun loadWidgetState(context: Context): WidgetData {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WidgetData(
            title = prefs.getString(KEY_TITLE, "").orEmpty(),
            artist = prefs.getString(KEY_ARTIST, "").orEmpty(),
            album = prefs.getString(KEY_ALBUM, "").orEmpty(),
            isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false),
            source = prefs.getString(KEY_SOURCE, "TAPEMAN").orEmpty(),
            progress = prefs.getFloat(KEY_PROGRESS, 0.35f),
            positionMs = prefs.getLong(KEY_POSITION_MS, 0L),
            durationMs = prefs.getLong(KEY_DURATION_MS, 0L),
            hasPrev = prefs.getBoolean(KEY_HAS_PREV, true),
            hasNext = prefs.getBoolean(KEY_HAS_NEXT, true),
            shellId = prefs.getString(KEY_SHELL_ID, "tdk_maxg") ?: "tdk_maxg",
        )
    }

    private fun saveWidgetState(
        context: Context,
        title: String,
        artist: String,
        album: String,
        isPlaying: Boolean,
        source: String,
        progress: Float,
        positionMs: Long,
        durationMs: Long,
        hasPrev: Boolean,
        hasNext: Boolean,
        shellId: String? = null,
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_TITLE, title)
            putString(KEY_ARTIST, artist)
            putString(KEY_ALBUM, album)
            putBoolean(KEY_IS_PLAYING, isPlaying)
            putString(KEY_SOURCE, source)
            putFloat(KEY_PROGRESS, progress)
            putLong(KEY_POSITION_MS, positionMs)
            putLong(KEY_DURATION_MS, durationMs)
            putBoolean(KEY_HAS_PREV, hasPrev)
            putBoolean(KEY_HAS_NEXT, hasNext)
            if (shellId != null) {
                putString(KEY_SHELL_ID, shellId)
            }
        }
    }

    private fun saveCachedArtFile(context: Context, bitmap: Bitmap) {
        runCatching {
            val file = File(context.cacheDir, "widget_artwork.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
        }
    }

    private fun loadCachedArtFile(context: Context): Bitmap? {
        return runCatching {
            val file = File(context.cacheDir, "widget_artwork.png")
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }.getOrNull()
    }
}
