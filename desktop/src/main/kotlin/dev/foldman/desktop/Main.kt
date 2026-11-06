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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clipToBounds
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
import dev.foldman.ui.EngravedCaps
import dev.foldman.ui.FULL_CASSETTE_ASPECT
import dev.foldman.ui.FullCassette
import dev.foldman.ui.KeySymbol
import dev.foldman.ui.LocalHaptics
import dev.foldman.ui.LocalMechSounds
import dev.foldman.ui.MiniButton
import dev.foldman.ui.PhonesJack
import dev.foldman.ui.Rx707Header
import dev.foldman.ui.SlideSwitch
import dev.foldman.ui.TAPE_WINDOW_ASPECT
import dev.foldman.ui.TapeCounter
import dev.foldman.ui.TapeWindow
import dev.foldman.ui.TransportKeys
import dev.foldman.ui.VolumeSlider
import dev.foldman.ui.bezel
import dev.foldman.ui.chassis
import dev.foldman.ui.paintedBody
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
        title = "Tapeman · Sony Walkman WM-RX707 Desktop",
        state = rememberWindowState(width = 1180.dp, height = 790.dp),
    ) {
        MaterialTheme {
            TapemanDesktopApp(mechSounds, audioBackend, authService, plexClient)
        }
    }
}

enum class DeckViewMode {
    STADIUM_WINDOW,
    EXPOSED_CASSETTE
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
    var volume by remember { mutableFloatStateOf(1f) }

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
    var viewMode by remember { mutableStateOf(DeckViewMode.EXPOSED_CASSETTE) }

