package dev.foldman.deck

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

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
        "The benchmark of Type II High Bias cassettes. Celebrated for high dynamic range, silky top end, and heavy resonance-damping charcoal housing.",
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
        "sony_uxpro", "UX-Pro 90", "Tapeman", "1986", "HIGH (TYPE II) · 70µs", true,
        "Super Uniaxial High Bias",
        "Audiophile master cassette featuring an integrated ceramic tape guide block that keeps the tape perfectly aligned against heads for ultra-crisp highs.",
    ),
    SONY_HF(
        "sony_hf", "HF 90", "Tapeman", "1985", "NORMAL (TYPE I) · 120µs", false,
        "High Fidelity Gamma-Hematite",
        "The quintessential retro cassette companion from the mid-80s, recognizable worldwide by its warm-grey shell and bold orange & red racing stripes.",
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

/** Cassette label colours, taken from the album art when there is some. */
data class LabelPalette(val paper: Color, val stripe: Color, val stripe2: Color, val ink: Color) {
    companion object {
        val Default = LabelPalette(Color(0xFFF2EBDC), Color(0xFFE8642C), Color(0xFFF2B33D), Color(0xFF2A2522))
    }
}

data class PlexAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int = 0,
    val coverUrl: String? = null,
    val isPlaylist: Boolean = false,
    val trackCount: Int = 0,
)

data class PlexArtist(
    val id: String,
    val name: String,
    val thumbUrl: String? = null,
    val summary: String? = null,
)

data class LocalTrack(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val coverUri: String? = null,
    val folder: String = "",
)

data class PlexUi(
    val serverUrl: String = "",
    val token: String = "",
    val libraryName: String = "",
    val searchQuery: String = "",
    val selectedLetter: String? = null,
    val items: List<PlexAlbum> = emptyList(),
    val artists: List<PlexArtist> = emptyList(),
    val openArtist: PlexArtist? = null,
    val artistAlbums: List<PlexAlbum> = emptyList(),
    val openAlbum: PlexAlbum? = null,
    val tracks: List<LocalTrack> = emptyList(),
    val loadedAlbumTitle: String? = null,
    val isSonicDjActive: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val editingSettings: Boolean = false,
    val message: String? = null,
)

data class DeckUi(
    val source: Source = Source.LOCAL,
    val transport: Transport = Transport.STOPPED,
    val status: SourceStatus = SourceStatus.Ready,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artwork: ImageBitmap? = null,
    val label: LabelPalette = LabelPalette.Default,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val trackIndex: Int = -1,
    val trackCount: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val tracks: List<LocalTrack> = emptyList(),
    val folderName: String? = null,
    val volume: Float = 0.5f,
    val keySounds: Boolean = true,
    val haptics: Boolean = true,
    val holdLocked: Boolean = false,
    val dbbEnabled: Boolean = false,
    val avlsEnabled: Boolean = false,
    val shell: CassetteShell = CassetteShell.TDK_MAXG,
    val reverseDir: Boolean = false,
    val isCurrentFavorite: Boolean = false,
    val plex: PlexUi = PlexUi(),
) {
    val transparentShell: Boolean get() = shell.isTransparent
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
