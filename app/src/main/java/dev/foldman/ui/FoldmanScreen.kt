package dev.foldman.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowLayoutInfo
import dev.foldman.deck.DeckEvent
import dev.foldman.deck.DeckUi
import dev.foldman.deck.DeckViewModel
import dev.foldman.deck.Source
import dev.foldman.deck.Transport
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
    var cassetteDoorOpen by rememberSaveable { mutableStateOf(false) }
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
                    cassetteDoorOpen = true
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
            selectSource = {
                vm.selectSource(it)
                cassetteDoorOpen = true
            },
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
            // Subtle launch badge showing version
            var showVersionBadge by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(4500)
                showVersionBadge = false
            }
            // On Galaxy Z Fold 8 Ultra: cover screen shortest edge is ~393dp; unfolded shortest edge is ~820dp.
            val posture = when {
                fold != null && fold.state == FoldingFeature.State.HALF_OPENED && fold.orientation == FoldingFeature.Orientation.HORIZONTAL -> Posture.Tabletop
                fold != null && fold.orientation == FoldingFeature.Orientation.VERTICAL -> Posture.Unfolded
                minOf(maxWidth, maxHeight) >= 560.dp -> Posture.Unfolded
                else -> Posture.Cover
            }
            LaunchedEffect(posture) { view.showSystemBars(posture != Posture.Cover) }
            when (posture) {
                Posture.Cover -> WalkmanPlayerUnit(
                    ui = ui,
                    anim = anim,
                    deck = deck,
                    vm = vm,
                    doorOpen = false,
                    onToggleDoor = { shelfOpen = true },
                    onCloseDoor = {},
                    modifier = Modifier.fillMaxSize(),
                )
                Posture.Unfolded -> Row(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    UnfoldedMusicHub(
                        ui = ui,
                        actions = shelfActions,
                        onPlayQueue = vm::playQueueItem,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    WalkmanPlayerUnit(
                        ui = ui,
                        anim = anim,
                        deck = deck,
                        vm = vm,
                        doorOpen = cassetteDoorOpen,
                        onToggleDoor = {
                            cassetteDoorOpen = !cassetteDoorOpen
                            if (cassetteDoorOpen) sounds.keyDown() else sounds.autoStop()
                        },
                        onCloseDoor = {
                            if (cassetteDoorOpen) {
                                cassetteDoorOpen = false
                                sounds.autoStop()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                Posture.Tabletop -> {
                    val (topH, gap) = with(density) {
                        fold!!.bounds.top.toDp() to (fold.bounds.bottom - fold.bounds.top).toDp()
                    }
                    Column(Modifier.fillMaxSize()) {
                        WalkmanPlayerUnit(
                            ui = ui,
                            anim = anim,
                            deck = deck,
                            vm = vm,
                            doorOpen = cassetteDoorOpen,
                            onToggleDoor = {
                                cassetteDoorOpen = !cassetteDoorOpen
                                if (cassetteDoorOpen) sounds.keyDown() else sounds.autoStop()
                            },
                            onCloseDoor = {
                                if (cassetteDoorOpen) {
                                    cassetteDoorOpen = false
                                    sounds.autoStop()
                                }
                            },
                            modifier = Modifier
                                .height(topH)
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                        )
                        Spacer(Modifier.height(gap))
                        UnfoldedMusicHub(
                            ui = ui,
                            actions = shelfActions,
                            onPlayQueue = vm::playQueueItem,
                            modifier = Modifier
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

            AnimatedVisibility(
                visible = showVersionBadge,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
            ) {
                Surface(
                    color = Color(0xEE16181C),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.2.dp, Color(0x66FFFFFF)),
                    shadowElevation = 10.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(Color(0xFF4CAF50), CircleShape)
                        )
                        Text(
                            "TAPEMAN v0.2.1",
                            style = EngravedCaps.copy(fontSize = 11.sp, color = Color(0xFFF0F3F8), letterSpacing = 1.5.sp),
                        )
                    }
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
 * The Sony Walkman WM-RX707 hardware unit:
 * - Used on the cover screen (folded).
 * - Used on the right half of the unfolded dual-pane view with an interactive animated cassette door (Option C).
 * - Left/top titanium bay: OPEN/CLOSE slide latch, AMS skip buttons, EX DBB switch, REC/SHELL button.
 * - Center matte-black faceplate: SONY header, animated 3D cassette door + exposed cassette chamber, logic pod.
 * - Right/bottom titanium bay: Mechanical tape counter, AVLS switch, volume slider, remote/phones jack.
 */
@Composable
private fun WalkmanPlayerUnit(
    ui: DeckUi,
    anim: DeckAnimations,
    deck: DeckControls,
    vm: DeckViewModel,
    doorOpen: Boolean,
    onToggleDoor: () -> Unit,
    onCloseDoor: () -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        val isPortraitWindow = maxHeight > maxWidth
        Row(
            Modifier
                .fillMaxSize()
                .quarterTurn(enabled = isPortraitWindow, clockwise = true)
                .paintedBody(DeckColors.Body, corner = 18.dp, framed = false, tactileDots = true),
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
                Rx707OpenLatch(
                    onClick = onToggleDoor,
                    label = if (doorOpen) "CLOSE" else "OPEN",
                )

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
            BoxWithConstraints(
                Modifier
                    .weight(0.71f)
                    .fillMaxHeight()
                    .clipToBounds()
                    .padding(top = 12.dp, start = 18.dp, end = 18.dp),
            ) {
                val density = LocalDensity.current
                val faceH = maxHeight
                val faceW = maxWidth
                val doorProgress by animateFloatAsState(
                    targetValue = if (doorOpen) 1f else 0f,
                    animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                    label = "doorProgress",
                )

                // Full-sized cassette fills the interior chamber taking up almost the entire player (~94% width, ~88% height)
                val u = minOf((faceW * 0.94f).value / 100f, (faceH * 0.88f).value / 64f)
                val tapeW = (100f * u).dp
                val tapeH = (64f * u).dp
                val tapeLeft = (faceW - tapeW) / 2f
                val tapeTop = (faceH - tapeH) / 2f
                // Inverted window: tape is rotated 180 so the label is on the left (clear window)
                // and the tape edge (pinch roller / capstan opening) sits on the right (under controls)
                val windowW = (84f * u).dp
                val windowH = (38f * u).dp
                val windowLeft = tapeLeft + (8f * u).dp
                val windowTop = tapeTop + (14.5f * u).dp

                // 1. Interior Chamber Layer
                // Always rendered underneath the door so the full cassette is seated in physical space.
                CassetteChamber(
                    ui = ui,
                    anim = anim,
                    onCloseDoor = onCloseDoor,
                    modifier = Modifier.fillMaxSize(),
                    vertical = false,
                    inverted = true,
                    promptToClose = doorOpen && doorProgress > 0.6f,
                    tapeOffset = DpOffset(tapeLeft, tapeTop),
                    tapeSize = DpSize(tapeW, tapeH),
                    doorProgress = doorProgress,
                )

                // 2. Exterior Cassette Door & Controls Layer
                // Tilts open in 3D around the bottom hinge. Controls (Rx707LogicPod), SONY silkscreen,
                // and stadium window are ALL mounted on the door, so they rotate together with the cover!
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            cameraDistance = 48f * density.density
                            rotationX = -72f * doorProgress
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            shadowElevation = 14.dp.toPx() * doorProgress
                        }
                        .clickable(enabled = doorOpen, onClick = onCloseDoor),
                ) {
                    // A. Solid faceplate with stadium window cutout, casing thickness rim & smoked acrylic lens
                    Canvas(Modifier.fillMaxSize()) {
                        val r = CornerRadius(11.5f * u, 11.5f * u)
                        val windowRect = RoundRect(
                            left = windowLeft.toPx(),
                            top = windowTop.toPx(),
                            right = (windowLeft + windowW).toPx(),
                            bottom = (windowTop + windowH).toPx(),
                            cornerRadius = r,
                        )

                        // Solid matte-black faceplate with cutout for the stadium window
                        val cutoutPath = Path().apply {
                            addRect(Rect(0f, 0f, size.width, size.height))
                            addRoundRect(windowRect)
                            fillType = PathFillType.EvenOdd
                        }
                        drawPath(cutoutPath, Color(0xFF141618))
                        drawPath(
                            cutoutPath,
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.05f), Color.Transparent, Color.Black.copy(alpha = 0.25f)),
                            ),
                        )

                        // Top casing thickness rim along the leading edge (y = 0)
                        val rimH = 14.dp.toPx()
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(Color(0xFF565B66), Color(0xFF33373E), Color(0xFF1B1D21)),
                                0f,
                                rimH,
                            ),
                            topLeft = Offset.Zero,
                            size = Size(size.width, rimH),
                        )
                        // Leading edge highlight chamfer
                        drawLine(
                            color = Color(0xFFE8EEF8).copy(alpha = 0.85f),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 2.dp.toPx(),
                        )
                        drawLine(
                            color = Color(0xFF070809),
                            start = Offset(0f, rimH),
                            end = Offset(size.width, rimH),
                            strokeWidth = 1.2f.dp.toPx(),
                        )

                        // Side flange walls along the inside perimeter (strictly inside 0..size.width)
                        val sideFlangeW = 8.dp.toPx()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF42464F), Color(0xFF1B1D20)),
                                0f,
                                sideFlangeW,
                            ),
                            topLeft = Offset.Zero,
                            size = Size(sideFlangeW, size.height),
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.35f),
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 1.2f.dp.toPx(),
                        )
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF1B1D20), Color(0xFF42464F)),
                                size.width - sideFlangeW,
                                size.width,
                            ),
                            topLeft = Offset(size.width - sideFlangeW, 0f),
                            size = Size(sideFlangeW, size.height),
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.35f),
                            start = Offset(size.width, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.2f.dp.toPx(),
                        )

                        // Latch catch notch in the center of the top casing rim
                        val catchW = 46.dp.toPx()
                        val catchH = 7.5f.dp.toPx()
                        val catchLeft = (size.width - catchW) / 2f
                        drawRoundRect(
                            color = Color(0xFF08090C),
                            topLeft = Offset(catchLeft, 1.dp.toPx()),
                            size = Size(catchW, catchH),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                        // Brass latch pin
                        val pinW = 14.dp.toPx()
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(Color(0xFFF2D884), Color(0xFFA6802C), Color(0xFF6E5216))
                            ),
                            topLeft = Offset((size.width - pinW) / 2f, 2.dp.toPx()),
                            size = Size(pinW, catchH - 3.dp.toPx()),
                            cornerRadius = CornerRadius(1.5f.dp.toPx()),
                        )

                        // Outer bezel trench & metallic rim around the stadium window
                        val rim = 1.6f * u
                        drawRoundRect(
                            color = Color(0xFF070809),
                            topLeft = Offset(windowRect.left, windowRect.top),
                            size = Size(windowRect.width, windowRect.height),
                            cornerRadius = r,
                            style = Stroke(rim * 1.5f),
                        )
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(Color(0xFF101114), Color(0xFF282B30), Color(0xFF181A1E))
                            ),
                            topLeft = Offset(windowRect.left, windowRect.top),
                            size = Size(windowRect.width, windowRect.height),
                            cornerRadius = r,
                            style = Stroke(rim),
                        )

                        // Smoked acrylic glass over the window cutout
                        val inner = RoundRect(
                            left = windowRect.left + rim,
                            top = windowRect.top + rim,
                            right = windowRect.right - rim,
                            bottom = windowRect.bottom - rim,
                            cornerRadius = CornerRadius(r.x - rim, r.y - rim),
                        )
                        val innerPath = Path().apply { addRoundRect(inner) }

                        clipPath(innerPath) {
                            // Smoked acrylic glass tint: lets the cassette underneath show through with dark acrylic tint
                            drawRect(Color(0xFF14171C).copy(alpha = 0.28f))

                            // Moulded inner stadium contour line
                            val cW = windowRect.width * 0.66f
                            val cH = windowRect.height * 0.48f
                            val cL = windowRect.left + (windowRect.width - cW) / 2f
                            val cT = windowRect.top + (windowRect.height - cH) / 2f
                            val cR = CornerRadius(cH / 2f, cH / 2f)
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.16f),
                                topLeft = Offset(cL, cT),
                                size = Size(cW, cH),
                                cornerRadius = cR,
                                style = Stroke(1.1f * u * 0.25f),
                            )
                            drawRoundRect(
                                color = Color.Black.copy(alpha = 0.28f),
                                topLeft = Offset(cL + 1f, cT + 1f),
                                size = Size(cW - 2f, cH - 2f),
                                cornerRadius = cR,
                                style = Stroke(0.8f * u * 0.25f),
                            )

                            // Soft diagonal acrylic light glare reflection
                            drawPath(
                                Path().apply {
                                    moveTo(windowRect.left + windowRect.width * 0.46f, windowRect.top)
                                    lineTo(windowRect.left + windowRect.width * 0.66f, windowRect.top)
                                    lineTo(windowRect.left + windowRect.width * 0.42f, windowRect.bottom)
                                    lineTo(windowRect.left + windowRect.width * 0.22f, windowRect.bottom)
                                    close()
                                },
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f), Color.Transparent),
                                    windowRect.left + windowRect.width * 0.22f,
                                    windowRect.left + windowRect.width * 0.66f,
                                ),
                            )

                            // Curved top glare highlight
                            drawRect(
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                                    windowRect.top,
                                    windowRect.top + windowRect.height * 0.26f,
                                ),
                                topLeft = Offset(windowRect.left, windowRect.top),
                                size = Size(windowRect.width, windowRect.height * 0.26f),
                            )

                            // Deep inner overhang shadow around the bezel edge
                            drawPath(innerPath, Color.Black.copy(alpha = 0.65f), style = Stroke(rim * 1.6f))
                        }
                        drawPath(innerPath, Color.White.copy(alpha = 0.22f), style = Stroke(0.9f))
                    }

                    // B. Clickable area over the window to open the door when closed
                    Box(
                        Modifier
                            .offset(windowLeft, windowTop)
                            .size(windowW, windowH)
                            .clickable(enabled = !doorOpen, onClick = onToggleDoor)
                    )

                    // C. Branding header placed near the center fold, inset neatly from both cover edges
                    Rx707Header(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp, start = 20.dp, end = 20.dp),
                        compact = true,
                    )

                    // D. Curved Hammer-Tone Gunmetal Logic Control Pod mounted along the leading edge!
                    // Mounted INSIDE the door so when the door opens, the controls rotate WITH the cover!
                    Rx707LogicPod(
                        ui = ui,
                        onRewind = deck.rewind,
                        onFastForward = deck.fastForward,
                        onStop = {
                            if (ui.transport == Transport.STOPPED) {
                                onToggleDoor()
                            } else {
                                deck.stop()
                            }
                        },
                        onPlayToggle = deck.playToggle,
                        onToggleHold = vm::setHoldLocked,
                        inverted = true,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(0.86f)
                            .height((faceH * 0.32f).coerceIn(84.dp, 116.dp)),
                    )

                    // E. Leading Edge 3D Extruded Casing Thickness Rim
                    // Anchored directly to the leading edge (y = 0) of the door, rotating inside
                    // the exact same 3D graphicsLayer as the faceplate and controls.
                    // Chamfered at both ends to match the rounded chassis contour, staying strictly
                    // within the cassette bay without jutting into the top or bottom hardware panels!
                    if (doorProgress > 0.005f) {
                        Canvas(Modifier.fillMaxSize()) {
                            val w = size.width
                            val rad = Math.toRadians((72.0 * doorProgress))
                            val cosT = kotlin.math.cos(rad).toFloat().coerceAtLeast(0.06f)
                            val sinT = kotlin.math.sin(rad).toFloat()

                            // Authentic physical casing thickness of a metal Walkman door (~9dp)
                            val screenRimH = 9.dp.toPx() * sinT
                            // Local height inside the door's 3D frame required to project to screenRimH:
                            val localRimH = (screenRimH / cosT).coerceIn(0f, 26.dp.toPx())

                            if (localRimH > 1f) {
                                val topY = -localRimH
                                val botY = 0f
                                val cornerR = 14.dp.toPx()

                                // Chamfered rim path:
                                // Along the front corner (botY = 0), spans from 0 to w.
                                // Along the back crease (topY), chamfers inwards at both ends to match
                                // the rounded chassis contours so the corners never jut out into hardware strips!
                                val rimPath = Path().apply {
                                    moveTo(0f, botY)
                                    lineTo(w, botY)
                                    lineTo(w - cornerR * 0.45f, topY)
                                    lineTo(cornerR * 0.45f, topY)
                                    close()
                                }

                                // 1. Solid brushed titanium casing edge face
                                drawPath(
                                    rimPath,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color(0xFF16181C), // Deep crease shadow at inner chamber joint
                                            Color(0xFF2E323A),
                                            Color(0xFF4C525E),
                                            Color(0xFF343840), // Darker transition near leading corner
                                        ),
                                        startY = topY,
                                        endY = botY,
                                    ),
                                )

                                // 2. Fine vertical brushed metal grain striations across the rim face
                                for (i in 0..48) {
                                    val lx = w * (i / 48f)
                                    val clampedLx = lx.coerceIn(cornerR * 0.45f, w - cornerR * 0.45f)
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.08f),
                                        start = Offset(clampedLx, topY),
                                        end = Offset(lx, botY),
                                        strokeWidth = 1f,
                                    )
                                }

                                // 3. Crisp polished chamfer highlight along the front leading corner (y = 0)
                                drawLine(
                                    color = Color(0xFFF6F8FD),
                                    start = Offset(0f, botY),
                                    end = Offset(w, botY),
                                    strokeWidth = 2.dp.toPx(),
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.45f),
                                    start = Offset(0f, botY - 1.dp.toPx()),
                                    end = Offset(w, botY - 1.dp.toPx()),
                                    strokeWidth = 0.8.dp.toPx(),
                                )

                                // 4. Inner crease shadow along the back edge
                                drawLine(
                                    color = Color(0xFF07080A),
                                    start = Offset(cornerR * 0.45f, topY),
                                    end = Offset(w - cornerR * 0.45f, topY),
                                    strokeWidth = 1.5.dp.toPx(),
                                )

                                // 5. Mechanical latch catch notch in the center of the rim
                                val catchW = 38.dp.toPx()
                                val catchH = (localRimH * 0.58f).coerceAtLeast(4f)
                                val catchLeft = (w - catchW) / 2f
                                val catchTop = topY + (localRimH - catchH) / 2f
                                drawRoundRect(
                                    color = Color(0xFF08090C),
                                    topLeft = Offset(catchLeft, catchTop),
                                    size = Size(catchW, catchH),
                                    cornerRadius = CornerRadius(2.dp.toPx()),
                                )
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.35f),
                                    topLeft = Offset(catchLeft, catchTop),
                                    size = Size(catchW, catchH),
                                    cornerRadius = CornerRadius(2.dp.toPx()),
                                    style = Stroke(1f),
                                )
                                // Brass latch catch pin
                                val pinW = 12.dp.toPx()
                                val pinH = catchH * 0.65f
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        listOf(Color(0xFFFBE492), Color(0xFFC49E46), Color(0xFF7E601C))
                                    ),
                                    topLeft = Offset((w - pinW) / 2f, catchTop + (catchH - pinH) / 2f),
                                    size = Size(pinW, pinH),
                                    cornerRadius = CornerRadius(1.5.dp.toPx()),
                                )

                                // 6. Outer corner bevel highlight lines
                                drawLine(
                                    color = Color.White.copy(alpha = 0.35f),
                                    start = Offset(0f, botY),
                                    end = Offset(cornerR * 0.45f, topY),
                                    strokeWidth = 1.2.dp.toPx(),
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.35f),
                                    start = Offset(w, botY),
                                    end = Offset(w - cornerR * 0.45f, topY),
                                    strokeWidth = 1.2.dp.toPx(),
                                )
                            }
                        }
                    }
                }
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
