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
        title = "Tapeman · Retro Stereo Cassette Player",
        icon = appIcon,
        state = windowState,
        alwaysOnTop = false,
        resizable = true,
    ) {
        MaterialTheme {
            TapemanDesktopApp(
                mechSounds = mechSounds,
                audioBackend = audioBackend,
                authService = authService,
                plexClient = plexClient,
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
    val audioVolume by audioBackend.volume.collectAsState()

    LaunchedEffect(audioVolume) {
        volume = audioVolume
    }

    // Winding state: -1 for REW, +1 for FF, 0 for idle
    var winding by remember { mutableStateOf(0) }
    var windPosMs by remember { mutableStateOf(0L) }
    var wasPlayingBeforeWind by remember { mutableStateOf(false) }
    var windJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val displayPosMs = if (winding != 0) windPosMs else audioPosMs
    val positionMs = if (activeTrackUrl != null) displayPosMs else 46000L
    val durationMs = if (activeTrackUrl != null && audioDurMs > 0L) audioDurMs else 240000L

    // Determine current transport state
    val transport = when {
        winding < 0 -> Transport.REWINDING
        winding > 0 -> Transport.FAST_FORWARDING
        isPlaying -> Transport.PLAYING
        displayPosMs > 0L -> Transport.PAUSED
        else -> Transport.STOPPED
    }

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
                        // Left Pane: Plex Music Browser (always displayed)
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
