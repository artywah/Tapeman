package dev.foldman.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowLayoutInfo
import dev.foldman.deck.DeckEvent
import dev.foldman.deck.DeckUi
import dev.foldman.deck.DeckViewModel
import dev.foldman.deck.Source
import kotlinx.coroutines.launch

private enum class Posture { Cover, Unfolded, Tabletop }

/**
 * Picks a layout from the hinge & screen geometry (tuned for the Galaxy Z Fold 8 Ultra):
 *  - folded (cover screen, 21:9): the Sony Walkman WM-RX707 laid out horizontally edge-to-edge;
 *  - unfolded (8.0" inner display): the WM-RX707 deck with the library above and now-playing below;
 *  - half-open on a table (Flex mode): the deck on the upper half, the library on the lower.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldmanScreen(
    vm: DeckViewModel,
    windowLayout: WindowLayoutInfo?,
    onPickFolder: () -> Unit,
    onInstallApp: (Source) -> Unit,
    onAppleSignIn: () -> Unit,
    onSpotifySignIn: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val anim = rememberDeckAnimations(ui.transport, ui.progress)
    val sounds = remember { MechSounds() }
    DisposableEffect(sounds) { onDispose { sounds.release() } }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var shelfOpen by rememberSaveable { mutableStateOf(false) }
    val feel by rememberUpdatedState(ui)

    // A new tape drops into the deck whenever the source changes (and when the app opens).
    LaunchedEffect(ui.source, ui.plex.loadedAlbumTitle) {
        anim.insert.snapTo(1f)
        anim.insert.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 220f))
        if (feel.keySounds) sounds.autoStop()
    }
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            if (feel.keySounds) sounds.autoStop()
            when (e) {
                DeckEvent.TapeEnd -> if (feel.haptics) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                DeckEvent.Eject -> {
                    if (feel.haptics) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    scope.launch {
                        anim.pop.animateTo(1f, tween(90))
                        anim.pop.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 500f))
                    }
                    shelfOpen = true
                }
            }
        }
    }

    val appleActions = remember(vm) {
        AppleActions(
            signIn = onAppleSignIn,
            signOut = vm::signOutOfApple,
            setDeveloperToken = vm::setAppleDeveloperToken,
            refresh = vm::loadApplePlaylists,
            openPlaylist = vm::openApplePlaylist,
            closePlaylist = vm::closeApplePlaylist,
            play = vm::playApplePlaylist,
        )
    }
    val plexActions = remember(vm) {
        PlexActions(
            setTab = vm::setPlexTab,
            setSearch = vm::setPlexSearch,
            setLetter = vm::setPlexLetter,
            refresh = vm::loadPlexItems,
            openAlbum = vm::openPlexAlbum,
            closeAlbum = vm::closePlexAlbum,
            playAlbum = { album, idx ->
                vm.playPlexAlbum(album, idx)
                shelfOpen = false
            },
            toggleSettings = vm::togglePlexSettings,
            saveSettings = vm::savePlexSettings,
        )
    }
    val spotifyActions = remember(vm) {
        SpotifyActions(
            signIn = onSpotifySignIn,
            signOut = vm::signOutOfSpotify,
            toggleEditClient = vm::toggleSpotifyClientEdit,
            saveConfig = vm::saveSpotifyConfig,
            setTab = vm::setSpotifyTab,
            refresh = vm::loadSpotifyLibrary,
            openCollection = vm::openSpotifyCollection,
            closeCollection = vm::closeSpotifyCollection,
            play = { idx ->
                vm.playSpotifyCollection(idx)
                shelfOpen = false
            },
        )
    }
    val shelfActions = remember(vm) {
        ShelfActions(
            selectSource = vm::selectSource,
            pickFolder = onPickFolder,
            playTrack = {
                vm.playTrack(it)
                shelfOpen = false
            },
            openAccess = vm::openAccessSettings,
            openApp = vm::openSourceApp,
            installApp = onInstallApp,
            apple = appleActions,
            plex = plexActions,
            spotify = spotifyActions,
        )
    }
    val deck = remember(vm) { DeckControls(vm) }
    val fold = windowLayout?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull()

    CompositionLocalProvider(
        LocalMechSounds provides sounds.takeIf { ui.keySounds },
        LocalHaptics provides ui.haptics,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(Color(0xFF1F2126), DeckColors.Backdrop), radius = 1800f)),
        ) {
            val density = LocalDensity.current
            val sheetHeight = maxHeight * 0.84f
            val landscape = maxWidth > maxHeight
            // On Galaxy Z Fold 8 Ultra: cover screen shortest edge is ~393dp; unfolded shortest edge is ~820dp.
            val posture = when {
                fold != null && fold.state == FoldingFeature.State.HALF_OPENED && fold.orientation == FoldingFeature.Orientation.HORIZONTAL -> Posture.Tabletop
                fold != null && fold.orientation == FoldingFeature.Orientation.VERTICAL -> Posture.Unfolded
                minOf(maxWidth, maxHeight) >= 560.dp -> Posture.Unfolded
                else -> Posture.Cover
            }
            LaunchedEffect(posture) { view.showSystemBars(posture != Posture.Cover) }
            when (posture) {
                Posture.Cover -> CoverWalkman(
                    ui = ui,
                    anim = anim,
                    deck = deck,
                    vm = vm,
                    onTapes = { shelfOpen = true },
                    modifier = Modifier.fillMaxSize(),
                )
                Posture.Unfolded -> Column(
                    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LibraryBand(ui, shelfActions, vm::playQueueItem, Modifier.fillMaxWidth().weight(if (landscape) 0.32f else 0.28f))
                    DeckBand(ui, anim, deck, vm, onTapes = { shelfOpen = true }, Modifier.fillMaxWidth().weight(if (landscape) 0.44f else 0.46f))
                    NowPlayingBand(ui, vm::playQueueItem, Modifier.fillMaxWidth().weight(if (landscape) 0.24f else 0.26f))
                }
                Posture.Tabletop -> {
                    val (topH, gap) = with(density) {
                        fold!!.bounds.top.toDp() to (fold.bounds.bottom - fold.bounds.top).toDp()
                    }
                    Column(Modifier.fillMaxSize()) {
                        DeckBand(
                            ui, anim, deck, vm, onTapes = { shelfOpen = true },
                            Modifier
                                .height(topH)
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                        )
                        Spacer(Modifier.height(gap))
                        LibraryBand(
                            ui, shelfActions, vm::playQueueItem,
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                        )
                    }
                }
            }
            if (shelfOpen && posture == Posture.Cover) {
                ModalBottomSheet(
                    onDismissRequest = { shelfOpen = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = Color(0xFF141518),
                ) {
                    TapeShelf(
                        ui,
                        shelfActions,
                        Modifier.fillMaxWidth().height(sheetHeight),
                    )
                }
            }
        }
    }
}

/** Hides the status and navigation bars (a swipe from the edge brings them back briefly), or restores them. */
private fun android.view.View.showSystemBars(show: Boolean) {
    var ctx = context
    while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
    val window = (ctx as? android.app.Activity)?.window ?: return
    WindowCompat.getInsetsController(window, this).run {
        if (show) {
            show(WindowInsetsCompat.Type.systemBars())
        } else {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/** Key actions, bundled so the layouts don't each need the view model. */
private class DeckControls(vm: DeckViewModel) {
    val play = vm::play
    val playToggle = vm::playOrTogglePause
    val pause = vm::pauseKey
    val stop = vm::stop
    val rewind = vm::rewind
    val fastForward = vm::fastForward
    val next = vm::next
    val previous = vm::previous
    val volume = vm::setVolume
}

/**
 * The folded phone (21:9 Galaxy Z Fold 8 Ultra cover screen):
 * Rendered sideways in landscape so the Sony Walkman WM-RX707 fills the entire cover screen
 * with its horizontal smoked stadium window, curved hammer-tone gunmetal logic control pod,
 * and brushed titanium edge control bays.
 */
@Composable
private fun CoverWalkman(
    ui: DeckUi,
    anim: DeckAnimations,
    deck: DeckControls,
    vm: DeckViewModel,
    onTapes: () -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        // Automatically rotate 90° clockwise when the cover screen is portrait-locked so holding
        // the closed phone sideways always shows the full-bleed horizontal WM-RX707.
        val isPortraitWindow = maxHeight > maxWidth
        Row(
            Modifier
                .fillMaxSize()
                .quarterTurn(enabled = isPortraitWindow, clockwise = true)
                .paintedBody(DeckColors.Body, corner = 0.dp, framed = false, tactileDots = true),
        ) {
            // 1. Left Brushed Titanium Hardware Rim (OPEN latch, AMS skip, EX DBB, REC/SHELL button)
            Column(
                Modifier
                    .weight(0.15f)
                    .fillMaxHeight()
                    .chassis(corner = 0.dp, screws = true)
                    .padding(horizontal = 8.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Rx707OpenLatch(onClick = onTapes)

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    MiniButton(
                        "AMS",
                        onPress = { if (!ui.holdLocked) deck.previous() },
                        symbol = KeySymbol.PREV,
                        diameter = 28.dp,
                        captionColor = DeckColors.Engrave,
                    )
                    MiniButton(
                        "AMS",
                        onPress = { if (!ui.holdLocked) deck.next() },
                        symbol = KeySymbol.NEXT,
                        diameter = 28.dp,
                        enabled = ui.hasNext,
                        captionColor = DeckColors.Engrave,
                    )
                }

                SlideSwitch(
                    on = ui.dbbEnabled,
                    onToggle = vm::setDbbEnabled,
                    caption = "EX DBB",
                    onLabel = "ON",
                    offLabel = "NORM",
                    height = 50.dp,
                )

                Rx707RecButton(
                    active = ui.transparentShell,
                    onClick = { vm.setTransparentShell(!ui.transparentShell) },
                )
            }

            // 2. Center Main Matte-Black Anodized WM-RX707 Faceplate
            Box(
                Modifier
                    .weight(0.71f)
                    .fillMaxHeight()
                    .padding(top = 12.dp, start = 18.dp, end = 18.dp),
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Rx707Header()
                    Spacer(Modifier.height(6.dp))
                    // Wide horizontal smoked stadium window
                    Box(
                        Modifier
                            .fillMaxWidth(0.95f)
                            .weight(1f)
                            .padding(bottom = 66.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        TapeWindow(
                            ui = ui,
                            anim = anim,
                            vertical = false,
                            modifier = Modifier.fillMaxHeight(),
                        )
                    }
                }

                // Curved Hammer-Tone Gunmetal Logic Control Pod rising from the bottom edge
                // and overlapping the lower curve of the cassette window, just like the real WM-RX707!
                Rx707LogicPod(
                    ui = ui,
                    onRewind = deck.rewind,
                    onFastForward = deck.fastForward,
                    onStop = deck.stop,
                    onPlayToggle = deck.playToggle,
                    onToggleHold = vm::setHoldLocked,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(0.86f)
                        .fillMaxHeight(0.49f),
                )
            }

            // 3. Right Brushed Titanium Edge Strip (Mechanical Counter, Knurled Volume, AVLS, Remote/Phones)
            Column(
                Modifier
                    .weight(0.14f)
                    .fillMaxHeight()
                    .chassis(corner = 0.dp, screws = true)
                    .padding(horizontal = 8.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.bezel().padding(4.dp)) {
                        TapeCounter(anim.reels, digitWidth = 13.dp)
                    }
                    MiniButton(
                        "000",
                        onPress = { anim.reels.resetCounter() },
                        diameter = 20.dp,
                        captionColor = DeckColors.Engrave,
                    )
                }
                Spacer(Modifier.height(8.dp))
                SlideSwitch(
                    on = ui.avlsEnabled,
                    onToggle = vm::setAvlsEnabled,
                    caption = "AVLS",
                    onLabel = "LIM",
                    offLabel = "NORM",
                    height = 46.dp,
                )
                Spacer(Modifier.height(8.dp))
                Text("VOLUME", style = EngravedCaps.copy(fontSize = 7.sp, color = DeckColors.Engrave))
                VolumeSlider(ui.volume, deck.volume, Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(6.dp))
                PhonesJack(Modifier.size(16.dp))
                Text("REMOTE · PHONES", style = EngravedCaps.copy(fontSize = 5.5.sp, color = DeckColors.Engrave))
            }
        }
    }
}

/** The unfolded phone: the Sony WM-RX707 laid down as a full stereo cassette deck. */
@Composable
private fun DeckBand(
    ui: DeckUi,
    anim: DeckAnimations,
    deck: DeckControls,
    vm: DeckViewModel,
    onTapes: () -> Unit,
    modifier: Modifier,
) {
    Row(modifier.paintedBody(DeckColors.Body, corner = 22.dp, tactileDots = true)) {
        Column(Modifier.weight(0.73f).fillMaxHeight().padding(horizontal = 18.dp, vertical = 12.dp)) {
            Rx707Header(compact = true)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(152.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                    Row(Modifier.fillMaxWidth().weight(1f).bezel().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        VuMeter(ui.transport, ui.source, Modifier.weight(1f).fillMaxHeight(), channel = 0)
                        VuMeter(ui.transport, ui.source, Modifier.weight(1f).fillMaxHeight(), channel = 1)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.bezel().padding(5.dp)) { TapeCounter(anim.reels, digitWidth = 15.dp) }
                        Spacer(Modifier.width(8.dp))
                        MiniButton("RESET", { anim.reels.resetCounter() }, diameter = 20.dp)
                        Spacer(Modifier.width(6.dp))
                        StatusLed(ui.transport, caption = "REC/OP")
                    }
                }
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    TapeWindow(ui, anim)
                }
                Spacer(Modifier.width(10.dp))
                EjectArrow(onClick = onTapes, Modifier.size(26.dp, 48.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                MiniButton("AMS", deck.previous, symbol = KeySymbol.PREV, diameter = 30.dp)
                Spacer(Modifier.width(10.dp))
                TransportKeys(ui, deck.stop, deck.rewind, deck.play, deck.fastForward, deck.pause, Modifier.weight(1f), keyHeight = 56.dp)
                Spacer(Modifier.width(10.dp))
                MiniButton("AMS", deck.next, symbol = KeySymbol.NEXT, diameter = 30.dp, enabled = ui.hasNext)
            }
        }
        // Brushed titanium right hardware bay: switches, EX DBB, AVLS, SHELL toggle, volume and phones.
        Row(
            Modifier
                .weight(0.27f)
                .fillMaxHeight()
                .padding(6.dp)
                .chassis(corner = 18.dp, screws = true)
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                Modifier.weight(1.1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Rx707RecButton(
                    active = ui.transparentShell,
                    onClick = { vm.setTransparentShell(!ui.transparentShell) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SlideSwitch(ui.dbbEnabled, vm::setDbbEnabled, "EX DBB", height = 46.dp)
                    SlideSwitch(ui.avlsEnabled, vm::setAvlsEnabled, "AVLS", height = 46.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SlideSwitch(ui.keySounds, vm::setKeySounds, "CLICK", height = 46.dp)
                    SlideSwitch(ui.haptics, vm::setHaptics, "HAPTIC", height = 46.dp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PhonesJack(Modifier.size(16.dp))
                    Text("PHONES", style = EngravedCaps.copy(fontSize = 6.sp))
                }
            }
            Column(Modifier.weight(0.9f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("VOLUME", style = EngravedCaps.copy(fontSize = 7.sp))
                VolumeSlider(ui.volume, deck.volume, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}
