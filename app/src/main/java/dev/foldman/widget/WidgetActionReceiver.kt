package dev.foldman.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import dev.foldman.MainActivity
import dev.foldman.playback.PlaybackService
import dev.foldman.remote.MediaListenerService

/**
 * Handles clicks on the 4x2 Walkman Deck widget's transport controls (Prev, Play/Pause, Next).
 *
 * Uses direct in-process control of [PlaybackService] when running to eliminate
 * [android.content.ReceiverCallNotAllowedException] (binding from BroadcastReceiver) and provide
 * instantaneous, zero-latency tactile response.
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // 1. Direct control of active PlaybackService (in-process, synchronous, zero-latency)
        val service = PlaybackService.instance
        val player = service?.session?.player
        if (player != null && (player.mediaItemCount > 0 || player.playbackState != androidx.media3.common.Player.STATE_IDLE)) {
            val isPlaying = player.isPlaying
            when (action) {
                TapeWidgetManager.ACTION_PLAY_PAUSE -> {
                    service.playPause()
                    TapeWidgetManager.updatePlayState(context, !isPlaying)
                }
                TapeWidgetManager.ACTION_PREV -> {
                    service.skipPrevious()
                    TapeWidgetManager.updateAll(context)
                }
                TapeWidgetManager.ACTION_NEXT -> {
                    service.skipNext()
                    TapeWidgetManager.updateAll(context)
                }
            }
            return
        }

        // 2. Control active remote media session (Spotify, YT Music, Apple Music)
        val handled = handleRemoteSession(context, action)
        if (handled) return

        // 3. Fallback: if nothing is actively playing and user taps PLAY, launch Tapeman
        if (action == TapeWidgetManager.ACTION_PLAY_PAUSE) {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(launchIntent)
        }
    }

    private fun handleRemoteSession(context: Context, action: String): Boolean {
        val mgr = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return false
        val listener = ComponentName(context, MediaListenerService::class.java)
        val sessions = runCatching { mgr.getActiveSessions(listener) }.getOrNull().orEmpty()

        val knownPackages = setOf(
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.apple.android.music",
        )
        val target = sessions.firstOrNull { it.packageName in knownPackages } ?: sessions.firstOrNull() ?: return false

        val controls = target.transportControls
        val isPlaying = target.playbackState?.state == PlaybackState.STATE_PLAYING

        when (action) {
            TapeWidgetManager.ACTION_PLAY_PAUSE -> {
                if (isPlaying) {
                    controls.pause()
                    TapeWidgetManager.updatePlayState(context, false)
                } else {
                    controls.play()
                    TapeWidgetManager.updatePlayState(context, true)
                }
            }
            TapeWidgetManager.ACTION_PREV -> controls.skipToPrevious()
            TapeWidgetManager.ACTION_NEXT -> controls.skipToNext()
        }
        return true
    }
}
