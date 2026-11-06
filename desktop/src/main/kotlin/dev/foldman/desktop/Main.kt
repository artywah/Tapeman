package dev.foldman.desktop

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import dev.foldman.desktop.audio.DesktopMechSounds
import dev.foldman.desktop.audio.JavaFxAudioBackend
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
    val audioBackend = remember { JavaFxAudioBackend() }
    val authService = remember { PlexAuthService() }
    val plexClient = remember { DesktopPlexClient() }

    val windowState = rememberWindowState(width = 980.dp, height = 620.dp)
    var isMiniPlayer by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = {
            audioBackend.release()
            mechSounds.release()
            exitApplication()
        },
        title = if (isMiniPlayer) "Tapeman" else "Tapeman · Retro Stereo Cassette Player",
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
                        windowState.size = DpSize(540.dp, 390.dp)
                    } else {
                        windowState.size = DpSize(980.dp, 620.dp)
                    }
                }
            )
        }
    }
}

@Composable
fun TapemanDesktopApp(
    mechSounds: DesktopMechSounds,
    audioBackend: JavaFxAudioBackend,
    authService: PlexAuthService,
    plexClient: DesktopPlexClient,
    isMiniPlayer: Boolean = false,
    onToggleMiniPlayer: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    var savedConfig by remember { mutableStateOf(authService.loadConfig()) }
    var showPlexDialog by remember { mutableStateOf(false) }
    var showLibraryBrowser by remember { mutableStateOf(true) }

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

        mechSounds.keyDown()
        slideTapeIn()
        audioBackend.play(track.uri)

        scope.launch {
            albumArtwork = plexClient.loadArtwork(track.coverUri ?: album.coverUrl)
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

                        // Right: Prominent "⤢ Open Library" Button + Settings Icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFFE5A00D))
                                    .clickable {
                                        mechSounds.keyDown()
                                        onToggleMiniPlayer()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "⤢ Open Library",
                                    color = Color(0xFF141518),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF1E2026))
                                    .border(1.dp, Color(0xFF333842), RoundedCornerShape(5.dp))
                                    .clickable {
                                        mechSounds.keyDown()
                                        showPlexDialog = true
                                    }
                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                            ) {
                                Text("⚙", color = Color(0xFFDFDFE5), fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Center: The Walkman Deck, scaled strictly to HEIGHT so it NEVER cuts off the bottom or screws
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
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
                                    } else if (currentQueue.isNotEmpty()) {
                                        currentAlbum?.let { playPlexTrack(currentQueue[currentTrackIndex], it, currentQueue, currentTrackIndex) }
                                    }
                                }
                            },
                            onStop = {
                                mechSounds.stop()
                                audioBackend.stop()
                            },
                            onRewind = {
                                mechSounds.rewind()
                                if (activeTrackUrl != null) {
                                    val newPos = (audioPosMs - 10000L).coerceAtLeast(0L)
                                    audioBackend.seekTo(newPos)
                                }
                            },
                            onFastForward = {
                                mechSounds.fastForward()
                                if (activeTrackUrl != null && durationMs > 0L) {
                                    val newPos = (audioPosMs + 10000L).coerceAtMost(durationMs)
                                    audioBackend.seekTo(newPos)
                                }
                            },
                            onPrevious = { playPrevious() },
                            onNext = { playNext() },
                            onSetVolume = { v ->
                                volume = v
                                audioBackend.setVolume(v)
                            },
                            onToggleDbb = { enabled ->
                                dbbEnabled = enabled
                                mechSounds.switchFlip()
                            },
                            onToggleAvls = { enabled ->
                                avlsEnabled = enabled
                                mechSounds.switchFlip()
                            },
                            onCycleShell = {
                                val shells = CassetteShell.entries
                                val nextIdx = (selectedShell.ordinal + 1) % shells.size
                                selectedShell = shells[nextIdx]
                                mechSounds.solenoid()
                            },
                            onToggleHold = { locked ->
                                holdLocked = locked
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
                        .padding(horizontal = 12.dp, vertical = 8.dp)
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
                        // Left: Server / Library Status Breadcrumb
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
                                text = if (savedConfig != null) {
                                    "${savedConfig?.serverName ?: "Plex"} · ${savedConfig?.libraryName ?: "Music"}".uppercase()
                                } else {
                                    "STANDALONE MODE"
                                },
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = Color(0xFF8E93A0),
                                ),
                            )
                        }

                        // Right: Mini Player, Library Toggle, Settings
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("⚙ Settings", color = Color(0xFFDFDFE5), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    if (savedConfig != null) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .background(Color(0xFF4CAF50), CircleShape)
                                        )
                                    }
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
                        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left Pane: Plex Music Browser (retractable)
                        if (showLibraryBrowser) {
                            PlexMusicBrowser(
                                client = plexClient,
                                currentConfig = savedConfig,
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
                                onOpenConnectDialog = { showPlexDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }

                        // Right Pane: Authentic Sony Walkman WM-RX707 Hardware Player Unit
                        Column(
                            modifier = if (showLibraryBrowser) Modifier.weight(1.15f).fillMaxHeight() else Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
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
                                            } else if (currentQueue.isNotEmpty()) {
                                                currentAlbum?.let { playPlexTrack(currentQueue[currentTrackIndex], it, currentQueue, currentTrackIndex) }
                                            }
                                        }
                                    },
                                    onStop = {
                                        mechSounds.stop()
                                        audioBackend.stop()
                                    },
                                    onRewind = {
                                        mechSounds.rewind()
                                        if (activeTrackUrl != null) {
                                            val newPos = (audioPosMs - 10000L).coerceAtLeast(0L)
                                            audioBackend.seekTo(newPos)
                                        }
                                    },
                                    onFastForward = {
                                        mechSounds.fastForward()
                                        if (activeTrackUrl != null && durationMs > 0L) {
                                            val newPos = (audioPosMs + 10000L).coerceAtMost(durationMs)
                                            audioBackend.seekTo(newPos)
                                        }
                                    },
                                    onPrevious = { playPrevious() },
                                    onNext = { playNext() },
                                    onSetVolume = { v ->
                                        volume = v
                                        audioBackend.setVolume(v)
                                    },
                                    onToggleDbb = { enabled ->
                                        dbbEnabled = enabled
                                        mechSounds.switchFlip()
                                    },
                                    onToggleAvls = { enabled ->
                                        avlsEnabled = enabled
                                        mechSounds.switchFlip()
                                    },
                                    onCycleShell = {
                                        val shells = CassetteShell.entries
                                        val nextIdx = (selectedShell.ordinal + 1) % shells.size
                                        selectedShell = shells[nextIdx]
                                        mechSounds.solenoid()
                                    },
                                    onToggleHold = { locked ->
                                        holdLocked = locked
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

                            Spacer(Modifier.height(10.dp))

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

            // Plex Connection & Unified Settings Dialog
            if (showPlexDialog) {
                PlexConnectionDialog(
                    authService = authService,
                    currentConfig = savedConfig,
                    keySoundsEnabled = keySoundsEnabled,
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
