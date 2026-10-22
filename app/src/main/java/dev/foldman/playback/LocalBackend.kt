package dev.foldman.playback

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.foldman.deck.BackendState
import dev.foldman.deck.DeckBackend
import dev.foldman.deck.LocalTrack
import dev.foldman.deck.NowPlaying
import dev.foldman.deck.SourceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plays a list of [LocalTrack] URIs (either a local folder or a Plex server album/playlist)
 * through [PlaybackService] and a Media3 controller.
 */
class LocalBackend(
    private val context: Context,
    private val emptyStatus: SourceStatus = SourceStatus.NeedsFolder,
) : DeckBackend {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(BackendState(status = emptyStatus))
    override val state: StateFlow<BackendState> = _state.asStateFlow()

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var whenConnected = mutableListOf<(MediaController) -> Unit>()

    private var tracks: List<LocalTrack> = emptyList()
    /** Library status while there is nothing to play yet (no folder/album, scanning, empty). */
    private var libraryStatus: SourceStatus = emptyStatus

    private var artworkFor: Any? = null
    private var artwork: Bitmap? = null
    private var artworkJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            whenConnected.forEach { it(c) }
            whenConnected.clear()
            publish()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let(block) ?: run {
            whenConnected += block
            connect()
        }
    }

    fun setLibraryStatus(status: SourceStatus) {
        libraryStatus = status
        publish()
    }

    private fun isOurQueue(c: MediaController): Boolean =
        tracks.isNotEmpty() &&
            c.mediaItemCount == tracks.size &&
            c.getMediaItemAt(0).mediaId == tracks[0].uri.toString()

    /**
      * Loads [newTracks] as the tape. A queue the service is already playing (the app was reopened
      * mid-song) is kept as it is, so playback doesn't jump.
      */
    fun setTracks(newTracks: List<LocalTrack>, startIndex: Int? = null, loadIntoPlayer: Boolean = true) {
        tracks = newTracks
        libraryStatus = if (newTracks.isEmpty()) SourceStatus.EmptyFolder else SourceStatus.Ready
        if (loadIntoPlayer) {
            withController { c ->
                val sameQueue = isOurQueue(c)
                if (!sameQueue || startIndex != null) {
                    if (newTracks.isNotEmpty()) {
                        c.setMediaItems(newTracks.map(::toMediaItem), startIndex ?: 0, 0L)
                        c.prepare()
                    }
                } else {
                    // Refresh titles from tags without disturbing playback.
                    newTracks.forEachIndexed { i, t ->
                        if (i < c.mediaItemCount && i != c.currentMediaItemIndex) c.replaceMediaItem(i, toMediaItem(t))
                    }
                }
                publish()
            }
        }
        publish()
    }

    fun playTrack(index: Int) = withController { c ->
        if (index !in tracks.indices) return@withController
        if (!isOurQueue(c)) {
            c.setMediaItems(tracks.map(::toMediaItem), index, 0L)
        } else {
            c.seekTo(index, 0L)
        }
        c.prepare()
        c.play()
        publish()
    }

    private fun toMediaItem(t: LocalTrack) = MediaItem.Builder()
        .setMediaId(t.uri.toString())
        .setUri(t.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)
                .setArtworkUri(t.coverUri)
                .setAlbumTitle(t.folder)
                .build(),
        )
        .build()

    private fun publish() {
        val c = controller
        if (c == null || tracks.isEmpty()) {
            _state.value = BackendState(status = libraryStatus)
            return
        }
        if (!isOurQueue(c)) {
            val first = tracks.first()
            updateArtworkFromUri(first.coverUri)
            _state.value = BackendState(
                status = SourceStatus.Ready,
                nowPlaying = NowPlaying(
                    title = first.title,
                    artist = first.artist,
                    album = first.folder,
                    artwork = artwork,
                    artworkKey = artworkFor.takeIf { artwork != null },
                    durationMs = 0L,
                ),
                isPlaying = false,
                ended = false,
                trackIndex = 0,
                trackCount = tracks.size,
                hasNext = tracks.size > 1,
                hasPrevious = false,
            )
            return
        }
        val index = c.currentMediaItemIndex
        val track = tracks.getOrNull(index)?.takeIf { it.uri.toString() == c.currentMediaItem?.mediaId }
        val meta = c.mediaMetadata
        updateArtwork(meta, track?.coverUri)
        _state.value = BackendState(
            status = SourceStatus.Ready,
            nowPlaying = NowPlaying(
                title = meta.title?.toString() ?: track?.title ?: c.currentMediaItem?.mediaId?.substringAfterLast("%2F"),
                artist = meta.artist?.toString() ?: meta.albumArtist?.toString() ?: track?.artist,
                album = meta.albumTitle?.toString() ?: track?.folder,
                artwork = artwork,
                artworkKey = artworkFor.takeIf { artwork != null },
                durationMs = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L,
            ),
            isPlaying = c.isPlaying || (c.playWhenReady && c.playbackState == Player.STATE_BUFFERING),
            ended = c.playbackState == Player.STATE_ENDED,
            trackIndex = index,
            trackCount = c.mediaItemCount,
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem(),
        )
    }

    private fun updateArtworkFromUri(uri: android.net.Uri?) {
        if (uri == artworkFor && (artwork != null || artworkJob?.isActive == true)) return
        artworkJob?.cancel()
        artworkFor = uri
        artwork = null
        if (uri == null) return
        artworkJob = scope.launch {
            val bmp = Artwork.load(context, uri)
            if (artworkFor == uri && bmp != null) {
                artwork = bmp
                publish()
            }
        }
    }

    private fun updateArtwork(meta: MediaMetadata, fallbackUri: android.net.Uri? = null) {
        val data = meta.artworkData
        val uri = meta.artworkUri ?: fallbackUri
        val key: Any? = data?.contentHashCode() ?: uri
        if (key == artworkFor && (artwork != null || artworkJob?.isActive == true)) return
        artworkJob?.cancel()
        artworkFor = key
        artwork = null
        if (key == null) return
        artworkJob = scope.launch {
            val bmp = withContext(Dispatchers.IO) { data?.let(Artwork::decode) } ?: uri?.let { Artwork.load(context, it) }
            if (artworkFor == key && bmp != null) {
                artwork = bmp
                publish()
            }
        }
    }

    override fun positionMs(): Long {
        val c = controller ?: return 0L
        return if (isOurQueue(c)) c.currentPosition else 0L
    }

    override fun play() = withController { c ->
        if (!isOurQueue(c) && tracks.isNotEmpty()) {
            c.setMediaItems(tracks.map(::toMediaItem), 0, 0L)
            c.prepare()
        } else {
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0L)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
        }
        c.play()
    }

    override fun pause() {
        val c = controller ?: return
        if (isOurQueue(c)) c.pause()
    }

    override fun seekTo(positionMs: Long) {
        val c = controller ?: return
        if (isOurQueue(c)) c.seekTo(positionMs)
    }

    override fun playQueueItem(id: Long) = playTrack(id.toInt())

    override fun skipToNext() {
        val c = controller ?: return
        if (isOurQueue(c) && c.hasNextMediaItem()) c.seekToNextMediaItem()
    }

    override fun skipToPrevious() {
        val c = controller ?: return
        if (isOurQueue(c) && c.hasPreviousMediaItem()) c.seekToPreviousMediaItem()
    }

    override fun release() {
        future?.let(MediaController::releaseFuture)
        future = null
        controller = null
        scope.cancel()
    }
}
