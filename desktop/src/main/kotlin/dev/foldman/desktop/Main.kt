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

    Window(
        onCloseRequest = {
            audioBackend.release()
            mechSounds.release()
            exitApplication()
        },
        title = "Tapeman · Retro Stereo Cassette Player",
        state = rememberWindowState(width = 1200.dp, height = 820.dp),
    ) {
        MaterialTheme {
            TapemanDesktopApp(mechSounds, audioBackend, authService, plexClient)
        }
    }
}

@Composable
fun TapemanDesktopApp(
    mechSounds: DesktopMechSounds,
    audioBackend: JavaFxAudioBackend,
    authService: PlexAuthService,
    plexClient: DesktopPlexClient,
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
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top Master Navigation Bar (Arty Tapeman badging, library switcher, shell picker, settings)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Arty Tapeman Custom Header Branding
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ARTY",
                            style = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                letterSpacing = 2.5.sp,
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(Color(0xFFFFFFFF), Color(0xFFD5D9DF), Color(0xFF9EA4AC), Color(0xFFE6E9EE))
                                ),
                            ),
                        )
                        Text(
                            text = "TAPEMAN",
                            style = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                letterSpacing = 1.6.sp,
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(Color(0xFFF6F8FA), Color(0xFFC2C7CE), Color(0xFF9BA0A8))
                                ),
                            ),
                        )
                        Box(
                            modifier = Modifier
                                .border(1.dp, Color(0xFFB8BDC4), RoundedCornerShape(50))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                "AVLS",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 7.5.sp,
                                    letterSpacing = 0.8.sp,
                                    color = Color(0xFFC8CDD4),
                                ),
                            )
                        }
                        Text(
                            "v1.0.0",
                            style = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                letterSpacing = 1.0.sp,
                                color = Color(0xFF888D96),
                            ),
                        )
                        Text(
                            "· STEREO CASSETTE PLAYER",
                            style = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                letterSpacing = 1.5.sp,
                                color = Color(0xFF636770),
                            ),
                        )
                    }

                    // Right: Cassette Archive Pills, Library Toggle, Settings
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Vintage Cassette Archive Selector Pills
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(CassetteShell.entries) { shell ->
                                val isSelected = shell == selectedShell
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) Color(0xFF282B33) else Color(0xFF141518))
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFFE5A00D) else Color(0xFF23252B),
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
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        // Retractable Library Browser Toggle Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (showLibraryBrowser) Color(0xFF262830) else Color(0xFF17181C))
                                .border(1.dp, Color(0xFF333640), RoundedCornerShape(6.dp))
                                .clickable {
                                    mechSounds.keyDown()
                                    showLibraryBrowser = !showLibraryBrowser
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (showLibraryBrowser) "Hide Library" else "Show Library",
                                color = Color(0xFFDFDFE5),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Single Unified Settings Button with connection indicator
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E2026))
                                .border(1.dp, Color(0xFF333842), RoundedCornerShape(6.dp))
                                .clickable {
                                    mechSounds.keyDown()
                                    showPlexDialog = true
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⚙ Settings", color = Color(0xFFDFDFE5), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                if (savedConfig != null) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Color(0xFF4CAF50), CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Main Content Workspace: Plex Browser + Walkman Hardware Player Unit
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
                    // Uses fixed aspect ratio 0.68f (matching real physical Walkman proportions) so it never squishes or compresses
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(0.68f, matchHeightConstraintsFirst = true),
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
                            modifier = Modifier.fillMaxSize(),
                            forcePortrait = true,
                        )
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
