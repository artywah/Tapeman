package dev.foldman.desktop

import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import dev.foldman.deck.CassetteShell
import dev.foldman.deck.DeckUi
import dev.foldman.deck.LocalTrack
import dev.foldman.deck.PlexAlbum
import dev.foldman.deck.Source
import dev.foldman.deck.Transport
import dev.foldman.desktop.audio.DesktopAudioRouter
import dev.foldman.desktop.audio.DesktopMechSounds
import dev.foldman.desktop.device.DeviceDiscoveryService
import dev.foldman.desktop.device.DeviceType
import dev.foldman.desktop.local.DesktopLocalBrowser
import dev.foldman.desktop.local.DesktopLocalLibrary
import dev.foldman.desktop.plex.ConnectToDeviceDialog
import dev.foldman.desktop.plex.DesktopPlexClient
import dev.foldman.desktop.plex.PlexAuthService
import dev.foldman.desktop.plex.PlexConnectionDialog
import dev.foldman.desktop.plex.PlexMusicBrowser
import dev.foldman.desktop.plex.PlexSavedConfig
import dev.foldman.ui.DeckColors
import dev.foldman.ui.LocalHaptics
import dev.foldman.ui.LocalMechSounds
import dev.foldman.ui.WalkmanPlayerUnit
import dev.foldman.ui.rememberDeckAnimations
import kotlinx.coroutines.launch

fun main() = application {
    val mechSounds = remember { DesktopMechSounds() }
    val audioBackend = remember { DesktopAudioRouter() }
    val authService = remember { PlexAuthService() }
    val plexClient = remember { DesktopPlexClient() }

    // Default window opens at the ideal 2:1 landscape ratio (1200dp x 590dp) matching user screenshot
    val windowState = rememberWindowState(width = 1200.dp, height = 590.dp)
    var isMiniPlayer by remember { mutableStateOf(false) }

    val appIcon = remember {
        runCatching {
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("icons/icon.png")
            stream?.use { androidx.compose.ui.res.loadImageBitmap(it) }?.let {
                androidx.compose.ui.graphics.painter.BitmapPainter(it)
            }
        }.getOrNull()
    }

    LaunchedEffect(Unit) {
        if (java.awt.Taskbar.isTaskbarSupported()) {
            runCatching {
                val taskbar = java.awt.Taskbar.getTaskbar()
                if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
                    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("icons/icon.png")
                    stream?.use {
                        val img = javax.imageio.ImageIO.read(it)
                        if (img != null) {
                            taskbar.iconImage = img
                        }
                    }
                }
            }
        }
    }

    Window(
        onCloseRequest = {
            audioBackend.release()
            mechSounds.release()
            exitApplication()
        },
        title = if (isMiniPlayer) "Tapeman" else "Tapeman · Retro Stereo Cassette Player",
        icon = appIcon,
        state = windowState,
        alwaysOnTop = isMiniPlayer,
        resizable = false,
    ) {
        MaterialTheme {
            TapemanDesktopApp(
                mechSounds = mechSounds,
                audioBackend = audioBackend,
                authService = authService,
                plexClient = plexClient,
                isMiniPlayer = isMiniPlayer,
                onToggleMiniPlayer = {
                    isMiniPlayer = !isMiniPlayer
                    if (isMiniPlayer) {
                        windowState.size = DpSize(470.dp, 390.dp)
                    } else {
                        windowState.size = DpSize(1200.dp, 590.dp)
                    }
                }
            )
        }
    }
}

enum class DesktopSource(val label: String) {
    PLEX("Plex Server"),
    LOCAL("Local Folder"),
}

