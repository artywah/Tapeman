package dev.foldman.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldman.deck.DeckUi
import dev.foldman.deck.Transport

/**
 * The Sony Walkman WM-RX707 hardware unit:
 * - Left/top titanium bay: OPEN/CLOSE slide latch, AMS skip buttons, EX DBB switch, REC/SHELL button.
 * - Center matte-black faceplate: Arty Tapeman header, animated 3D cassette door + exposed cassette chamber, logic pod.
 * - Right/bottom titanium bay: Mechanical tape counter, AVLS switch, volume slider, remote/phones jack.
 */
@Composable
fun WalkmanPlayerUnit(
    ui: DeckUi,
    anim: DeckAnimations,
    doorOpen: Boolean,
    onToggleDoor: () -> Unit,
    onCloseDoor: () -> Unit,
    onPlayToggle: () -> Unit,
    onStop: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSetVolume: (Float) -> Unit,
    onToggleDbb: (Boolean) -> Unit,
    onToggleAvls: (Boolean) -> Unit,
    onCycleShell: () -> Unit,
    onToggleHold: (Boolean) -> Unit = {},
    onResetCounter: () -> Unit = { anim.reels.resetCounter() },
    onOpenInspector: () -> Unit = {},
    modifier: Modifier = Modifier,
    forcePortrait: Boolean? = null,
) {
    BoxWithConstraints(modifier) {
        val isPortraitWindow = forcePortrait ?: (maxHeight > maxWidth)
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
                        onPress = { if (!ui.holdLocked) onPrevious() },
                        symbol = KeySymbol.PREV,
                        diameter = 28.dp,
                        captionColor = DeckColors.Engrave,
                    )
                    MiniButton(
                        "AMS",
                        onPress = { if (!ui.holdLocked) onNext() },
                        symbol = KeySymbol.NEXT,
                        diameter = 28.dp,
                        enabled = ui.hasNext,
                        captionColor = DeckColors.Engrave,
                    )
                }

                SlideSwitch(
                    on = ui.dbbEnabled,
                    onToggle = onToggleDbb,
                    caption = "EX DBB",
                    onLabel = "ON",
                    offLabel = "NORM",
                    height = 50.dp,
                )

                Rx707RecButton(
                    active = ui.shell.isTransparent,
                    onClick = onCycleShell,
                    caption = "REC · SHELL",
                    subLabel = ui.shell.brand,
                    onLongClick = onOpenInspector,
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
                        onRewind = onRewind,
                        onFastForward = onFastForward,
                        onStop = {
                            if (ui.transport == Transport.STOPPED) {
                                onToggleDoor()
                            } else {
                                onStop()
                            }
                        },
                        onPlayToggle = onPlayToggle,
                        onToggleHold = onToggleHold,
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
                                    cornerRadius = CornerRadius(1.5f.dp.toPx()),
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
                        onPress = onResetCounter,
                        diameter = 20.dp,
                        captionColor = DeckColors.Engrave,
                    )
                }
                Spacer(Modifier.height(8.dp))
                SlideSwitch(
                    on = ui.avlsEnabled,
                    onToggle = onToggleAvls,
                    caption = "AVLS",
                    onLabel = "LIM",
                    offLabel = "NORM",
                    height = 46.dp,
                )
                Spacer(Modifier.height(8.dp))
                Text("VOLUME", style = EngravedCaps.copy(fontSize = 7.sp, color = DeckColors.Engrave))
                VolumeSlider(ui.volume, onSetVolume, Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(6.dp))
                PhonesJack(Modifier.size(16.dp))
            }
        }
    }
}
