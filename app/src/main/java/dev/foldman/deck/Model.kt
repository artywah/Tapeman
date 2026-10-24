package dev.foldman.deck

import android.graphics.Bitmap
import android.net.Uri
import dev.foldman.plex.PlexAlbum
import dev.foldman.plex.PlexArtist
import dev.foldman.plex.PlexClient
import dev.foldman.plex.PlexSectionTab
import dev.foldman.spotify.SpotifyCollection
import dev.foldman.spotify.SpotifyTab
import dev.foldman.spotify.SpotifyTrack
import kotlinx.coroutines.flow.StateFlow

/** Where the tape in the deck comes from. */
enum class Source(val label: String, val packageName: String?) {
    LOCAL("Local folder", null),
    PLEX("Plex Server", null),
    SPOTIFY("Spotify", "com.spotify.music"),
    YT_MUSIC("YT Music", "com.google.android.apps.youtube.music"),
    APPLE_MUSIC("Apple Music", "com.apple.android.music"),
}

/**
 * What the mechanism is doing. PAUSED keeps the play key latched with the pinch roller lifted;
 * STOPPED releases every key. The reels animate from this.
 */
enum class Transport { STOPPED, PLAYING, PAUSED, REWINDING, FAST_FORWARDING }

sealed interface SourceStatus {
    data object Ready : SourceStatus
    data object NeedsFolder : SourceStatus
    data class Scanning(val found: Int) : SourceStatus
    data object EmptyFolder : SourceStatus
    data object NeedsAccess : SourceStatus
    data object AppNotRunning : SourceStatus
    data object NotInstalled : SourceStatus
    data object NeedsDeveloperToken : SourceStatus
    data object NeedsAppleSignIn : SourceStatus
    data object NeedsPlexAlbum : SourceStatus
}

/** What the tape shelf knows about the user's Apple Music library (MusicKit sign-in + Apple Music API). */
data class AppleUi(
    /** MusicKit's auth AAR is in the build, so Foldman can sign in and browse. */
    val sdkAuth: Boolean = false,
    /** MusicKit's playback AAR is in the build, so Foldman streams in-app instead of driving the Apple Music app. */
    val sdkPlayback: Boolean = false,
    val hasDeveloperToken: Boolean = false,
    val signedIn: Boolean = false,
    val playlists: List<dev.foldman.apple.ApplePlaylist> = emptyList(),
    val openPlaylist: dev.foldman.apple.ApplePlaylist? = null,
    val tracks: List<dev.foldman.apple.AppleTrack> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
)

/** What the tape shelf & library band know about the user's Plex Music Server. */
data class PlexUi(
    val serverUrl: String = PlexClient.DEFAULT_URL,
    val token: String = PlexClient.DEFAULT_TOKEN,
    val libraryName: String = PlexClient.DEFAULT_LIBRARY,
    val tab: PlexSectionTab = PlexSectionTab.ALL,
    val searchQuery: String = "",
    val selectedLetter: String? = null,
    val items: List<PlexAlbum> = emptyList(),
    val artists: List<PlexArtist> = emptyList(),
    val openArtist: PlexArtist? = null,
    val artistAlbums: List<PlexAlbum> = emptyList(),
    val openAlbum: PlexAlbum? = null,
    val tracks: List<LocalTrack> = emptyList(),
    val loadedAlbumTitle: String? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val editingSettings: Boolean = false,
    val message: String? = null,
)

/** What the tape shelf & library band know about the user's Spotify library (OAuth PKCE + Web API). */
data class SpotifyUi(
    val clientId: String = "",
    val signedIn: Boolean = false,
    val tab: SpotifyTab = SpotifyTab.PLAYLISTS,
    val collections: List<SpotifyCollection> = emptyList(),
    val openCollection: SpotifyCollection? = null,
    val tracks: List<SpotifyTrack> = emptyList(),
    val loading: Boolean = false,
    val editingClient: Boolean = false,
    val message: String? = null,
)

data class LocalTrack(
    val uri: Uri,
    val title: String,
    val artist: String?,
    val folder: String,
    val coverUri: Uri?,
)

/** A song in the play queue: a local file (id = index), a Plex track, or another app's queue item. */
data class QueueEntry(
    val id: Long,
    val title: String,
    val subtitle: String?,
    val artwork: Bitmap? = null,
    val artworkUri: Uri? = null,
)

data class NowPlaying(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artwork: Bitmap? = null,
    /** Changes whenever the artwork does, so the label palette is only worked out once per cover. */
    val artworkKey: Any? = null,
    val durationMs: Long = 0,
)

data class BackendState(
    val status: SourceStatus = SourceStatus.Ready,
    val nowPlaying: NowPlaying = NowPlaying(),
    val isPlaying: Boolean = false,
    val ended: Boolean = false,
    val trackIndex: Int = -1,
    val trackCount: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val queue: List<QueueEntry> = emptyList(),
    val currentQueueId: Long = -1,
)

/** One tape source: the local/Plex Media3 player, or another app's media session. */
interface DeckBackend {
    val state: StateFlow<BackendState>
    fun positionMs(): Long
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun skipToNext()
    fun skipToPrevious()
    fun playQueueItem(id: Long)
    /** Re-checks permissions and sessions, e.g. when the app comes back to the foreground. */
    fun refresh() {}
    fun release()
}
