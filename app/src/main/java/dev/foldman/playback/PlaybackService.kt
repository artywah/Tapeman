package dev.foldman.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.foldman.MainActivity

/** Plays the local folder in the background, with the system media notification and controls. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    companion object {
        @Volatile
        var instance: PlaybackService? = null
            private set
    }

    internal var session: MediaSession? = null
    val isPlaying: Boolean get() = session?.player?.isPlaying == true
    val hasPlayer: Boolean get() = session?.player != null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(DspAudioProcessor, TeeAudioProcessor(AudioLevels)))
                .build()
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                val meta = player.mediaMetadata
                val dur = player.duration.coerceAtLeast(0L)
                val pos = player.currentPosition.coerceAtLeast(0L)
                val prog = if (dur > 0L) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
                dev.foldman.widget.TapeWidgetManager.notifyPlaybackState(
                    context = this@PlaybackService,
                    title = meta.title?.toString(),
                    artist = meta.artist?.toString() ?: meta.albumArtist?.toString(),
                    album = meta.albumTitle?.toString(),
                    isPlaying = player.isPlaying,
                    source = "LOCAL",
                    artworkUri = meta.artworkUri?.toString(),
                    artworkData = meta.artworkData,
                    hasPrevious = player.hasPreviousMediaItem(),
                    hasNext = player.hasNextMediaItem(),
                    progress = prog,
                    positionMs = pos,
                    durationMs = dur,
                )
            }
        })

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(openApp).build()

        val channelId = "tapeman_playback_channel_v2"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val existing = notificationManager.getNotificationChannel(channelId)
            if (existing == null) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    getString(dev.foldman.R.string.notification_channel_now_playing),
                    android.app.NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Current playing track status and controls"
                    setShowBadge(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }
        }

        val defaultProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(channelId)
            .setChannelName(dev.foldman.R.string.notification_channel_now_playing)
            .build()
        setMediaNotificationProvider(object : MediaNotification.Provider {
            override fun createNotification(
                mediaSession: MediaSession,
                customLayout: com.google.common.collect.ImmutableList<androidx.media3.session.CommandButton>,
                actionFactory: MediaNotification.ActionFactory,
                onNotificationChangedCallback: MediaNotification.Provider.Callback,
            ): MediaNotification {
                val mediaNotification = defaultProvider.createNotification(
                    mediaSession, customLayout, actionFactory, onNotificationChangedCallback,
                )
                val n = mediaNotification.notification
                n.flags = n.flags or Notification.FLAG_ONGOING_EVENT
                n.category = Notification.CATEGORY_TRANSPORT
                n.visibility = Notification.VISIBILITY_PUBLIC
                n.icon = dev.foldman.R.drawable.tapeman_icon_monochrome
                // Android Live Updates promotion flag (Android 16+) & Samsung Now Bar integration
                n.extras.putBoolean("android.requestPromotedOngoing", true)
                n.extras.putCharSequence("android.substName", "Tapeman")
                return mediaNotification
            }

            override fun handleCustomCommand(
                session: MediaSession,
                action: String,
                extras: android.os.Bundle,
            ): Boolean = defaultProvider.handleCustomCommand(session, action, extras)

            override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
                defaultProvider.getNotificationChannelInfo()
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    fun playPause() {
        val player = session?.player ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun skipNext() {
        val player = session?.player ?: return
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    fun skipPrevious() {
        val player = session?.player ?: return
        if (player.currentPosition > 3000 || !player.hasPreviousMediaItem()) {
            player.seekTo(0)
        } else {
            player.seekToPreviousMediaItem()
        }
    }
}