    // Track & Queue metadata
    var trackTitle by remember { mutableStateOf("Automatic Music Sensor") }
    var trackArtist by remember { mutableStateOf("Sony Walkman WM-RX707") }
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
        hasNext = hasNext,
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
                // Top Master Navigation Bar (Plex library switcher, mode toggles, unified settings)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Vintage Sony Walkman Typography
                    Column {
                        Text(
                            text = "SONY WALKMAN WM-RX707",
                            color = Color(0xFFD4AF37),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = "STEREO CASSETTE PLAYER · LOGIC CONTROL · AUTO REVERSE",
                            color = Color(0xFF888890),
                            fontSize = 9.5.sp,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    // Right: Global Navigation & Settings
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Library Toggle
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

                        // View Mode Switcher: Exposed Cassette vs Stadium Door
                        Row(
                            modifier = Modifier
                                .background(Color(0xFF191A1E), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFF2C2E35), RoundedCornerShape(6.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ViewModeButton(
                                text = "Exposed Tape",
                                selected = viewMode == DeckViewMode.EXPOSED_CASSETTE,
                                onClick = {
                                    mechSounds.keyDown()
                                    viewMode = DeckViewMode.EXPOSED_CASSETTE
                                }
                            )
                            ViewModeButton(
                                text = "Walkman Window",
                                selected = viewMode == DeckViewMode.STADIUM_WINDOW,
                                onClick = {
                                    mechSounds.keyDown()
                                    viewMode = DeckViewMode.STADIUM_WINDOW
                                }
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

                // Main Content Workspace: Plex Browser + Walkman Hardware Deck
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
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
                                .weight(0.38f)
                                .fillMaxHeight()
                        )
                    }

                    // Right Pane: The Sculpted Sony Walkman WM-RX707 Hardware Player Deck
                    Row(
                        modifier = Modifier
                            .weight(if (showLibraryBrowser) 0.62f else 1f)
                            .fillMaxHeight()
                            .paintedBody(DeckColors.Body, corner = 20.dp, framed = true, tactileDots = true),
                    ) {
                        // 1. Center Cassette Compartment & Front Controls
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Silk-screened WM-RX707 Header
                            Rx707Header(compact = true)

                            Spacer(Modifier.height(8.dp))

                            // Authentic Vintage Cassette Archive Selector (All 7 vintage shells)
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(CassetteShell.entries) { shell ->
                                    val isSelected = shell == selectedShell
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isSelected) Color(0xFF2A2D34) else Color(0xFF141619),
                                                RoundedCornerShape(5.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) Color(0xFFE5A00D) else Color(0xFF24262C),
                                                RoundedCornerShape(5.dp)
                                            )
                                            .clickable {
                                                mechSounds.keyDown()
                                                selectedShell = shell
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = shell.displayName,
                                                color = if (isSelected) Color(0xFFE5A00D) else Color(0xFFD0D0D5),
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            )
                                            Text(
                                                text = shell.tapeType.substringBefore(" ·"),
                                                color = Color(0xFF707078),
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace,
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Main Cassette Well Area
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clipToBounds(),
                                contentAlignment = Alignment.Center
                            ) {
                                when (viewMode) {
                                    DeckViewMode.EXPOSED_CASSETTE -> {
                                        FullCassette(
                                            ui = ui,
                                            anim = anim,
                                            stationary = false,
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .aspectRatio(FULL_CASSETTE_ASPECT, matchHeightConstraintsFirst = true)
                                        )
                                    }
                                    DeckViewMode.STADIUM_WINDOW -> {
                                        TapeWindow(
                                            ui = ui,
                                            anim = anim,
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .aspectRatio(TAPE_WINDOW_ASPECT, matchHeightConstraintsFirst = true)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Tactile Progress Bar & Timestamps
                            Row(
                                modifier = Modifier.fillMaxWidth(0.96f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatTime(positionMs),
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Spacer(Modifier.width(8.dp))
                                Slider(
                                    value = ui.progress,
                                    onValueChange = { p ->
                                        if (activeTrackUrl != null && durationMs > 0) {
                                            val newMs = (p * durationMs).toLong()
                                            audioBackend.seekTo(newMs)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFE5A00D),
                                        activeTrackColor = Color(0xFFE5A00D),
                                        inactiveTrackColor = Color(0xFF282A30)
                                    )
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = formatTime(durationMs),
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            // Authentic Sculpted 3D Spun-Chrome Transport Keys & AMS Skips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                MiniButton(
                                    caption = "AMS",
                                    onPress = { playPrevious() },
                                    symbol = KeySymbol.PREV,
                                    diameter = 32.dp,
                                    captionColor = DeckColors.EngraveLight
                                )

                                Spacer(Modifier.width(10.dp))

                                TransportKeys(
                                    ui = ui,
                                    onStop = {
                                        mechSounds.autoStop()
                                        audioBackend.stop()
                                    },
                                    onRewind = {
                                        mechSounds.keyDown()
                                        if (activeTrackUrl != null) {
                                            val newPos = (audioPosMs - 10000L).coerceAtLeast(0L)
                                            audioBackend.seekTo(newPos)
                                        }
                                    },
                                    onPlay = {
                                        mechSounds.keyDown()
                                        if (activeTrackUrl != null) {
                                            audioBackend.resume()
                                        }
                                    },
                                    onFastForward = {
                                        mechSounds.keyDown()
                                        if (activeTrackUrl != null) {
                                            val newPos = (audioPosMs + 10000L).coerceAtMost(durationMs)
                                            audioBackend.seekTo(newPos)
                                        }
                                    },
                                    onPause = {
                                        mechSounds.keyUp()
                                        audioBackend.pause()
                                    },
                                    modifier = Modifier.weight(1f),
                                    keyHeight = 56.dp
                                )

                                Spacer(Modifier.width(10.dp))

                                MiniButton(
                                    caption = "AMS",
                                    onPress = { playNext() },
                                    symbol = KeySymbol.NEXT,
                                    diameter = 32.dp,
                                    enabled = hasNext,
                                    captionColor = DeckColors.EngraveLight
                                )
                            }
                        }

                        // 2. Right Brushed Titanium Hardware Rim (Counter, Switches, Volume, Phones)
                        Column(
                            modifier = Modifier
                                .width(128.dp)
                                .fillMaxHeight()
                                .padding(6.dp)
                                .chassis(corner = 16.dp, screws = true)
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            // Mechanical 3-digit rolling tape counter
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.bezel().padding(4.dp)) {
                                    TapeCounter(anim.reels, digitWidth = 12.dp)
                                }
                                Spacer(Modifier.height(4.dp))
                                MiniButton(
                                    caption = "RESET",
                                    onPress = {
                                        mechSounds.keyUp()
                                        anim.reels.resetCounter()
                                    },
                                    diameter = 22.dp,
                                    captionColor = DeckColors.Engrave
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            // Hardware Slide Switches: EX DBB and CLICK (Sound Effects Toggle)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                SlideSwitch(
                                    on = dbbEnabled,
                                    onToggle = { dbbEnabled = it },
                                    caption = "EX DBB",
                                    height = 46.dp
                                )
                                SlideSwitch(
                                    on = keySoundsEnabled,
                                    onToggle = { updateKeySounds(it) },
                                    caption = "CLICK",
                                    height = 46.dp
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            // Vertical Knurled Thumbwheel Volume Slider
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("VOLUME", style = EngravedCaps.copy(fontSize = 7.5.sp, color = DeckColors.Engrave))
                                Spacer(Modifier.height(2.dp))
                                VolumeSlider(
                                    volume = volume,
                                    onVolume = {
                                        volume = it
                                        audioBackend.setVolume(it)
                                    },
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            // Headphone socket
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PhonesJack(Modifier.size(18.dp))
                                Spacer(Modifier.height(3.dp))
                                Text("PHONES", style = EngravedCaps.copy(fontSize = 6.sp, color = DeckColors.Engrave))
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

@Composable
fun ViewModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) Color(0xFFE5A00D) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = if (selected) Color.Black else Color(0xFF909096),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