@Composable
fun TapemanDesktopApp(
    mechSounds: DesktopMechSounds,
    audioBackend: DesktopAudioRouter,
    authService: PlexAuthService,
    plexClient: DesktopPlexClient,
    isMiniPlayer: Boolean = false,
    onToggleMiniPlayer: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val deviceService = remember { DeviceDiscoveryService() }
    val currentDevice by deviceService.currentDevice.collectAsState()

    // Hook device selection directly to active audio router
    LaunchedEffect(deviceService, audioBackend) {
        deviceService.onDeviceSelected = { selectedDevice ->
            val ok = audioBackend.switchDevice(selectedDevice)
            Pair(ok, audioBackend.lastError)
        }
    }

    var savedConfig by remember { mutableStateOf(authService.loadConfig()) }
    var activeSource by remember { mutableStateOf(DesktopSource.PLEX) }
    var showPlexDialog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var isSonicDjActive by remember { mutableStateOf(false) }

    // Local folder state
    var localFolder by remember { mutableStateOf(DesktopLocalLibrary.loadSavedFolder()) }
    var localTracks by remember { mutableStateOf<List<LocalTrack>>(emptyList()) }
    LaunchedEffect(localFolder) {
        localTracks = localFolder?.let { DesktopLocalLibrary.scanFolder(it) } ?: emptyList()
    }

    var albumArtwork by remember { mutableStateOf<ImageBitmap?>(null) }

    // Sound effects toggle state (synced with hardware switch & persistent config)
    var keySoundsEnabled by remember { mutableStateOf(savedConfig?.keySounds ?: true) }
    LaunchedEffect(keySoundsEnabled) {
        mechSounds.enabled = keySoundsEnabled
    }

    // Hardware deck states
    var dbbEnabled by remember { mutableStateOf(false) }
    var avlsEnabled by remember { mutableStateOf(false) }
    var holdLocked by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(1f) }
    var cassetteDoorOpen by remember { mutableStateOf(false) }

    // Synchronize client credentials whenever savedConfig changes
    LaunchedEffect(savedConfig) {
        savedConfig?.let { cfg ->
            plexClient.serverUrl = cfg.serverUrl
            plexClient.token = cfg.serverToken
            cfg.librarySectionId?.let { plexClient.sectionId = it }
            keySoundsEnabled = cfg.keySounds
            mechSounds.enabled = cfg.keySounds
        }
    }

    var selectedShell by remember { mutableStateOf(CassetteShell.SONY_UXPRO) }

    // Track & Queue metadata
    var trackTitle by remember { mutableStateOf("Automatic Music Sensor") }
    var trackArtist by remember { mutableStateOf("Arty · Tapeman") }
    var albumTitle by remember { mutableStateOf("Type IV Metal Master") }
    var activeTrackUrl by remember { mutableStateOf<String?>(null) }
    var currentQueue by remember { mutableStateOf<List<LocalTrack>>(emptyList()) }
    var currentTrackIndex by remember { mutableIntStateOf(0) }
    var currentAlbum by remember { mutableStateOf<PlexAlbum?>(null) }
    var currentTrackDurationMs by remember { mutableStateOf(0L) }
    var navigateToAlbumRequest by remember { mutableStateOf<Pair<PlexAlbum, Int>?>(null) }
    var navCounter by remember { mutableIntStateOf(0) }

    // Audio engine state bindings
    val localAudioIsPlaying by audioBackend.isPlaying.collectAsState()
    val localAudioPosMs by audioBackend.currentPositionMs.collectAsState()
    val audioDurMs by audioBackend.durationMs.collectAsState()
    val audioVolume by audioBackend.volume.collectAsState()

    val isPlaying = localAudioIsPlaying
    val audioPosMs = localAudioPosMs

    LaunchedEffect(audioVolume) {
        volume = audioVolume
    }

    // Winding state: -1 for REW, +1 for FF, 0 for idle
    var winding by remember { mutableStateOf(0) }
    var windPosMs by remember { mutableStateOf(0L) }
    var wasPlayingBeforeWind by remember { mutableStateOf(false) }
    var windJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val displayPosMs = if (winding != 0) windPosMs else audioPosMs
    val hasActivePlayback = activeTrackUrl != null
    val positionMs = if (hasActivePlayback) displayPosMs else 46000L
    val effectiveDurMs = when {
        activeTrackUrl != null && audioDurMs > 0L -> audioDurMs
        activeTrackUrl != null && currentTrackDurationMs > 0L -> currentTrackDurationMs
        else -> 0L
    }
    val durationMs = if (effectiveDurMs > 0L) effectiveDurMs else 240000L

    // Determine current transport state
    val transport = when {
        winding < 0 -> Transport.REWINDING
        winding > 0 -> Transport.FAST_FORWARDING
        isPlaying -> Transport.PLAYING
        displayPosMs > 0L && hasActivePlayback -> Transport.PAUSED
        else -> Transport.STOPPED
    }

    val hasNext = currentQueue.isNotEmpty() && currentTrackIndex < currentQueue.size - 1
    val hasPrevious = currentQueue.isNotEmpty() && currentTrackIndex > 0

    val effectiveTitle = trackTitle
    val effectiveArtist = trackArtist
    val effectiveAlbum = albumTitle

    val ui = DeckUi(
        source = when (activeSource) {
            DesktopSource.PLEX -> Source.PLEX
            DesktopSource.LOCAL -> Source.LOCAL
        },
        transport = transport,
        title = effectiveTitle,
        artist = effectiveArtist,
        album = effectiveAlbum,
        artwork = albumArtwork,
        positionMs = positionMs,
        durationMs = durationMs,
        shell = selectedShell,
        keySounds = keySoundsEnabled,
        dbbEnabled = dbbEnabled,
        avlsEnabled = avlsEnabled,
        volume = volume,
        holdLocked = holdLocked,
        hasNext = hasNext,
        hasPrevious = hasPrevious,
    )

    val handleRewindHold: (Boolean) -> Unit = { isDown ->
        if (isDown) {
            windJob?.cancel()
            windJob = scope.launch {
                val wasPlaying = isPlaying
                wasPlayingBeforeWind = wasPlaying
                windPosMs = audioPosMs
                delay(220) // distinguish tap from hold
                if (wasPlaying) {
                    audioBackend.pause()
                }
                winding = -1
                var lastTime = System.currentTimeMillis()
                while (isActive) {
                    val now = System.currentTimeMillis()
                    val dt = (now - lastTime).coerceAtLeast(1)
                    lastTime = now
                    val deltaMs = dt * 14L
                    windPosMs = (windPosMs - deltaMs).coerceAtLeast(0L)
                    delay(30)
                }
            }
        } else {
            val job = windJob
            windJob = null
            if (job != null && job.isActive && winding == 0) {
                // Quick tap release before hold threshold -> skip back 15 seconds
                job.cancel()
                val newPos = (audioPosMs - 15000L).coerceAtLeast(0L)
                windPosMs = newPos
                audioBackend.seekTo(newPos)
            } else if (winding != 0) {
                // Released after holding -> seek to final position and resume if previously playing
                job?.cancel()
                val target = windPosMs
                winding = 0
                audioBackend.seekTo(target)
                if (wasPlayingBeforeWind) {
                    audioBackend.resume()
                }
            }
        }
    }

    val handleFastForwardHold: (Boolean) -> Unit = { isDown ->
        if (isDown) {
            windJob?.cancel()
            windJob = scope.launch {
                val wasPlaying = isPlaying
                wasPlayingBeforeWind = wasPlaying
                windPosMs = audioPosMs
                delay(220) // distinguish tap from hold
                if (wasPlaying) {
                    audioBackend.pause()
                }
                winding = 1
                var lastTime = System.currentTimeMillis()
                while (isActive) {
                    val now = System.currentTimeMillis()
                    val dt = (now - lastTime).coerceAtLeast(1)
                    lastTime = now
                    val deltaMs = dt * 14L
                    windPosMs = (windPosMs + deltaMs).coerceAtMost(durationMs)
                    delay(30)
                }
            }
        } else {
            val job = windJob
            windJob = null
            if (job != null && job.isActive && winding == 0) {
                // Quick tap release before hold threshold -> skip forward 15 seconds
                job.cancel()
                val newPos = (audioPosMs + 15000L).coerceAtMost(durationMs)
                windPosMs = newPos
                audioBackend.seekTo(newPos)
            } else if (winding != 0) {
                // Released after holding -> seek to final position and resume if previously playing
                job?.cancel()
                val target = windPosMs
                winding = 0
                audioBackend.seekTo(target)
                if (wasPlayingBeforeWind) {
                    audioBackend.resume()
                }
            }
        }
    }

    val anim = rememberDeckAnimations(ui.transport, ui.progress)

    fun slideTapeIn() {
        scope.launch {
            anim.insert.snapTo(1f)
            anim.insert.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 160f))
        }
    }

    LaunchedEffect(selectedShell) {
        slideTapeIn()
    }

    fun playPlexTrack(
        track: LocalTrack,
        album: PlexAlbum? = null,
        queue: List<LocalTrack> = currentQueue,
        index: Int = 0
    ) {
        val resolvedAlbum = if (album != null && (track.albumRatingKey == null || album.id == track.albumRatingKey)) {
            album
        } else {
            PlexAlbum(
                id = track.albumRatingKey ?: track.ratingKey ?: "sonic_${track.title.hashCode()}",
                title = track.album.ifBlank { "Sonic DJ Flow" },
                artist = track.artist.ifBlank { "Various Artists" },
                coverUrl = track.coverUri,
                trackCount = queue.size
            )
        }
        trackTitle = track.title
        trackArtist = track.artist
        albumTitle = resolvedAlbum.title
        activeTrackUrl = track.uri
        currentQueue = queue
        currentTrackIndex = index
        currentAlbum = resolvedAlbum
        currentTrackDurationMs = track.durationMs

        audioBackend.setTrackInfo(
            title = track.title,
            artist = track.artist,
            album = resolvedAlbum.title,
            coverUrl = track.coverUri ?: resolvedAlbum.coverUrl,
            durationMs = track.durationMs
        )

        mechSounds.keyDown()
        slideTapeIn()
        audioBackend.play(track.uri)

        scope.launch {
            albumArtwork = plexClient.loadArtwork(track.coverUri ?: resolvedAlbum.coverUrl)
        }
        scope.launch {
            plexClient.reportTimeline(track.ratingKey, "playing", 0L, track.durationMs)
        }
    }

    fun playAlbumDirectly(album: PlexAlbum) {
        scope.launch {
            val tracks = plexClient.fetchTracks(album)
            if (tracks.isNotEmpty()) {
                playPlexTrack(tracks.first(), album, tracks, 0)
            }
        }
    }

    var isSonicReplenishing by remember { mutableStateOf(false) }
    var isHolidaySonicSession by remember { mutableStateOf(false) }

    fun filterNewSonicTracks(candidates: List<LocalTrack>, existingQueue: List<LocalTrack>): List<LocalTrack> {
        val existingUris = existingQueue.map { it.uri }.toSet()
        val existingTitles = existingQueue.map { it.title.lowercase().replace(Regex("[^a-z0-9]"), "") }.toSet()
        return candidates.filter { t ->
            val norm = t.title.lowercase().replace(Regex("[^a-z0-9]"), "")
            t.uri !in existingUris && (norm.isBlank() || norm !in existingTitles)
        }
    }

    fun toggleSonicDj() {
        mechSounds.keyDown()
        val next = !isSonicDjActive
        isSonicDjActive = next
        if (!next) {
            isHolidaySonicSession = false
        }

        if (next) {
            val currTrack = currentQueue.getOrNull(currentTrackIndex)
            if (currTrack != null && activeTrackUrl != null) {
                isHolidaySonicSession = plexClient.isHolidayTitleOrAlbum(currTrack.title, currTrack.album)
                // Sonic DJ turned on while a song is playing: fetch similar tracks to follow right after
                scope.launch {
                    val similar = plexClient.fetchSonicallySimilarTracks(
                        currTrack.ratingKey,
                        limit = 15,
                        forceHolidayTheme = isHolidaySonicSession
                    )
                    if (similar.isNotEmpty()) {
                        val newItems = filterNewSonicTracks(similar, listOf(currTrack))
                        if (newItems.isNotEmpty()) {
                            currentQueue = currentQueue.take(currentTrackIndex + 1) + newItems
                        }
                    }
                }
            } else {
                // Seed acoustic sonic flow from recent or top library items
                scope.launch {
                    val home = plexClient.fetchHomeData()
                    val seedAlbum = home.recentlyPlayed.firstOrNull() ?: home.hubs.firstOrNull()?.items?.firstOrNull()
                    if (seedAlbum != null) {
                        val tracks = plexClient.fetchTracks(seedAlbum)
                        if (tracks.isNotEmpty()) {
                            playPlexTrack(tracks.first(), seedAlbum, tracks, 0)
                        }
                    }
                }
            }
        }
    }

    fun selectDesktopSource(next: DesktopSource) {
        if (next == activeSource) return
        mechSounds.switchFlip()
        if (next != DesktopSource.PLEX) {
            isSonicDjActive = false
            isHolidaySonicSession = false
        }
        activeSource = next
    }

    fun playLocalTrack(track: LocalTrack, queue: List<LocalTrack>, index: Int) {
        activeSource = DesktopSource.LOCAL
        isSonicDjActive = false
        isHolidaySonicSession = false
        trackTitle = track.title
        trackArtist = track.artist
        albumTitle = track.folder
        activeTrackUrl = track.uri
        currentQueue = queue
        currentTrackIndex = index
        currentAlbum = null
        currentTrackDurationMs = track.durationMs
        albumArtwork = null

        audioBackend.setTrackInfo(
            title = track.title,
            artist = track.artist,
            album = track.folder,
            coverUrl = null,
            durationMs = track.durationMs
        )
        mechSounds.keyDown()
        slideTapeIn()
        audioBackend.play(track.uri)
    }

    fun playPrevious() {
        mechSounds.keyDown()
        if (currentQueue.isNotEmpty() && currentTrackIndex > 0) {
            val prevIdx = currentTrackIndex - 1
            if (activeSource == DesktopSource.LOCAL) {
                playLocalTrack(currentQueue[prevIdx], currentQueue, prevIdx)
            } else {
                playPlexTrack(currentQueue[prevIdx], currentAlbum, currentQueue, prevIdx)
            }
        } else if (activeTrackUrl != null) {
            audioBackend.seekTo(0L)
        }
    }

    fun playNext() {
        mechSounds.keyDown()
        if (currentQueue.isNotEmpty() && currentTrackIndex < currentQueue.size - 1) {
            val nextIdx = currentTrackIndex + 1
            val nextTrack = currentQueue[nextIdx]
            if (activeSource == DesktopSource.LOCAL) {
                playLocalTrack(nextTrack, currentQueue, nextIdx)
            } else {
                playPlexTrack(nextTrack, currentAlbum, currentQueue, nextIdx)
            }
        } else if (activeSource == DesktopSource.PLEX && isSonicDjActive && !isSonicReplenishing) {
            // Reached end of current queue with Sonic DJ on: fetch more immediately and play!
            isSonicReplenishing = true
            val curr = currentQueue.getOrNull(currentTrackIndex)
            scope.launch {
                try {
                    val more = plexClient.fetchSonicallySimilarTracks(curr?.ratingKey, limit = 15, forceHolidayTheme = isHolidaySonicSession)
                    if (more.isNotEmpty()) {
                        val newItems = filterNewSonicTracks(more, currentQueue)
                        val toAdd = if (newItems.isNotEmpty()) newItems else more
                        val nextIdx = currentQueue.size
                        val newQueue = currentQueue + toAdd
                        currentQueue = newQueue
                        playPlexTrack(toAdd.first(), currentAlbum, newQueue, nextIdx)
                    }
                } finally {
                    isSonicReplenishing = false
                }
            }
        }
    }

    // Track which queue index we already auto-advanced from to prevent double-skipping while next track loads
    var lastAutoAdvancedIndex by remember { mutableIntStateOf(-1) }
    var lastScrobbledUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeTrackUrl, currentTrackIndex) {
        if (isPlaying) {
            lastAutoAdvancedIndex = -1
        }
    }

    // Report scrobble to Plex once track passes 80% (or ends) so "Jump Back In" and play history update
    LaunchedEffect(activeSource, activeTrackUrl, audioPosMs, effectiveDurMs, isPlaying) {
        if (activeSource != DesktopSource.PLEX) return@LaunchedEffect
        val curr = currentQueue.getOrNull(currentTrackIndex) ?: return@LaunchedEffect
        val uri = activeTrackUrl ?: return@LaunchedEffect
        if (lastScrobbledUri == uri) return@LaunchedEffect
        val threshold = (effectiveDurMs * 0.8f).toLong().coerceAtLeast(10_000L)
        if (effectiveDurMs > 0L && audioPosMs >= threshold.coerceAtMost(effectiveDurMs)) {
            lastScrobbledUri = uri
            plexClient.scrobble(curr.ratingKey)
        }
    }

    // Auto-advance to next track when song finishes (or continuously advance via Sonic DJ)
    LaunchedEffect(isPlaying, audioPosMs, effectiveDurMs, hasNext, isSonicDjActive, currentTrackIndex, activeSource) {
        if (isPlaying) {
            lastAutoAdvancedIndex = -1
            return@LaunchedEffect
        }
        val atEnd = effectiveDurMs > 0 && (audioPosMs >= effectiveDurMs - 1500L || (audioPosMs > 0 && audioPosMs == effectiveDurMs))
        if (atEnd && (hasNext || (activeSource == DesktopSource.PLEX && isSonicDjActive)) && lastAutoAdvancedIndex != currentTrackIndex) {
            lastAutoAdvancedIndex = currentTrackIndex
            if (activeSource == DesktopSource.PLEX) {
                val curr = currentQueue.getOrNull(currentTrackIndex)
                if (curr != null && lastScrobbledUri != curr.uri) {
                    lastScrobbledUri = curr.uri
                    scope.launch { plexClient.scrobble(curr.ratingKey) }
                }
            }
            playNext()
        }
    }

    // Sonic DJ dynamic replenishment: when near end of queue, fetch more acoustic tracks ahead of time
    LaunchedEffect(isSonicDjActive, currentTrackIndex, currentQueue.size) {
        if (isSonicDjActive && currentQueue.isNotEmpty() && currentTrackIndex >= currentQueue.size - 2 && !isSonicReplenishing) {
            val curr = currentQueue.getOrNull(currentTrackIndex) ?: return@LaunchedEffect
            scope.launch {
                isSonicReplenishing = true
                try {
                    val more = plexClient.fetchSonicallySimilarTracks(curr.ratingKey, limit = 15, forceHolidayTheme = isHolidaySonicSession)
                    if (more.isNotEmpty()) {
                        val newItems = filterNewSonicTracks(more, currentQueue)
                        if (newItems.isNotEmpty()) {
                            currentQueue = currentQueue + newItems
                        }
                    }
                } finally {
                    isSonicReplenishing = false
                }
            }
        }
    }

    fun updateKeySounds(enabled: Boolean) {
        keySoundsEnabled = enabled
        mechSounds.enabled = enabled
        savedConfig?.let { cfg ->
            val updated = cfg.copy(keySounds = enabled)
            authService.saveConfig(updated)
            savedConfig = updated
        }
    }

    CompositionLocalProvider(
        LocalMechSounds provides mechSounds.takeIf { keySoundsEnabled },
        LocalHaptics provides false,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeckColors.Backdrop),
            contentAlignment = Alignment.Center
        ) {
            if (isMiniPlayer) {
                // ==========================================
                // DEDICATED SPOTIFY-STYLE MINI PLAYER VIEW
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Mini Player Top Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Track & Artist Status with Live Playing LED
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(if (isPlaying) Color(0xFF4CAF50) else Color(0xFFE5A00D), CircleShape)
                            )
                            Text(
                                text = listOf(ui.title, ui.artist).filter { it.isNotBlank() }.joinToString(" — ").ifBlank { "ARTY TAPEMAN" },
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD0D4DC),
                                ),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        // Right: Sonic DJ, Device Picker, Open Library Button + Settings
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (activeSource == DesktopSource.PLEX && savedConfig != null && isSonicDjActive) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF003840))
                                        .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(5.dp))
                                        .clickable { toggleSonicDj() }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "🎧 DJ",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Device Picker icon
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (currentDevice.type != DeviceType.MAC) Color(0xFF10281E) else Color(0xFF22242B))
                                    .border(
                                        1.dp,
                                        if (currentDevice.type != DeviceType.MAC) Color(0xFF1DB954) else Color(0xFF333640),
                                        RoundedCornerShape(5.dp)
                                    )
                                    .clickable {
                                        mechSounds.keyDown()
                                        showDeviceDialog = true
                                    }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "⎚",
                                    color = if (currentDevice.type != DeviceType.MAC) Color(0xFF1DB954) else Color(0xFFDFDFE5),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Prominent Golden "Open Library" Action Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFFE5A00D))
                                    .clickable {
                                        mechSounds.keyDown()
                                        onToggleMiniPlayer()
                                    }
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "⤢ Open Library",
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                            }

                            // Quick Settings Icon
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF22242B))
                                    .clickable {
                                        mechSounds.keyDown()
                                        showPlexDialog = true
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text("⚙", color = Color(0xFFDFDFE5), fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Player Unit fitted snuggly to window height
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        WalkmanPlayerUnit(
                            ui = ui,
                            anim = anim,
                            doorOpen = cassetteDoorOpen,
                            onToggleDoor = {
                                cassetteDoorOpen = !cassetteDoorOpen
                                if (cassetteDoorOpen) mechSounds.keyDown() else mechSounds.autoStop()
                            },
                            onCloseDoor = {
                                if (cassetteDoorOpen) {
                                    cassetteDoorOpen = false
                                    mechSounds.autoStop()
                                }
                            },
                            onPlayToggle = {
                                if (isPlaying) {
                                    mechSounds.pause()
                                    audioBackend.pause()
                                } else {
                                    mechSounds.play()
                                    if (activeTrackUrl != null) {
                                        audioBackend.resume()
                                    } else {
                                        audioBackend.seekTo(0L)
                                        audioBackend.resume()
                                    }
                                }
                            },
                            onStop = {
                                mechSounds.autoStop()
                                audioBackend.stop()
                            },
                            onRewind = {
                                mechSounds.keyUp()
                                val newPos = (displayPosMs - 15000L).coerceAtLeast(0L)
                                audioBackend.seekTo(newPos)
                            },
                            onFastForward = {
                                mechSounds.keyUp()
                                val newPos = (displayPosMs + 15000L).coerceAtMost(durationMs)
                                audioBackend.seekTo(newPos)
                            },
                            onRewindHold = handleRewindHold,
                            onFastForwardHold = handleFastForwardHold,
                            onNext = ::playNext,
                            onPrevious = ::playPrevious,
                            onSetVolume = {
                                volume = it
                                audioBackend.setVolume(it)
                            },
                            onToggleHold = {
                                holdLocked = !holdLocked
                                mechSounds.switchFlip()
                            },
                            onToggleDbb = {
                                dbbEnabled = !dbbEnabled
                                mechSounds.switchFlip()
                            },
                            onToggleAvls = {
                                avlsEnabled = !avlsEnabled
                                mechSounds.switchFlip()
                            },
                            onCycleShell = {
                                val shells = CassetteShell.entries
                                val nextIdx = (shells.indexOf(selectedShell) + 1) % shells.size
                                selectedShell = shells[nextIdx]
                                mechSounds.switchFlip()
                            },
                            onResetCounter = {
                                mechSounds.keyDown()
                                anim.reels.resetCounter()
                            },
                            modifier = Modifier.fillMaxSize(),
                            forcePortrait = false,
                        )
                    }
                }
            } else {
                // ==========================================
                // FULL DUAL VIEW: LIBRARY + RETRO WALKMAN
                // ==========================================
                Column(
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Top Window Header Bar: clean, no redundant logos
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Server / Library Status Breadcrumb & Active Device
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(if (savedConfig != null) Color(0xFF4CAF50) else Color(0xFF6B7280), CircleShape)
                            )
                            Text(
                                text = buildString {
                                    if (savedConfig != null) {
                                        append("${savedConfig?.serverName ?: "Plex"} · ${savedConfig?.libraryName ?: "Music"}".uppercase())
                                    } else {
                                        append("STANDALONE MODE")
                                    }
                                    if (currentDevice.type != DeviceType.MAC) {
                                        append(" · ⎚ ${currentDevice.name.uppercase()}")
                                    }
                                },
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = if (currentDevice.type != DeviceType.MAC) Color(0xFF1DB954) else Color(0xFF8E93A0),
                                ),
                            )
                        }

                        // Right: Sonic DJ, Device Picker, Mini Player, Library Toggle, Settings
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Sonic DJ Active Indicator / Quick Toggle (Plex-only)
                            if (activeSource == DesktopSource.PLEX && savedConfig != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSonicDjActive) Color(0xFF003840) else Color(0xFF17181C))
                                        .border(
                                            1.dp,
                                            if (isSonicDjActive) Color(0xFF00E5FF) else Color(0xFF333640),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { toggleSonicDj() }
                                        .padding(horizontal = 9.dp, vertical = 5.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("🎧", fontSize = 11.sp)
                                        Text(
                                            text = if (isSonicDjActive) "SONIC DJ: ON" else "Sonic DJ",
                                            color = if (isSonicDjActive) Color(0xFF00E5FF) else Color(0xFFDFDFE5),
                                            fontSize = 11.sp,
                                            fontWeight = if (isSonicDjActive) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // Spotify Connect Style Device Picker Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (currentDevice.type != DeviceType.MAC) Color(0xFF10281E) else Color(0xFF17181C))
                                    .border(
                                        1.dp,
                                        if (currentDevice.type != DeviceType.MAC) Color(0xFF1DB954) else Color(0xFF333640),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        mechSounds.keyDown()
                                        showDeviceDialog = true
                                    }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("⎚", color = if (currentDevice.type != DeviceType.MAC) Color(0xFF1DB954) else Color(0xFFDFDFE5), fontSize = 12.sp)
                                    Text(
                                        text = if (currentDevice.type != DeviceType.MAC) currentDevice.name else "Devices",
                                        color = if (currentDevice.type != DeviceType.MAC) Color(0xFF1DB954) else Color(0xFFDFDFE5),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Mini Player Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF17181C))
                                    .border(1.dp, Color(0xFF333640), RoundedCornerShape(6.dp))
                                    .clickable {
                                        mechSounds.keyDown()
                                        onToggleMiniPlayer()
                                    }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("▣", color = Color(0xFFDFDFE5), fontSize = 11.sp)
                                    Text(
                                        text = "Mini Player",
                                        color = Color(0xFFDFDFE5),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Settings Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E2026))
                                    .border(1.dp, Color(0xFF333842), RoundedCornerShape(6.dp))
                                    .clickable {
                                        mechSounds.keyDown()
                                        showPlexDialog = true
                                    }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("⚙", color = Color(0xFFE5A00D), fontSize = 11.sp)
                                    Text(
                                        text = "Settings",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .background(if (savedConfig != null) Color(0xFF4CAF50) else Color(0xFFE5A00D), CircleShape)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Main Content Workspace
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left Pane: Active Source Browser (full height)
                        Box(
                            modifier = Modifier
                                .weight(1.05f)
                                .fillMaxHeight()
                        ) {
                            when (activeSource) {
                                DesktopSource.PLEX -> {
                                    PlexMusicBrowser(
                                        client = plexClient,
                                        currentConfig = savedConfig,
                                        isSonicDjActive = isSonicDjActive,
                                        onToggleSonicDj = ::toggleSonicDj,
                                        onSwitchLibrary = { newSecId, newSecName ->
                                            savedConfig?.let { cfg ->
                                                val updated = cfg.copy(libraryName = newSecName, librarySectionId = newSecId)
                                                authService.saveConfig(updated)
                                                savedConfig = updated
                                                plexClient.sectionId = newSecId
                                            }
                                        },
                                        onTrackSelected = { track, queue, album ->
                                            if (isSonicDjActive) {
                                                isHolidaySonicSession = plexClient.isHolidayTitleOrAlbum(track.title, album.title)
                                                playPlexTrack(track, album, listOf(track), 0)
                                            } else {
                                                val idx = queue.indexOfFirst { it.uri == track.uri }.coerceAtLeast(0)
                                                playPlexTrack(track, album, queue, idx)
                                            }
                                        },
                                        onPlayAlbumDirectly = ::playAlbumDirectly,
                                        onOpenConnectDialog = { showPlexDialog = true },
                                        activeTrackUri = activeTrackUrl,
                                        navigateToAlbumRequest = navigateToAlbumRequest,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                DesktopSource.LOCAL -> {
                                    DesktopLocalBrowser(
                                        activeFolder = localFolder,
                                        tracks = localTracks,
                                        activeTrackUri = activeTrackUrl,
                                        onPickFolder = {
                                            mechSounds.keyDown()
                                            DesktopLocalLibrary.pickFolder()?.let { chosen ->
                                                DesktopLocalLibrary.saveFolder(chosen)
                                                localFolder = chosen
                                            }
                                        },
                                        onTrackSelected = { track, queue, idx ->
                                            playLocalTrack(track, queue, idx)
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        // Right Pane: Authentic Retro Walkman Hardware Player Unit
                        Column(
                            modifier = Modifier.weight(0.95f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                val targetAspect = 1.52f
                                val fittedModifier = if (maxWidth / maxHeight > targetAspect) {
                                    Modifier
                                        .fillMaxHeight()
                                        .aspectRatio(targetAspect, matchHeightConstraintsFirst = true)
                                } else {
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(targetAspect, matchHeightConstraintsFirst = false)
                                }

                                WalkmanPlayerUnit(
                                    ui = ui,
                                    anim = anim,
                                    doorOpen = cassetteDoorOpen,
                                    onToggleDoor = {
                                        cassetteDoorOpen = !cassetteDoorOpen
                                        if (cassetteDoorOpen) mechSounds.keyDown() else mechSounds.autoStop()
                                    },
                                    onCloseDoor = {
                                        if (cassetteDoorOpen) {
                                            cassetteDoorOpen = false
                                            mechSounds.autoStop()
                                        }
                                    },
                                    onPlayToggle = {
                                        if (isPlaying) {
                                            mechSounds.pause()
                                            audioBackend.pause()
                                        } else {
                                            mechSounds.play()
                                            if (activeTrackUrl != null) {
                                                audioBackend.resume()
                                            } else {
                                                audioBackend.seekTo(0L)
                                                audioBackend.resume()
                                            }
                                        }
                                    },
                                    onStop = {
                                        mechSounds.autoStop()
                                        audioBackend.stop()
                                    },
                                    onRewind = {
                                        mechSounds.keyUp()
                                        val newPos = (displayPosMs - 15000L).coerceAtLeast(0L)
                                        audioBackend.seekTo(newPos)
                                    },
                                    onFastForward = {
                                        mechSounds.keyUp()
                                        val newPos = (displayPosMs + 15000L).coerceAtMost(durationMs)
                                        audioBackend.seekTo(newPos)
                                    },
                                    onRewindHold = handleRewindHold,
                                    onFastForwardHold = handleFastForwardHold,
                                    onNext = ::playNext,
                                    onPrevious = ::playPrevious,
                                    onSetVolume = {
                                        volume = it
                                        audioBackend.setVolume(it)
                                    },
                                    onToggleHold = {
                                        holdLocked = !holdLocked
                                        mechSounds.switchFlip()
                                    },
                                    onToggleDbb = {
                                        dbbEnabled = !dbbEnabled
                                        mechSounds.switchFlip()
                                    },
                                    onToggleAvls = {
                                        avlsEnabled = !avlsEnabled
                                        mechSounds.switchFlip()
                                    },
                                    onCycleShell = {
                                        val shells = CassetteShell.entries
                                        val nextIdx = (shells.indexOf(selectedShell) + 1) % shells.size
                                        selectedShell = shells[nextIdx]
                                        mechSounds.switchFlip()
                                    },
                                    onResetCounter = {
                                        mechSounds.keyDown()
                                        anim.reels.resetCounter()
                                    },
                                    modifier = fittedModifier,
                                    forcePortrait = false,
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            // Dedicated Cassette Tape Archive Selector Tray
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF131417))
                                    .border(1.dp, Color(0xFF22242B), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "TAPE ARCHIVE",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = Color(0xFF727680)
                                    )
                                )
                                Spacer(Modifier.width(8.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items(CassetteShell.entries) { shell ->
                                        val isSelected = shell == selectedShell
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isSelected) Color(0xFF282B33) else Color(0xFF181A20))
                                                .border(
                                                    1.dp,
                                                    if (isSelected) Color(0xFFE5A00D) else Color(0xFF262832),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .clickable {
                                                    mechSounds.keyDown()
                                                    selectedShell = shell
                                                }
                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = shell.displayName,
                                                color = if (isSelected) Color(0xFFE5A00D) else Color(0xFF90949E),
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Now Playing Card (matches mobile view; click to jump back to album)
                            val canJumpNowPlaying = activeSource == DesktopSource.PLEX && currentAlbum != null && activeTrackUrl != null
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF141619))
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                    .clickable(enabled = canJumpNowPlaying) {
                                        currentAlbum?.let { alb ->
                                            mechSounds.keyDown()
                                            navigateToAlbumRequest = alb to (++navCounter)
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Album Art Square
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Brush.linearGradient(listOf(ui.label.stripe, ui.label.stripe2))),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    albumArtwork?.let { bmp ->
                                        Image(bmp, "Album art", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    } ?: Text("♪", color = Color.White.copy(alpha = 0.75f), fontSize = 20.sp)
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (hasActivePlayback) effectiveTitle else "No tape playing",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val byLine = if (hasActivePlayback) {
                                        listOf(effectiveArtist, effectiveAlbum).filter { it.isNotBlank() }.joinToString(" · ")
                                    } else {
                                        "Select a track or album from your library"
                                    }
                                    Text(
                                        text = byLine,
                                        color = Color(0xFF8E93A0),
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val dispPos = if (hasActivePlayback) positionMs else 0L
                                        val dispDur = if (hasActivePlayback && effectiveDurMs > 0L) effectiveDurMs else 0L
                                        Text(
                                            text = "${formatTimeMs(dispPos)} / ${formatTimeMs(dispDur)}",
                                            color = Color(0xFFA0A4AE),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                        Text(
                                            text = buildString {
                                                append(
                                                    when {
                                                        currentDevice.type != DeviceType.MAC -> currentDevice.name.uppercase()
                                                        activeSource == DesktopSource.LOCAL -> "LOCAL"
                                                        else -> "PLEX"
                                                    }
                                                )
                                                if (dbbEnabled) append(" · DBB")
                                                if (avlsEnabled) append(" · AVLS")
                                            },
                                            color = Color(0xFFE5A00D),
                                            fontSize = 9.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    val progressFraction = if (hasActivePlayback && effectiveDurMs > 0L) {
                                        (positionMs.toFloat() / effectiveDurMs.toFloat()).coerceIn(0f, 1f)
                                    } else 0f
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color.White.copy(alpha = 0.12f)),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(progressFraction)
                                                .fillMaxHeight()
                                                .background(Color(0xFFE5A00D)),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Spotify Connect Device Picker Dialog
            if (showDeviceDialog) {
                ConnectToDeviceDialog(
                    deviceService = deviceService,
                    currentVolume = volume,
                    onVolumeChange = {
                        volume = it
                        audioBackend.setVolume(it)
                    },
                    onDismiss = { showDeviceDialog = false }
                )
            }

            // Plex Connection & Unified Settings Dialog
            if (showPlexDialog) {
                PlexConnectionDialog(
                    authService = authService,
                    currentConfig = savedConfig,
                    keySoundsEnabled = keySoundsEnabled,
                    deviceService = deviceService,
                    activeSourceLabel = activeSource.label,
                    onSelectSourceLabel = { label ->
                        DesktopSource.entries.firstOrNull { it.label == label }?.let(::selectDesktopSource)
                    },
                    localFolderPath = localFolder?.absolutePath,
                    onPickLocalFolder = {
                        mechSounds.keyDown()
                        DesktopLocalLibrary.pickFolder()?.let { chosen ->
                            DesktopLocalLibrary.saveFolder(chosen)
                            localFolder = chosen
                            selectDesktopSource(DesktopSource.LOCAL)
                        }
                    },
                    onToggleKeySounds = { updateKeySounds(it) },
                    onConnected = { newConfig ->
                        savedConfig = newConfig
                    },
                    onDisconnect = {
                        authService.clearConfig()
                        savedConfig = null
                        isSonicDjActive = false
                        isHolidaySonicSession = false
                        audioBackend.stop()
                        activeTrackUrl = null
                    },
                    onDismiss = { showPlexDialog = false }
                )
            }
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%02d:%02d".format(min, sec)
}
