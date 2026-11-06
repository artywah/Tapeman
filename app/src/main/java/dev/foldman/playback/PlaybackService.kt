package dev.foldman.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media.utils.MediaConstants
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dev.foldman.MainActivity
import dev.foldman.deck.LocalTrack
import dev.foldman.plex.PlexAlbum
import dev.foldman.plex.PlexClient
import dev.foldman.plex.PlexSectionTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Background audio playback engine and Android Auto automotive media service.
 * Supports AndroidX Media3 MediaLibraryService, Content Style layout hints,
 * custom automotive transport actions (Favorites, Sonic Radio, Sonic DJ),
 * and dynamic track injection via Plex Sonic Analysis.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    companion object {
        const val CUSTOM_COMMAND_FAVORITE = "dev.foldman.command.FAVORITE"
        const val CUSTOM_COMMAND_SONIC_RADIO = "dev.foldman.command.SONIC_RADIO"
        const val CUSTOM_COMMAND_SONIC_DJ = "dev.foldman.command.SONIC_DJ"

        const val ACTION_TOGGLE_SONIC_DJ = "action_toggle_sonic_dj"

        private const val ROOT_ID = "tapeman_root"
        private const val SECTION_HOME = "section_home"
        private const val SECTION_PLAYLISTS = "section_plex_playlists"
        private const val SECTION_ALBUMS = "section_plex_albums"
        private const val SECTION_ARTISTS = "section_plex_artists"
        private const val SECTION_RECENT = "section_plex_recent"
        private const val SECTION_LOCAL = "section_local"

        @Volatile
        var instance: PlaybackService? = null
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var plexClient: PlexClient

    internal var session: MediaLibrarySession? = null
    val isPlaying: Boolean get() = session?.player?.isPlaying == true
    val hasPlayer: Boolean get() = session?.player != null

    private var isFavoriteActive: Boolean = false
    private var isSonicDjActive: Boolean = false
    private var isSonicDjFetching: Boolean = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        plexClient = PlexClient.fromPreferences(this)

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

        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
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

                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    val currentItem = player.currentMediaItem
                    val isFav = currentItem?.mediaMetadata?.extras?.getBoolean("is_favorite") ?: false
                    if (isFav != isFavoriteActive) {
                        isFavoriteActive = isFav
                        updateCustomLayout()
                    }
                    checkSonicDjTrigger(player)
                }

                if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                    if (player.playbackState == Player.STATE_ENDED) {
                        checkSonicDjTrigger(player, forceAtEnd = true)
                    }
                }
            }
        })

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val callback = object : MediaLibrarySession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): MediaSession.ConnectionResult {
                val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                    .add(SessionCommand(CUSTOM_COMMAND_FAVORITE, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_COMMAND_SONIC_RADIO, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_COMMAND_SONIC_DJ, Bundle.EMPTY))
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(commands)
                    .setCustomLayout(buildCustomLayout())
                    .build()
            }

            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?,
            ): ListenableFuture<LibraryResult<MediaItem>> {
                val rootExtras = createContentStyleExtras(isGrid = true)
                val rootItem = MediaItem.Builder()
                    .setMediaId(ROOT_ID)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("Tapeman")
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setExtras(rootExtras)
                            .build(),
                    )
                    .build()
                val rootParams = LibraryParams.Builder().setExtras(rootExtras).build()
                return Futures.immediateFuture(LibraryResult.ofItem(rootItem, rootParams))
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?,
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                serviceScope.launch {
                    try {
                        val items = resolveChildren(parentId)
                        val isGrid = when {
                            parentId == SECTION_HOME || parentId == SECTION_PLAYLISTS || parentId == SECTION_ALBUMS || parentId == SECTION_RECENT -> true
                            parentId.startsWith("album_letter_") -> true
                            parentId.startsWith("album_range_") -> true
                            parentId.startsWith("album_all_") -> true
                            parentId.startsWith("artist_letter_") -> false
                            parentId.startsWith("artist_range_") -> false
                            parentId.startsWith("artist_all_") -> false
                            parentId.startsWith("artist_") -> true
                            parentId == SECTION_ARTISTS -> false
                            else -> false
                        }
                        val responseBundle = (params?.extras?.let { Bundle(it) } ?: Bundle()).apply {
                            putAll(createContentStyleExtras(isGrid))
                        }
                        val responseParams = LibraryParams.Builder().setExtras(responseBundle).build()
                        future.set(LibraryResult.ofItemList(items, responseParams))
                    } catch (e: Exception) {
                        future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
                    }
                }
                return future
            }

            override fun onGetItem(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                mediaId: String,
            ): ListenableFuture<LibraryResult<MediaItem>> {
                val item = MediaItem.Builder().setMediaId(mediaId).build()
                return Futures.immediateFuture(LibraryResult.ofItem(item, null))
            }

            override fun onSearch(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                params: LibraryParams?,
            ): ListenableFuture<LibraryResult<Void>> {
                serviceScope.launch {
                    val artists = runCatching { plexClient.fetchArtists(searchQuery = query, limit = 15) }.getOrDefault(emptyList())
                    val albums = runCatching { plexClient.fetchItems(tab = PlexSectionTab.ALL, searchQuery = query, limit = 15) }.getOrDefault(emptyList())
                    session.notifySearchResultChanged(browser, query, artists.size + albums.size, params)
                }
                return Futures.immediateFuture(LibraryResult.ofVoid())
            }

            override fun onGetSearchResult(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?,
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                serviceScope.launch {
                    val fallbackArt = Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_favorites}")
                    val artists = runCatching { plexClient.fetchArtists(searchQuery = query, limit = 15) }.getOrDefault(emptyList())
                    val albums = runCatching { plexClient.fetchItems(tab = PlexSectionTab.ALL, searchQuery = query, limit = 15) }.getOrDefault(emptyList())
                    val list = mutableListOf<MediaItem>()

                    artists.forEach { artist ->
                        list += MediaItem.Builder()
                            .setMediaId("artist_${artist.id}")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(artist.name)
                                    .setSubtitle("Artist")
                                    .setArtworkUri(artist.thumbUrl?.let(Uri::parse) ?: fallbackArt)
                                    .setIsBrowsable(true)
                                    .setIsPlayable(false)
                                    .setExtras(createContentStyleExtras(isGrid = false))
                                    .build(),
                            )
                            .build()
                    }

                    albums.forEach { album ->
                        val artworkUri = album.coverUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: fallbackArt
                        list += MediaItem.Builder()
                            .setMediaId("album_${album.id}")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(album.title)
                                    .setSubtitle(album.artist)
                                    .setArtworkUri(artworkUri)
                                    .setIsBrowsable(true)
                                    .setIsPlayable(true)
                                    .setExtras(createContentStyleExtras(isGrid = true))
                                    .build(),
                            )
                            .build()
                    }

                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(list), params))
                }
                return future
            }

            override fun onSetMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>,
                startIndex: Int,
                startPositionMs: Long,
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                val target = mediaItems.firstOrNull()
                if (target == null) {
                    future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
                    return future
                }

                // Handle Sonic DJ first-tile toggle tap
                if (target.mediaId == ACTION_TOGGLE_SONIC_DJ) {
                    toggleSonicDj()

                    val player = session?.player
                    val currentItems = if (player != null && player.mediaItemCount > 0) {
                        (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
                    } else {
                        emptyList()
                    }
                    val currentIndex = player?.currentMediaItemIndex ?: 0
                    val currentPos = player?.currentPosition ?: 0L
                    future.set(MediaSession.MediaItemsWithStartPosition(currentItems, currentIndex, currentPos))
                    return future
                }

                // If single container clicked from car browser (album, playlist, radio, or favorites)
                val id = target.mediaId
                val isPlayableContainer = (id.startsWith("album_") && !id.startsWith("album_letter_") && !id.startsWith("album_range_") && !id.startsWith("album_all_")) ||
                    id.startsWith("playlist_") ||
                    id.startsWith("radio_") ||
                    id == PlexClient.SPECIAL_FAVORITES_ID
                if (target.localConfiguration == null && isPlayableContainer) {
                    serviceScope.launch {
                        val tracks = resolveTracksForContainer(id)
                        val playable = tracks.map(::toPlayableMediaItem)
                        future.set(
                            MediaSession.MediaItemsWithStartPosition(
                                playable,
                                startIndex.coerceIn(playable.indices),
                                startPositionMs,
                            )
                        )
                    }
                    return future
                }

                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
                )
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle,
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    CUSTOM_COMMAND_FAVORITE -> {
                        handleToggleFavorite()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    CUSTOM_COMMAND_SONIC_RADIO -> {
                        handleSonicRadio()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    CUSTOM_COMMAND_SONIC_DJ -> {
                        toggleSonicDj()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                }
                return super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        session = MediaLibrarySession.Builder(this, player, callback)
            .setSessionActivity(openApp)
            .build()

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
                customLayout: ImmutableList<CommandButton>,
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
                n.extras.putBoolean("android.requestPromotedOngoing", true)
                n.extras.putCharSequence("android.substName", "Tapeman")
                return mediaNotification
            }

            override fun handleCustomCommand(
                session: MediaSession,
                action: String,
                extras: Bundle,
            ): Boolean = defaultProvider.handleCustomCommand(session, action, extras)

            override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
                defaultProvider.getNotificationChannelInfo()
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        serviceScope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun buildCustomLayout(): ImmutableList<CommandButton> {
        val favCmd = SessionCommand(CUSTOM_COMMAND_FAVORITE, Bundle.EMPTY)
        val radioCmd = SessionCommand(CUSTOM_COMMAND_SONIC_RADIO, Bundle.EMPTY)
        val djCmd = SessionCommand(CUSTOM_COMMAND_SONIC_DJ, Bundle.EMPTY)

        val favBtn = CommandButton.Builder()
            .setSessionCommand(favCmd)
            .setIconResId(dev.foldman.R.drawable.ic_auto_favorite)
            .setDisplayName(if (isFavoriteActive) "Favorited" else "Favorite")
            .setEnabled(true)
            .build()

        val radioBtn = CommandButton.Builder()
            .setSessionCommand(radioCmd)
            .setIconResId(dev.foldman.R.drawable.ic_auto_radio)
            .setDisplayName("Sonic Radio")
            .setEnabled(true)
            .build()

        val djBtn = CommandButton.Builder()
            .setSessionCommand(djCmd)
            .setIconResId(dev.foldman.R.drawable.ic_auto_sonic_dj)
            .setDisplayName(if (isSonicDjActive) "Sonic DJ: On" else "Sonic DJ: Off")
            .setEnabled(true)
            .build()

        return ImmutableList.of(favBtn, radioBtn, djBtn)
    }

    private fun updateCustomLayout() {
        session?.setCustomLayout(buildCustomLayout())
    }

    fun toggleSonicDj(): Boolean {
        isSonicDjActive = !isSonicDjActive
        updateCustomLayout()
        session?.player?.let { checkSonicDjTrigger(it, forceAtEnd = true) }
        session?.notifyChildrenChanged(SECTION_PLAYLISTS, 0, null)
        session?.notifyChildrenChanged(SECTION_HOME, 0, null)
        return isSonicDjActive
    }

    fun getSonicDjActive(): Boolean = isSonicDjActive

    private fun handleToggleFavorite() {
        val player = session?.player ?: return
        val current = player.currentMediaItem ?: return
        val ratingKey = current.mediaMetadata.extras?.getString("plex_rating_key") ?: return
        val nextFav = !isFavoriteActive
        isFavoriteActive = nextFav
        updateCustomLayout()
        serviceScope.launch {
            plexClient.rateItem(ratingKey, if (nextFav) 10 else -1)
        }
    }

    private fun handleSonicRadio() {
        val player = session?.player ?: return
        val current = player.currentMediaItem ?: return
        val ratingKey = current.mediaMetadata.extras?.getString("plex_rating_key") ?: return
        serviceScope.launch {
            val tracks = plexClient.fetchStationTracks(ratingKey)
            if (tracks.isNotEmpty()) {
                val playable = tracks.map(::toPlayableMediaItem)
                withContext(Dispatchers.Main) {
                    player.setMediaItems(playable, 0, 0L)
                    player.prepare()
                    player.play()
                }
            }
        }
    }

    private fun checkSonicDjTrigger(player: Player, forceAtEnd: Boolean = false) {
        if (!isSonicDjActive || isSonicDjFetching) return
        val current = player.currentMediaItem ?: return
        val ratingKey = current.mediaMetadata.extras?.getString("plex_rating_key") ?: return
        val remaining = player.mediaItemCount - player.currentMediaItemIndex - 1
        if (remaining <= 1 || forceAtEnd) {
            isSonicDjFetching = true
            serviceScope.launch {
                try {
                    val similar = plexClient.fetchSonicallySimilarTracks(ratingKey, limit = 10, maxDistance = 0.22f)
                    if (similar.isNotEmpty()) {
                        val existingIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
                        val newTracks = similar.filter { it.uri.toString() !in existingIds }
                        if (newTracks.isNotEmpty()) {
                            val items = newTracks.map(::toPlayableMediaItem)
                            withContext(Dispatchers.Main) {
                                player.addMediaItems(items)
                            }
                        }
                    }
                } finally {
                    isSonicDjFetching = false
                }
            }
        }
    }

    private suspend fun resolveChildren(parentId: String): ImmutableList<MediaItem> = withContext(Dispatchers.IO) {
        when (parentId) {
            ROOT_ID -> {
                val list = mutableListOf<MediaItem>()
                // 1. Home (Grid)
                list += createContainerNode(
                    id = SECTION_HOME,
                    title = "Home",
                    subtitle = "DJ, Radios & Recommendations",
                    isGrid = true,
                )
                // 2. Playlists (Grid)
                list += createContainerNode(
                    id = SECTION_PLAYLISTS,
                    title = "Playlists",
                    subtitle = "Plex Playlists & Loved Tracks",
                    isGrid = true,
                )
                // 3. Albums (Grid)
                list += createContainerNode(
                    id = SECTION_ALBUMS,
                    title = "Albums",
                    subtitle = "Full Discography",
                    isGrid = true,
                )
                // 4. Artists (List)
                list += createContainerNode(
                    id = SECTION_ARTISTS,
                    title = "Artists",
                    subtitle = "A–Z Artist Roster",
                    isGrid = false,
                )
                // 5. Recent (Grid)
                list += createContainerNode(
                    id = SECTION_RECENT,
                    title = "Recent Additions",
                    subtitle = "Latest in Library",
                    isGrid = true,
                )
                // 6. Local Device Audio (List)
                list += createContainerNode(
                    id = SECTION_LOCAL,
                    title = "Local Audio",
                    subtitle = "Device Music Folder",
                    isGrid = false,
                )
                ImmutableList.copyOf(list)
            }
            SECTION_HOME -> {
                val home = runCatching { plexClient.fetchHomeData() }.getOrDefault(dev.foldman.plex.PlexHomeData())
                val items = mutableListOf<MediaItem>()
                val fallbackArt = Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_favorites}")

                // 1. DJ & Favorites Group
                items += MediaItem.Builder()
                    .setMediaId(ACTION_TOGGLE_SONIC_DJ)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(if (isSonicDjActive) "🎧 Sonic DJ: ON" else "🎧 Sonic DJ: OFF")
                            .setSubtitle(if (isSonicDjActive) "Continuous Mode · Tap to Turn Off" else "Continuous DJ Mode · Tap to Turn On")
                            .setArtworkUri(Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_sonic_dj}"))
                            .setIsBrowsable(false)
                            .setIsPlayable(true)
                            .setExtras(createContentStyleExtras(isGrid = true, groupTitle = "DJ & Favorites"))
                            .build(),
                    )
                    .build()

                items += MediaItem.Builder()
                    .setMediaId(PlexClient.SPECIAL_FAVORITES_ID)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("★ Loved Tracks")
                            .setSubtitle("Plex Favorites")
                            .setArtworkUri(Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_favorites}"))
                            .setIsBrowsable(true)
                            .setIsPlayable(true)
                            .setExtras(createContentStyleExtras(isGrid = true, groupTitle = "DJ & Favorites"))
                            .build(),
                    )
                    .build()

                // 2. Recommended Stations Group
                home.recentArtist?.let { artist ->
                    items += MediaItem.Builder()
                        .setMediaId("radio_${artist.id}")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle("📻 ${artist.name} Radio")
                                .setSubtitle("Artist Station · Tap to Play")
                                .setArtworkUri(artist.thumbUrl?.let(Uri::parse) ?: Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_sonic_dj}"))
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .setExtras(createContentStyleExtras(isGrid = true, groupTitle = "Recommended Stations"))
                                .build(),
                        )
                        .build()
                }

                home.similarArtists.firstOrNull()?.let { simArtist ->
                    items += MediaItem.Builder()
                        .setMediaId("radio_${simArtist.id}")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle("📻 ${simArtist.name} Radio")
                                .setSubtitle("Similar Artist Station")
                                .setArtworkUri(simArtist.thumbUrl?.let(Uri::parse) ?: Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_sonic_dj}"))
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .setExtras(createContentStyleExtras(isGrid = true, groupTitle = "Recommended Stations"))
                                .build(),
                        )
                        .build()
                }

                // 3. Jump Back In Group (Recently Played Albums)
                home.recentlyPlayed.take(4).forEach { album ->
                    val artworkUri = album.coverUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: fallbackArt
                    items += MediaItem.Builder()
                        .setMediaId("album_${album.id}")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(album.title)
                                .setSubtitle(album.artist)
                                .setArtworkUri(artworkUri)
                                .setIsBrowsable(true)
                                .setIsPlayable(true)
                                .setExtras(createContentStyleExtras(isGrid = true, groupTitle = "Jump Back In"))
                                .build(),
                        )
                        .build()
                }

                // 4. Dynamic Hub recommendations (More by..., More in Genre...)
                home.hubs.forEach { hub ->
                    hub.items.take(3).forEach { album ->
                        if (items.none { it.mediaId == "album_${album.id}" }) {
                            val artworkUri = album.coverUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: fallbackArt
                            items += MediaItem.Builder()
                                .setMediaId("album_${album.id}")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(album.title)
                                        .setSubtitle(album.artist)
                                        .setArtworkUri(artworkUri)
                                        .setIsBrowsable(true)
                                        .setIsPlayable(true)
                                        .setExtras(createContentStyleExtras(isGrid = true, groupTitle = hub.title))
                                        .build(),
                                )
                                .build()
                        }
                    }
                }

                ImmutableList.copyOf(items)
            }
            SECTION_PLAYLISTS -> {
                val playlists = runCatching { plexClient.fetchItems(PlexSectionTab.PLAYLISTS) }.getOrDefault(emptyList())
                val items = mutableListOf<MediaItem>()

                // 1. ALWAYS Tile 1: Sonic DJ / Freeze mode toggle
                items += MediaItem.Builder()
                    .setMediaId(ACTION_TOGGLE_SONIC_DJ)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(if (isSonicDjActive) "🎧 Sonic DJ: ON" else "🎧 Sonic DJ: OFF")
                            .setSubtitle(if (isSonicDjActive) "Continuous Mode · Tap to Turn Off" else "Continuous DJ Mode · Tap to Turn On")
                            .setArtworkUri(Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_sonic_dj}"))
                            .setIsBrowsable(false)
                            .setIsPlayable(true)
                            .setExtras(createContentStyleExtras(isGrid = true))
                            .build(),
                    )
                    .build()

                // 2. Playlists & Loved Tracks (Grid items)
                val favArtworkUri = Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_favorites}")
                val hasFavorites = playlists.any { it.id == PlexClient.SPECIAL_FAVORITES_ID }
                if (!hasFavorites) {
                    items += MediaItem.Builder()
                        .setMediaId(PlexClient.SPECIAL_FAVORITES_ID)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle("★ Loved Tracks")
                                .setSubtitle("Plex Favorites")
                                .setArtworkUri(favArtworkUri)
                                .setIsBrowsable(true)
                                .setIsPlayable(true)
                                .setExtras(createContentStyleExtras(isGrid = true))
                                .build(),
                        )
                        .build()
                }

                playlists.forEach { album ->
                    val artworkUri = if (album.id == PlexClient.SPECIAL_FAVORITES_ID) {
                        favArtworkUri
                    } else {
                        album.coverUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: favArtworkUri
                    }
                    items += MediaItem.Builder()
                        .setMediaId(album.id)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(album.title)
                                .setSubtitle(album.artist)
                                .setArtworkUri(artworkUri)
                                .setIsBrowsable(true)
                                .setIsPlayable(true)
                                .setExtras(createContentStyleExtras(isGrid = true))
                                .build(),
                        )
                        .build()
                }
                ImmutableList.copyOf(items)
            }
            SECTION_ALBUMS -> {
                val items = mutableListOf<MediaItem>()
                val fallbackArt = Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_favorites}")
                val alphabetArt = Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_alphabet}")

                // 1. Recently Added Albums (8 items in 4-across grid)
                val recentAlbums = runCatching { plexClient.fetchItems(PlexSectionTab.RECENT, limit = 8) }.getOrDefault(emptyList())
                recentAlbums.forEach { album ->
                    val artworkUri = album.coverUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: fallbackArt
                    items += MediaItem.Builder()
                        .setMediaId("album_${album.id}")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(album.title)
                                .setSubtitle(album.artist)
                                .setArtworkUri(artworkUri)
                                .setIsBrowsable(true)
                                .setIsPlayable(true)
                                .setExtras(createContentStyleExtras(isGrid = true, groupTitle = "Recently Added Albums"))
                                .build(),
                        )
                        .build()
                }

                // 2. Browse Albums by Letter (A–Z)
                val letterSummaries = runCatching { plexClient.fetchFirstCharacters(type = 9) }.getOrDefault(emptyList())
                val lettersToDisplay = if (letterSummaries.isNotEmpty()) {
                    letterSummaries.filter { (it.title.length == 1 && it.title[0] in 'A'..'Z') || it.title == "#" }
                } else {
                    (('A'..'Z').map { dev.foldman.plex.PlexLetterSummary(it.toString(), it.toString(), 0) } + dev.foldman.plex.PlexLetterSummary("%23", "#", 0))
                }

                lettersToDisplay.forEach { letter ->
                    val countText = if (letter.size > 0) "${letter.size} albums" else "Browse albums"
                    val displayTitle = if (letter.title == "#") "# (123 & Symbols)" else letter.title
                    val letterKey = if (letter.title == "#") "%23" else letter.key
                    items += MediaItem.Builder()
                        .setMediaId("album_letter_$letterKey")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(displayTitle)
                                .setSubtitle(countText)
                                .setArtworkUri(alphabetArt)
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .setExtras(createContentStyleExtras(isGrid = true, groupTitle = "Browse Albums by Letter (A–Z)"))
                                .build(),
                        )
                        .build()
                }
                ImmutableList.copyOf(items)
            }
            SECTION_RECENT -> {
                val albums = runCatching { plexClient.fetchItems(PlexSectionTab.RECENT, limit = 100) }.getOrDefault(emptyList())
                val fallbackArt = Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_favorites}")
                val items = albums.map { album ->
                    val artworkUri = album.coverUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: fallbackArt
                    MediaItem.Builder()
                        .setMediaId("album_${album.id}")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(album.title)
                                .setSubtitle(album.artist)
                                .setArtworkUri(artworkUri)
                                .setIsBrowsable(true)
                                .setIsPlayable(true)
                                .setExtras(createContentStyleExtras(isGrid = true))
                                .build(),
                        )
                        .build()
                }
                ImmutableList.copyOf(items)
            }
            SECTION_ARTISTS -> {
                val items = mutableListOf<MediaItem>()
                val fallbackArt = Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_favorites}")
                val alphabetArt = Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_alphabet}")

                // 1. Recent & Suggested Artists
                val home = runCatching { plexClient.fetchHomeData() }.getOrNull()
                val recentArtists = buildList {
                    home?.recentArtist?.let { add(it) }
                    home?.similarArtists?.let { addAll(it) }
                }.distinctBy { it.id }.take(6)

                recentArtists.forEach { artist ->
                    items += MediaItem.Builder()
                        .setMediaId("artist_${artist.id}")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(artist.name)
                                .setSubtitle("Recent / Recommended Artist")
                                .setArtworkUri(artist.thumbUrl?.let(Uri::parse) ?: fallbackArt)
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .setExtras(createContentStyleExtras(isGrid = false, groupTitle = "Recent & Suggested Artists"))
                                .build(),
                        )
                        .build()
                }

                // 2. Browse by Letter (A–Z)
                val letterSummaries = runCatching { plexClient.fetchFirstCharacters(type = 8) }.getOrDefault(emptyList())
                val lettersToDisplay = if (letterSummaries.isNotEmpty()) {
                    letterSummaries.filter { (it.title.length == 1 && it.title[0] in 'A'..'Z') || it.title == "#" }
                } else {
                    (('A'..'Z').map { dev.foldman.plex.PlexLetterSummary(it.toString(), it.toString(), 0) } + dev.foldman.plex.PlexLetterSummary("%23", "#", 0))
                }

                lettersToDisplay.forEach { letter ->
                    val countText = if (letter.size > 0) "${letter.size} artists" else "Browse artists"
                    val displayTitle = if (letter.title == "#") "# (123 & Symbols)" else letter.title
                    val letterKey = if (letter.title == "#") "%23" else letter.key
                    items += MediaItem.Builder()
                        .setMediaId("artist_letter_$letterKey")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(displayTitle)
                                .setSubtitle(countText)
                                .setArtworkUri(alphabetArt)
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .setExtras(createContentStyleExtras(isGrid = false, groupTitle = "Browse by Letter (A–Z)"))
                                .build(),
                        )
                        .build()
                }
                ImmutableList.copyOf(items)
            }
            SECTION_LOCAL -> {
                val prefs = getSharedPreferences("foldman", Context.MODE_PRIVATE)
                val folderUri = prefs.getString("folder", null)?.let(Uri::parse)
                if (folderUri != null) {
                    val tracks = runCatching { LocalLibrary(this@PlaybackService).scan(folderUri) }.getOrDefault(emptyList())
                    ImmutableList.copyOf(tracks.map(::toPlayableMediaItem))
                } else {
                    ImmutableList.of()
                }
            }
            else -> {
                when {
                    parentId.startsWith("artist_letter_") -> {
                        val rawKey = parentId.removePrefix("artist_letter_")
                        val letter = if (rawKey == "%23" || rawKey == "#") "#" else rawKey
                        val artists = runCatching { plexClient.fetchArtists(letter = letter, limit = 1000) }.getOrDefault(emptyList())
                        val alphabetArt = Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_alphabet}")
                        val fallbackArt = Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_favorites}")

                        if (artists.size <= 40) {
                            val items = artists.map { artist ->
                                MediaItem.Builder()
                                    .setMediaId("artist_${artist.id}")
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(artist.name)
                                            .setSubtitle("Artist")
                                            .setArtworkUri(artist.thumbUrl?.let(Uri::parse) ?: fallbackArt)
                                            .setIsBrowsable(true)
                                            .setIsPlayable(false)
                                            .setExtras(createContentStyleExtras(isGrid = false))
                                            .build(),
                                    )
                                    .build()
                            }
                            ImmutableList.copyOf(items)
                        } else {
                            val chunkSize = 35
                            val items = mutableListOf<MediaItem>()
                            for (start in artists.indices step chunkSize) {
                                val end = minOf(start + chunkSize, artists.size)
                                val chunk = artists.subList(start, end)
                                val first = chunk.first().name.take(16)
                                val last = chunk.last().name.take(16)
                                items += MediaItem.Builder()
                                    .setMediaId("artist_range_${rawKey}_${start}_${end}")
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle("$first → $last")
                                            .setSubtitle("${chunk.size} artists")
                                            .setArtworkUri(alphabetArt)
                                            .setIsBrowsable(true)
                                            .setIsPlayable(false)
                                            .setExtras(createContentStyleExtras(isGrid = false, groupTitle = "Sections (A–Z)"))
                                            .build(),
                                    )
                                    .build()
                            }
                            items += MediaItem.Builder()
                                .setMediaId("artist_all_$rawKey")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle("★ All '$letter' Artists")
                                        .setSubtitle("${artists.size} artists (continuous list)")
                                        .setArtworkUri(alphabetArt)
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setExtras(createContentStyleExtras(isGrid = false, groupTitle = "Full Roster"))
                                        .build(),
                                )
                                .build()
                            ImmutableList.copyOf(items)
                        }
                    }
                    parentId.startsWith("artist_range_") -> {
                        val parts = parentId.removePrefix("artist_range_").split("_")
                        val rawKey = parts.getOrNull(0) ?: ""
                        val start = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        val end = parts.getOrNull(2)?.toIntOrNull() ?: (start + 35)
                        val letter = if (rawKey == "%23" || rawKey == "#") "#" else rawKey
                        val count = end - start

                        val artists = runCatching { plexClient.fetchArtists(letter = letter, start = start, limit = count) }.getOrDefault(emptyList())
                        val fallbackArt = Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_favorites}")
                        val items = artists.map { artist ->
                            MediaItem.Builder()
                                .setMediaId("artist_${artist.id}")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(artist.name)
                                        .setSubtitle("Artist")
                                        .setArtworkUri(artist.thumbUrl?.let(Uri::parse) ?: fallbackArt)
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setExtras(createContentStyleExtras(isGrid = false))
                                        .build(),
                                )
                                .build()
                        }
                        ImmutableList.copyOf(items)
                    }
                    parentId.startsWith("artist_all_") -> {
                        val rawKey = parentId.removePrefix("artist_all_")
                        val letter = if (rawKey == "%23" || rawKey == "#") "#" else rawKey
                        val artists = runCatching { plexClient.fetchArtists(letter = letter, limit = 1000) }.getOrDefault(emptyList())
                        val fallbackArt = Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_favorites}")
                        val items = artists.map { artist ->
                            MediaItem.Builder()
                                .setMediaId("artist_${artist.id}")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(artist.name)
                                        .setSubtitle("Artist")
                                        .setArtworkUri(artist.thumbUrl?.let(Uri::parse) ?: fallbackArt)
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setExtras(createContentStyleExtras(isGrid = false))
                                        .build(),
                                )
                                .build()
                        }
                        ImmutableList.copyOf(items)
                    }
                    parentId.startsWith("album_letter_") -> {
                        val rawKey = parentId.removePrefix("album_letter_")
                        val letter = if (rawKey == "%23" || rawKey == "#") "#" else rawKey
                        val albums = runCatching { plexClient.fetchItems(tab = PlexSectionTab.ALL, letter = letter, limit = 1000) }.getOrDefault(emptyList())
                        val alphabetArt = Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_alphabet}")
                        val fallbackArt = Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_favorites}")

                        if (albums.size <= 40) {
                            val items = albums.map { album ->
                                val artworkUri = album.coverUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: fallbackArt
                                MediaItem.Builder()
                                    .setMediaId("album_${album.id}")
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(album.title)
                                            .setSubtitle(album.artist)
                                            .setArtworkUri(artworkUri)
                                            .setIsBrowsable(true)
                                            .setIsPlayable(true)
                                            .setExtras(createContentStyleExtras(isGrid = true))
                                            .build(),
                                    )
                                    .build()
                            }
                            ImmutableList.copyOf(items)
                        } else {
                            val chunkSize = 35
                            val items = mutableListOf<MediaItem>()
                            for (start in albums.indices step chunkSize) {
                                val end = minOf(start + chunkSize, albums.size)
                                val chunk = albums.subList(start, end)
                                val first = chunk.first().title.take(16)
                                val last = chunk.last().title.take(16)
                                items += MediaItem.Builder()
                                    .setMediaId("album_range_${rawKey}_${start}_${end}")
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle("$first → $last")
                                            .setSubtitle("${chunk.size} albums")
                                            .setArtworkUri(alphabetArt)
                                            .setIsBrowsable(true)
                                            .setIsPlayable(false)
                                            .setExtras(createContentStyleExtras(isGrid = true, groupTitle = "Sections (A–Z)"))
                                            .build(),
                                    )
                                    .build()
                            }
                            items += MediaItem.Builder()
                                .setMediaId("album_all_$rawKey")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle("★ All '$letter' Albums")
                                        .setSubtitle("${albums.size} albums (continuous list)")
                                        .setArtworkUri(alphabetArt)
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setExtras(createContentStyleExtras(isGrid = true, groupTitle = "Full Discography"))
                                        .build(),
                                )
                                .build()
                            ImmutableList.copyOf(items)
                        }
                    }
                    parentId.startsWith("album_range_") -> {
                        val parts = parentId.removePrefix("album_range_").split("_")
                        val rawKey = parts.getOrNull(0) ?: ""
                        val start = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        val end = parts.getOrNull(2)?.toIntOrNull() ?: (start + 35)
                        val letter = if (rawKey == "%23" || rawKey == "#") "#" else rawKey
                        val count = end - start

                        val albums = runCatching { plexClient.fetchItems(tab = PlexSectionTab.ALL, letter = letter, start = start, limit = count) }.getOrDefault(emptyList())
                        val fallbackArt = Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_favorites}")
                        val items = albums.map { album ->
                            val artworkUri = album.coverUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: fallbackArt
                            MediaItem.Builder()
                                .setMediaId("album_${album.id}")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(album.title)
                                        .setSubtitle(album.artist)
                                        .setArtworkUri(artworkUri)
                                        .setIsBrowsable(true)
                                        .setIsPlayable(true)
                                        .setExtras(createContentStyleExtras(isGrid = true))
                                        .build(),
                                )
                                .build()
                        }
                        ImmutableList.copyOf(items)
                    }
                    parentId.startsWith("album_all_") -> {
                        val rawKey = parentId.removePrefix("album_all_")
                        val letter = if (rawKey == "%23" || rawKey == "#") "#" else rawKey
                        val albums = runCatching { plexClient.fetchItems(tab = PlexSectionTab.ALL, letter = letter, limit = 500) }.getOrDefault(emptyList())
                        val fallbackArt = Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_favorites}")
                        val items = albums.map { album ->
                            val artworkUri = album.coverUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: fallbackArt
                            MediaItem.Builder()
                                .setMediaId("album_${album.id}")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(album.title)
                                        .setSubtitle(album.artist)
                                        .setArtworkUri(artworkUri)
                                        .setIsBrowsable(true)
                                        .setIsPlayable(true)
                                        .setExtras(createContentStyleExtras(isGrid = true))
                                        .build(),
                                )
                                .build()
                        }
                        ImmutableList.copyOf(items)
                    }
                    parentId.startsWith("artist_") && !parentId.startsWith("artist_letter_") && !parentId.startsWith("artist_range_") && !parentId.startsWith("artist_all_") -> {
                        val artistId = parentId.removePrefix("artist_")
                        val albums = runCatching { plexClient.fetchArtistAlbums(artistId) }.getOrDefault(emptyList())
                        val fallbackArt = Uri.parse("android.resource://$packageName/${dev.foldman.R.drawable.bg_tile_favorites}")
                        val items = mutableListOf<MediaItem>()

                        // 1-tap Artist Radio at the top
                        items += MediaItem.Builder()
                            .setMediaId("radio_$artistId")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle("📻 Play Artist Radio")
                                    .setSubtitle("Endless dynamic station")
                                    .setArtworkUri(fallbackArt)
                                    .setIsBrowsable(false)
                                    .setIsPlayable(true)
                                    .setExtras(createContentStyleExtras(isGrid = true))
                                    .build(),
                            )
                            .build()

                        albums.forEach { album ->
                            val artworkUri = album.coverUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: fallbackArt
                            items += MediaItem.Builder()
                                .setMediaId("album_${album.id}")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(album.title)
                                        .setSubtitle(album.artist)
                                        .setArtworkUri(artworkUri)
                                        .setIsBrowsable(true)
                                        .setIsPlayable(true)
                                        .setExtras(createContentStyleExtras(isGrid = true))
                                        .build(),
                                )
                                .build()
                        }
                        ImmutableList.copyOf(items)
                    }
                    parentId.startsWith("album_") || parentId.startsWith("playlist_") || parentId.startsWith("radio_") || parentId == PlexClient.SPECIAL_FAVORITES_ID -> {
                        val tracks = resolveTracksForContainer(parentId)
                        ImmutableList.copyOf(tracks.map(::toPlayableMediaItem))
                    }
                    else -> ImmutableList.of()
                }
            }
        }
    }

    private suspend fun resolveTracksForContainer(containerId: String): List<LocalTrack> = withContext(Dispatchers.IO) {
        when {
            containerId == PlexClient.SPECIAL_FAVORITES_ID -> {
                plexClient.fetchFavorites()
            }
            containerId.startsWith("album_") -> {
                val id = containerId.removePrefix("album_")
                plexClient.fetchTracks(PlexAlbum(id = id, title = "", artist = ""))
            }
            containerId.startsWith("playlist_") -> {
                plexClient.fetchTracks(PlexAlbum(id = containerId, title = "", artist = "", isPlaylist = true))
            }
            containerId.startsWith("radio_") -> {
                val rawId = containerId.removePrefix("radio_").removePrefix("artist_")
                plexClient.fetchStationTracks(rawId)
            }
            else -> emptyList()
        }
    }

    private fun createContentStyleExtras(isGrid: Boolean, groupTitle: String? = null): Bundle = Bundle().apply {
        putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
        val style = if (isGrid) {
            MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
        } else {
            MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
        }
        putInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, style)
        putInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, style)
        putInt(androidx.media3.session.MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, style)
        putInt(androidx.media3.session.MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, style)
        if (groupTitle != null) {
            putString(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, groupTitle)
            putString("androidx.media3.session.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE", groupTitle)
        }
    }

    private fun createContainerNode(id: String, title: String, subtitle: String, isGrid: Boolean): MediaItem {
        val extras = createContentStyleExtras(isGrid)
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setExtras(extras)
                    .build(),
            )
            .build()
    }

    private fun toPlayableMediaItem(track: LocalTrack): MediaItem {
        val extras = Bundle().apply {
            track.ratingKey?.let { putString("plex_rating_key", it) }
            putBoolean("is_favorite", track.isFavorite)
            putAll(createContentStyleExtras(isGrid = false))
        }
        return MediaItem.Builder()
            .setMediaId(track.uri.toString())
            .setUri(track.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.folder)
                    .setArtworkUri(track.coverUri)
                    .setUserRating(HeartRating(track.isFavorite))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setExtras(extras)
                    .build(),
            )
            .build()
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
