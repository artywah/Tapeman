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

/**
 * Authentic vintage Compact Cassette shells inspired by the tapedeck.org archive.
 */
enum class CassetteShell(
    val id: String,
    val displayName: String,
    val brand: String,
    val year: String,
    val tapeType: String,
    val isTransparent: Boolean,
    val formulation: String,
    val description: String,
) {
    TDK_SA(
        "tdk_sa", "TDK SA-90", "TDK", "1986", "HIGH (TYPE II) · 70µs", false,
        "Super Avilyn (Cobalt-doped Fe2O3)",
        "The undisputed benchmark of Type II High Bias cassettes. Celebrated for high dynamic range, silky top end, and heavy resonance-damping charcoal housing.",
    ),
    TDK_MAXG(
        "tdk_maxg", "TDK MA-XG 90", "TDK", "1986", "METAL (TYPE IV) · 70µs", true,
        "Finavinx Metal Alloy",
        "The holy grail of cassette engineering: a rigid 3-piece die-cast metal and ceramic frame that virtually eliminates wow and flutter. Features white composite dual-ring reels and red leader clamp.",
    ),
    MAXELL_XLIIS(
        "maxell_xliis", "Maxell XLII-S", "Maxell", "1988", "HIGH (TYPE II) · 70µs", false,
        "Pure Epitaxial Magnetic Particles",
        "Maxell's definitive audiophile cassette featuring the Super Silent-Phase Accuracy Cassette Mechanism, faceted spool wells, and central optical alignment reticle.",
    ),
    MAXELL_UDII(
        "maxell_udii", "Maxell UD II", "Maxell", "1990", "HIGH (TYPE II) · 70µs", false,
        "High-Energy Epitaxial",
        "Maxell's iconic 1990 design featuring the High Resonance Damping Cassette Mechanism with signature ultra-wide curved oval viewing window across the spools.",
    ),
    SONY_UXPRO(
        "sony_uxpro", "Sony UX-Pro", "Sony", "1986", "HIGH (TYPE II) · 70µs", true,
        "Super Uniaxial High Bias",
        "Sony's audiophile master cassette featuring an integrated ceramic tape guide block that keeps the tape perfectly aligned against Walkman heads for ultra-crisp highs.",
    ),
    SONY_HF(
        "sony_hf", "Sony HF 90", "Sony", "1985", "NORMAL (TYPE I) · 120µs", false,
        "High Fidelity Gamma-Hematite",
        "The quintessential Walkman companion from the mid-80s, recognizable worldwide by its warm-grey shell and bold orange & red racing stripes.",
    ),
    TEAC_SOUND52(
        "teac_sound52", "TEAC Sound 52", "TEAC", "1984", "NORMAL (TYPE I) · 120µs", true,
        "High-Density Normal Bias · 120µs EQ",
        "Legendary open-reel cassette equipped with dual spun-aluminum 3-window flanges that rotate in real time, mimicking a studio master reel-to-reel deck.",
    ),
}

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
