package dev.foldman.desktop

import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
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
        resizable = true,
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
                        // Snug mini player matching screenshot 2026-11-07 at 10.54.00 am
                        windowState.size = DpSize(470.dp, 390.dp)
                    } else {
                        // Ideal full view matching screenshot 2026-11-07 at 10.52.39 am
                        windowState.size = DpSize(1200.dp, 590.dp)
                    }
                }
            )
        }
    }
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
    var showPlexDialog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var showLibraryBrowser by remember { mutableStateOf(true) }
    var isSonicDjActive by remember { mutableStateOf(false) }

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
    var albumArtwork by remember { mutableStateOf<ImageBitmap?>(null) }
    var activeTrackUrl by remember { mutableStateOf<String?>(null) }
    var currentQueue by remember { mutableStateOf<List<LocalTrack>>(emptyList()) }
    var currentTrackIndex by remember { mutableIntStateOf(0) }
    var currentAlbum by remember { mutableStateOf<PlexAlbum?>(null) }

    // Audio engine state bindings
    val isPlaying by audioBackend.isPlaying.collectAsState()
    val audioPosMs by audioBackend.currentPositionMs.collectAsState()
    val audioDurMs by audioBackend.durationMs.collectAsState()

    // Determine current transport state
    val transport = when {
        isPlaying -> Transport.PLAYING
        audioPosMs > 0L -> Transport.PAUSED
        else -> Transport.STOPPED
    }

    val positionMs = if (activeTrackUrl != null) audioPosMs else 46000L
    val durationMs = if (activeTrackUrl != null && audioDurMs > 0L) audioDurMs else 240000L

    val hasNext = currentQueue.isNotEmpty() && currentTrackIndex < currentQueue.size - 1
    val hasPrevious = currentQueue.isNotEmpty() && currentTrackIndex > 0

    val ui = DeckUi(
        source = Source.PLEX,
        transport = transport,
        title = trackTitle,
        artist = trackArtist,
        album = albumTitle,
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

    fun playPlexTrack(track: LocalTrack, album: PlexAlbum, queue: List<LocalTrack>, index: Int) {
        trackTitle = track.title
        trackArtist = track.artist
        albumTitle = album.title
        activeTrackUrl = track.uri
        currentQueue = queue
        currentTrackIndex = index
        currentAlbum = album

        audioBackend.setTrackInfo(
            title = track.title,
            artist = track.artist,
            album = album.title,
            coverUrl = track.coverUri ?: album.coverUrl
        )

        mechSounds.keyDown()
        slideTapeIn()
        audioBackend.play(track.uri)

        scope.launch {
            albumArtwork = plexClient.loadArtwork(track.coverUri ?: album.coverUrl)
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

    fun toggleSonicDj() {
        mechSounds.keyDown()
        val next = !isSonicDjActive
        isSonicDjActive = next

        if (next && (currentQueue.isEmpty() || activeTrackUrl == null)) {
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

    fun playPrevious() {
        mechSounds.keyDown()
        if (currentQueue.isNotEmpty() && currentTrackIndex > 0) {
            val prevIdx = currentTrackIndex - 1
            currentAlbum?.let { playPlexTrack(currentQueue[prevIdx], it, currentQueue, prevIdx) }
        } else if (activeTrackUrl != null) {
            audioBackend.seekTo(0L)
        }
    }

    fun playNext() {
        mechSounds.keyDown()
        if (currentQueue.isNotEmpty() && currentTrackIndex < currentQueue.size - 1) {
            val nextIdx = currentTrackIndex + 1
            currentAlbum?.let { playPlexTrack(currentQueue[nextIdx], it, currentQueue, nextIdx) }
        }
    }

    // Auto-advance to next track when song finishes
    LaunchedEffect(isPlaying, audioPosMs, audioDurMs) {
        if (!isPlaying && audioDurMs > 0 && audioPosMs >= audioDurMs - 600L && hasNext) {
            playNext()
        }
    }

    // Sonic DJ dynamic replenishment: when near end of queue, fetch more acoustic tracks
    LaunchedEffect(isSonicDjActive, currentTrackIndex, currentQueue.size) {
        if (isSonicDjActive && currentQueue.isNotEmpty() && currentTrackIndex >= currentQueue.size - 2) {
            val curr = currentQueue.getOrNull(currentTrackIndex) ?: return@LaunchedEffect
            scope.launch {
                val more = plexClient.fetchSonicallySimilarTracks(curr.uri, limit = 10)
                if (more.isNotEmpty()) {
                    val existing = currentQueue.map { it.uri }.toSet()
                    val newItems = more.filter { it.uri !in existing }
                    if (newItems.isNotEmpty()) {
                        currentQueue = currentQueue + newItems
                    }
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
                            if (isSonicDjActive) {
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
                                val newPos = (audioPosMs - 15000L).coerceAtLeast(0L)
                                audioBackend.seekTo(newPos)
                            },
                            onFastForward = {
                                mechSounds.keyUp()
                                val newPos = (audioPosMs + 15000L).coerceAtMost(durationMs)
                                audioBackend.seekTo(newPos)
                            },
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
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(1.52f, matchHeightConstraintsFirst = true),
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
                            // Sonic DJ Active Indicator / Quick Toggle
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

                            // Mini Player Toggle Button
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
                                Text(
                                    text = "▣ Mini Player",
                                    color = Color(0xFFDFDFE5),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Library Browser Toggle
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (showLibraryBrowser) Color(0xFF262830) else Color(0xFF17181C))
                                    .border(1.dp, Color(0xFF333640), RoundedCornerShape(6.dp))
                                    .clickable {
                                        mechSounds.keyDown()
                                        showLibraryBrowser = !showLibraryBrowser
                                    }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (showLibraryBrowser) "Hide Library" else "Show Library",
                                    color = Color(0xFFDFDFE5),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
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
                        // Left Pane: Plex Music Browser (retractable)
                        if (showLibraryBrowser) {
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
                                    val idx = queue.indexOfFirst { it.uri == track.uri }.coerceAtLeast(0)
                                    playPlexTrack(track, album, queue, idx)
                                },
                                onPlayAlbumDirectly = ::playAlbumDirectly,
                                onOpenConnectDialog = { showPlexDialog = true },
                                modifier = Modifier
                                    .weight(1.05f)
                                    .fillMaxHeight()
                            )
                        }

                        // Right Pane: Authentic Retro Walkman Hardware Player Unit
                        Column(
                            modifier = if (showLibraryBrowser) Modifier.weight(0.95f).fillMaxHeight() else Modifier.fillMaxSize(),
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
                                        val newPos = (audioPosMs - 15000L).coerceAtLeast(0L)
                                        audioBackend.seekTo(newPos)
                                    },
                                    onFastForward = {
                                        mechSounds.keyUp()
                                        val newPos = (audioPosMs + 15000L).coerceAtMost(durationMs)
                                        audioBackend.seekTo(newPos)
                                    },
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
                        }
                    }
                }
            }

            // Spotify Connect Device Picker Dialog
            if (showDeviceDialog) {
                ConnectToDeviceDialog(
                    deviceService = deviceService,
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
                    onToggleKeySounds = { updateKeySounds(it) },
                    onConnected = { newConfig ->
                        savedConfig = newConfig
                    },
                    onDisconnect = {
                        authService.clearConfig()
                        savedConfig = null
                        audioBackend.stop()
                        activeTrackUrl = null
                    },
                    onDismiss = { showPlexDialog = false }
                )
            }
        }
    }
}
