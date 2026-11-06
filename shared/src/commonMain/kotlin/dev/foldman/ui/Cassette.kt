package dev.foldman.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldman.deck.CassetteShell
import dev.foldman.deck.DeckUi
import dev.foldman.deck.LabelPalette
import dev.foldman.deck.Source
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

// Cassette geometry in millimetres (a real Compact Cassette is 100.4 × 63.8).
private val HUB_L = Offset(28.75f, 32f)
private val HUB_R = Offset(71.25f, 32f)
private val WINDOW = Rect(15f, 21f, 85f, 43f)

/**
 * The part of the cassette the WM-RX707 stadium window shows: both hubs, the tape packs,
 * and the MA-XG46 / album markings around them.
 */
private val CROP = Rect(8f, 11.5f, 92f, 49.5f)
const val TAPE_WINDOW_ASPECT = 84f / 38f
const val FULL_CASSETTE_ASPECT = 100f / 64f

/**
 * Renders the complete exposed Compact Cassette tape (DuoMan style),
 * showing the entire shell, corner screws, hubs, tape ribbon path, and full label.
 */
@Composable
fun FullCassette(
    ui: DeckUi,
    anim: DeckAnimations,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    inverted: Boolean = false,
    stationary: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val shell by animateColorAsState(DeckColors.shell(ui.source), tween(600), label = "shellFull")
    Canvas(
        modifier
            .quarterTurn(vertical)
            .aspectRatio(FULL_CASSETTE_ASPECT)
            .graphicsLayer(),
    ) {
        val u = size.width / 100f
        val slide = if (stationary) 0f else anim.insert.value
        val pop = if (stationary) 0f else anim.pop.value
        val dx = if (vertical) slide * size.width * 1.1f + pop * u * 5f else 0f
        val dy = if (vertical) 0f else -slide * size.height * 1.3f - pop * u * 5f
        translate(dx, dy) {
            withTransform({
                if (inverted) {
                    rotate(180f, Offset(50f * u, 32f * u))
                }
                scale(u, u, Offset.Zero)
            }) {
                drawSelectedCassette(ui, anim.reels, shell, measurer, u, inverted = inverted)
            }
        }
    }
}

/**
 * The interior cassette well / chamber of the Walkman:
 * Shows the recessed transport bay, guide spindles, magnetic read/write head,
 * and the full cassette tape seated inside. Tapping it closes the door.
 */
@Composable
fun CassetteChamber(
    ui: DeckUi,
    anim: DeckAnimations,
    onCloseDoor: () -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    inverted: Boolean = false,
    promptToClose: Boolean = true,
    tapeOffset: DpOffset = DpOffset.Zero,
    tapeSize: DpSize = DpSize.Unspecified,
    doorProgress: Float = 0f,
) {
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0C0E10))
            .clickable(onClick = onCloseDoor),
    ) {
        val hasCustomTape = tapeSize != DpSize.Unspecified
        // Draw the mechanical bay floor: drive spindles, guide posts, and tape head
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Recessed bay floor texture & perimeter bevel
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF0A0B0D), Color(0xFF141619), Color(0xFF090A0C))),
                cornerRadius = CornerRadius(12.dp.toPx()),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.08f),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(1.5f.dp.toPx()),
            )

            val (tapeLeftPx, tapeTopPx, tapeWPx, tapeHPx) = if (hasCustomTape) {
                with(density) {
                    listOf(tapeOffset.x.toPx(), tapeOffset.y.toPx(), tapeSize.width.toPx(), tapeSize.height.toPx())
                }
            } else {
                val tW = w * 0.92f
                val tH = tW * 64f / 100f
                listOf((w - tW) / 2f, (h - tH) / 2f, tW, tH)
            }

            val uPx = tapeWPx / 100f

            // Cassette well inner guide tracks around the cassette
            val trackPad = 5f.dp.toPx()
            drawRoundRect(
                color = Color(0xFF060708),
                topLeft = Offset(tapeLeftPx - trackPad, tapeTopPx - trackPad),
                size = Size(tapeWPx + trackPad * 2, tapeHPx + trackPad * 2),
                cornerRadius = CornerRadius(8.dp.toPx()),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.05f),
                topLeft = Offset(tapeLeftPx - trackPad, tapeTopPx - trackPad),
                size = Size(tapeWPx + trackPad * 2, tapeHPx + trackPad * 2),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(1f.dp.toPx()),
            )

            // Mechanical drive spindle indicators perfectly aligned behind the reel hubs
            val hubLX = tapeLeftPx + 28.75f * uPx
            val hubLY = tapeTopPx + 32f * uPx
            val hubRX = tapeLeftPx + 71.25f * uPx
            val hubRY = tapeTopPx + 32f * uPx
            val spindleR = 13.dp.toPx()

            // Left spindle teeth
            drawCircle(Color(0xFF1C1F24), radius = spindleR, center = Offset(hubLX, hubLY))
            drawCircle(Color.White.copy(alpha = 0.22f), radius = spindleR, center = Offset(hubLX, hubLY), style = Stroke(1f))
            drawCircle(Color(0xFF32363D), radius = spindleR * 0.45f, center = Offset(hubLX, hubLY))

            // Right spindle teeth
            drawCircle(Color(0xFF1C1F24), radius = spindleR, center = Offset(hubRX, hubRY))
            drawCircle(Color.White.copy(alpha = 0.22f), radius = spindleR, center = Offset(hubRX, hubRY), style = Stroke(1f))
            drawCircle(Color(0xFF32363D), radius = spindleR * 0.45f, center = Offset(hubRX, hubRY))

            // Lower/Upper tape head bridge assembly (permalloy head, capstan, pinch roller)
            val headW = tapeWPx * 0.26f
            val headH = 8.dp.toPx()
            val headLeft = tapeLeftPx + (tapeWPx - headW) / 2f
            val headTop = if (inverted) {
                (tapeTopPx - headH - 1f.dp.toPx()).coerceAtLeast(1f.dp.toPx())
            } else {
                (tapeTopPx + tapeHPx + 2f.dp.toPx()).coerceAtMost(h - headH - 2f.dp.toPx())
            }
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF555960), Color(0xFF2B2D31))),
                topLeft = Offset(headLeft, headTop),
                size = Size(headW, headH),
                cornerRadius = CornerRadius(2f.dp.toPx()),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.35f),
                topLeft = Offset(headLeft, headTop),
                size = Size(headW, headH),
                cornerRadius = CornerRadius(2f.dp.toPx()),
                style = Stroke(0.8f),
            )

            // Soft directional ambient drop shadow cast by the open cassette door onto the bay floor
            if (doorProgress > 0.05f) {
                val shadowH = (72.dp * doorProgress).toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.20f * doorProgress),
                            Color.Black.copy(alpha = 0.50f * doorProgress),
                            Color.Black.copy(alpha = 0.78f * doorProgress),
                        ),
                        h - shadowH,
                        h,
                    ),
                    topLeft = Offset(0f, h - shadowH),
                    size = Size(w, shadowH),
                )
            }
        }

        // Full cassette sitting in the chamber
        val tapeMod = if (hasCustomTape) {
            Modifier
                .offset(tapeOffset.x, tapeOffset.y)
                .size(tapeSize.width, tapeSize.height)
        } else {
            Modifier
                .align(Alignment.Center)
                .fillMaxSize(0.92f)
                .padding(vertical = 4.dp)
        }
        FullCassette(
            ui = ui,
            anim = anim,
            vertical = vertical,
            inverted = inverted,
            modifier = tapeMod,
        )

        // Prompt overlay
        if (promptToClose) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xCC0E1013))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    "TAP CASSETTE OR CLOSE TO PLAY",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeckColors.Accent,
                        letterSpacing = 1.sp,
                    ),
                )
            }
        }
    }
}

/**
 * The WM-RX707 wide rounded-stadium smoked acrylic window with the cassette behind it.
 */
@Composable
fun TapeWindow(ui: DeckUi, anim: DeckAnimations, modifier: Modifier = Modifier, vertical: Boolean = false) {
    val measurer = rememberTextMeasurer()
    val shell by animateColorAsState(DeckColors.shell(ui.source), tween(600), label = "shell")
    Canvas(
        modifier
            .quarterTurn(vertical)
            .aspectRatio(TAPE_WINDOW_ASPECT)
            .graphicsLayer(),
    ) {
        val u = size.width / CROP.width
        // WM-RX707 wide rounded-stadium window geometry
        val r = CornerRadius(11.5f * u, 11.5f * u)
        val rim = 1.6f * u

        // Outer recessed bezel trench in the matte black body
        drawRoundRect(
            color = Color(0xFF070809),
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = r,
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF101114), Color(0xFF282B30), Color(0xFF181A1E))
            ),
            topLeft = Offset(0.5f, 0.5f),
            size = Size(size.width - 1f, size.height - 1f),
            cornerRadius = r,
            style = Stroke(rim),
        )

        val inner = RoundRect(rim, rim, size.width - rim, size.height - rim, CornerRadius(r.x - rim, r.y - rim))
        val innerPath = Path().apply { addRoundRect(inner) }
        drawPath(innerPath, Color(0xFF090A0C))

        val slide = anim.insert.value
        val pop = anim.pop.value
        clipPath(innerPath) {
            val dx = if (vertical) slide * size.width * 1.1f + pop * u * 5f else 0f
            val dy = if (vertical) 0f else -slide * size.height * 1.3f - pop * u * 5f
            translate(dx, dy) {
                withTransform({
                    translate(-CROP.left * u, -CROP.top * u)
                    scale(u, u, Offset.Zero)
                }) {
                    drawSelectedCassette(ui, anim.reels, shell, measurer, u)
                }
            }

            // Smoked acrylic glass tint + WM-RX707 inner stadium contour ring
            drawRect(Color(0xFF1E2228).copy(alpha = 0.18f))

            // Inner stadium contour moulded into the WM-RX707 acrylic window (as seen in wm_rx707_controls.jpg)
            val contourW = size.width * 0.66f
            val contourH = size.height * 0.48f
            val contourLeft = (size.width - contourW) / 2f
            val contourTop = (size.height - contourH) / 2f
            val contourR = CornerRadius(contourH / 2f, contourH / 2f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.14f),
                topLeft = Offset(contourLeft, contourTop),
                size = Size(contourW, contourH),
                cornerRadius = contourR,
                style = Stroke(1.1f * u * 0.25f),
            )
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.28f),
                topLeft = Offset(contourLeft + 1f, contourTop + 1f),
                size = Size(contourW - 2f, contourH - 2f),
                cornerRadius = contourR,
                style = Stroke(0.8f * u * 0.25f),
            )

            // Soft diagonal acrylic reflection & curved top glare
            drawPath(
                Path().apply {
                    moveTo(size.width * 0.46f, 0f); lineTo(size.width * 0.66f, 0f)
                    lineTo(size.width * 0.42f, size.height); lineTo(size.width * 0.22f, size.height); close()
                },
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.03f), Color.Transparent),
                    size.width * 0.22f,
                    size.width * 0.66f,
                ),
            )
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.15f), Color.Transparent),
                    0f,
                    size.height * 0.26f,
                )
            )
            // Deep inner overhang shadow
            drawPath(innerPath, Color.Black.copy(alpha = 0.72f), style = Stroke(rim * 1.6f))
        }
        drawPath(innerPath, Color.White.copy(alpha = 0.22f), style = Stroke(0.9f))
    }
}

/**
 * Draws the iconic Sony MA-XG46 Metal Position (Type IV) transparent smoked cassette
 * shown inside the WM-RX707 in wm_rx707_front.jpg.
 */
private fun DrawScope.drawMaXgCassette(
    ui: DeckUi,
    reels: ReelMotion,
    shellTint: Color,
    measurer: TextMeasurer,
    u: Float,
    inverted: Boolean = false,
) {
    val shellRR = RoundRect(0f, 0f, 100f, 64f, CornerRadius(3.4f))
    val shellPath = Path().apply { addRoundRect(shellRR) }

    // 1. Dark cassette bay floor behind the transparent shell
    drawPath(shellPath, Color(0xFF0E1012))

    // Internal structural ribs of the MA-XG46 ceramic/polycarbonate frame
    drawLine(Color.White.copy(alpha = 0.06f), Offset(8f, 20f), Offset(92f, 20f), 0.4f)
    drawLine(Color.White.copy(alpha = 0.06f), Offset(8f, 44f), Offset(92f, 44f), 0.4f)
    drawLine(Color.White.copy(alpha = 0.05f), Offset(50f, 10f), Offset(50f, 52f), 0.4f)

    // 2. Full magnetic tape packs visible through the transparent smoked shell!
    drawPack(HUB_L, reels.leftRadius, reels.leftAngle)
    drawPack(HUB_R, reels.rightRadius, reels.rightAngle)

    // Tape path running from each pack down to the guide rollers
    drawLine(DeckColors.Tape, Offset(HUB_L.x - reels.leftRadius, HUB_L.y), Offset(HUB_L.x - reels.leftRadius - 1f, 64f), 0.6f)
    drawLine(DeckColors.Tape, Offset(HUB_R.x + reels.rightRadius, HUB_R.y), Offset(HUB_R.x + reels.rightRadius + 1f, 64f), 0.6f)

    // 3. Iconic white composite dual-ring MA-XG46 reel flanges + red leader clamp!
    drawMaXgFlange(HUB_L, reels.leftAngle, reels.leftDelta, hasRedClamp = true)
    drawMaXgFlange(HUB_R, reels.rightAngle, reels.rightDelta, hasRedClamp = false)

    // 4. Smoked translucent polycarbonate top shell layer with subtle source tint
    drawPath(
        shellPath,
        Brush.verticalGradient(
            listOf(
                lerp(Color(0xFF282C32), shellTint, 0.25f).copy(alpha = 0.34f),
                Color(0xFF14161A).copy(alpha = 0.24f),
                lerp(Color(0xFF1E2228), shellTint, 0.25f).copy(alpha = 0.38f),
            ),
            0f,
            64f,
        ),
    )

    // Center transparent scale window outline
    val centerWindow = RoundRect(Rect(36f, 25.5f, 64f, 38.5f), CornerRadius(1.6f))
    drawRoundRect(
        color = Color.White.copy(alpha = 0.16f),
        topLeft = Offset(centerWindow.left, centerWindow.top),
        size = Size(centerWindow.width, centerWindow.height),
        cornerRadius = CornerRadius(1.6f),
        style = Stroke(0.35f),
    )
    // Scale tick dots between the hubs (visible in wm_rx707_front.jpg)
    for (i in 0..4) {
        val tx = 42f + i * 4f
        drawCircle(Color.White.copy(alpha = 0.32f), 0.38f, Offset(tx, 32f))
    }

    // 5. Top slim metallic header strip showing Album Art & Current Song Title
    val headerStrip = RoundRect(11f, 12.6f, 89f, 18.2f, CornerRadius(1.2f))
    val stripPath = Path().apply { addRoundRect(headerStrip) }
    val accent = DeckColors.sourceAccent(ui.source)
    clipPath(stripPath) {
        drawRect(Color(0xFF141619).copy(alpha = 0.86f), Offset(11f, 12.6f), Size(78f, 5.6f))
        ui.artwork?.let { art ->
            drawCroppedImage(art, Rect(11f, 12.6f, 20f, 18.2f), inverted = inverted)
        }
        drawRect(accent, Offset(11f, 17.5f), Size(78f, 0.7f))
    }
    drawPath(stripPath, Color.White.copy(alpha = 0.22f), style = Stroke(0.22f))

    fun text(s: String, x: Float, y: Float, sizeMm: Float, maxWidthMm: Float, style: TextStyle) {
        if (s.isBlank()) return
        val layout = measurer.measure(
            s,
            style.copy(fontSize = (sizeMm * u).toSp()),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = (maxWidthMm * u).toInt().coerceAtLeast(1)),
        )
        val twMm = layout.size.width / u
        val thMm = layout.size.height / u
        withTransform({
            translate(x, y)
            if (inverted) {
                rotate(180f, Offset(twMm / 2f, thMm / 2f))
            }
            scale(1f / u, 1f / u, Offset.Zero)
        }) { drawText(layout) }
    }

    val printBold = TextStyle(fontFamily = FontFamily.SansSerif, color = Color(0xFFF0F3F7), fontWeight = FontWeight.Bold)
    val printBlack = TextStyle(fontFamily = FontFamily.SansSerif, color = Color(0xFFFFFFFF), fontWeight = FontWeight.Black)
    val titleX = if (ui.artwork != null) 21.8f else 13.5f
    val title = ui.title.ifBlank { placeholderTitle(ui) }
    val trackLine = listOf(title, ui.artist).filter { it.isNotBlank() }.joinToString("  —  ")
    text(trackLine, titleX, 13.5f, 3.1f, 87f - titleX, printBold)

    // 6. Bottom metadata rail (safely between hubs and in corners)
    val srcCode = when (ui.source) {
        Source.LOCAL -> "LOCAL · HI-BIAS"
        Source.PLEX -> "PLEX · DIRECT"
        Source.SPOTIFY -> "SPOTIFY · STREAM"
        Source.YT_MUSIC -> "YTM · SESSION"
        Source.APPLE_MUSIC -> "APPLE · MUSIC"
    }
    // Left corner (x: 10..20, safe from HUB_L collar at 20.5mm)
    text("MA-XG46", 11.0f, 44.2f, 2.8f, 9.5f, printBlack)
    // Center bay (x: 37..63, safe between the two hubs)
    text("TYPE IV · METAL POSITION", 37.0f, 44.5f, 2.0f, 26f, printBold.copy(color = Color(0xFFDCE0E6)))
    // Right corner (x: 80.5..90.5, safe from HUB_R collar at 79.5mm)
    text(srcCode, 80.5f, 44.5f, 2.0f, 10f, printBold.copy(color = accent))

    drawBottomBridge(Color(0xFF23262B), light = false)
    listOf(Offset(4.4f, 4.4f), Offset(95.6f, 4.4f), Offset(4.4f, 59.6f), Offset(95.6f, 59.6f), Offset(50f, 55.5f))
        .forEachIndexed { i, c -> drawScrew(c, 1.5f, 15f + i * 40f) }
}

/**
 * Draws the Sony MA-XG46 white dual-ring composite reel flange with radial slots and the
 * signature red/orange leader clamp (visible on the left reel in wm_rx707_front.jpg).
 */
private fun DrawScope.drawMaXgFlange(center: Offset, angle: Float, delta: Float, hasRedClamp: Boolean) {
    val outerFlangeR = 13.8f
    val midRingR = 10.6f
    // Outer white composite ring
    drawCircle(
        color = Color.Black.copy(alpha = 0.45f),
        radius = outerFlangeR + 0.4f,
        center = center + Offset(0.2f, 0.4f),
    )
    drawCircle(
        color = Color(0xFFECEEE9).copy(alpha = 0.92f),
        radius = outerFlangeR,
        center = center,
        style = Stroke(2.1f),
    )
    drawCircle(
        color = Color(0xFFDADCD6).copy(alpha = 0.85f),
        radius = midRingR,
        center = center,
        style = Stroke(1.3f),
    )

    // Rotating spokes + red leader clamp
    rotate(angle, center) {
        if (hasRedClamp) {
            drawArc(
                color = Color(0xFFE03C24),
                startAngle = 115f,
                sweepAngle = 24f,
                useCenter = false,
                topLeft = center - Offset(midRingR + 1.4f, midRingR + 1.4f),
                size = Size((midRingR + 1.4f) * 2f, (midRingR + 1.4f) * 2f),
                style = Stroke(2.2f),
            )
        }
        for (i in 0 until 3) {
            rotate(i * 120f, center) {
                drawRect(
                    color = Color(0xFFE8EAE4).copy(alpha = 0.9f),
                    topLeft = Offset(center.x - 1.0f, center.y - outerFlangeR),
                    size = Size(2.0f, outerFlangeR - 6.0f),
                )
            }
        }
    }

    // Center hub + drive spindle with motion blur
    drawHub(center, angle, delta)
}

/** Draws the classic printed-label Compact Cassette when transparentShell is toggled off. */
private fun DrawScope.drawClassicCassette(
    ui: DeckUi,
    reels: ReelMotion,
    shell: Color,
    measurer: TextMeasurer,
    u: Float,
    inverted: Boolean = false,
) {
    val light = shell.luminance() > 0.5f
    val shellRR = RoundRect(0f, 0f, 100f, 64f, CornerRadius(3.4f))
    val shellPath = Path().apply { addRoundRect(shellRR) }

    translate(0.8f, 1.6f) { drawPath(shellPath, Color.Black.copy(alpha = 0.45f)) }
    drawPath(
        shellPath,
        Brush.verticalGradient(listOf(lerp(shell, Color.White, 0.18f), shell, lerp(shell, Color.Black, 0.22f)), 0f, 64f),
    )
    for (i in 0 until 7) {
        val y = 22f + i * 3f
        drawLine(lerp(shell, Color.Black, 0.25f), Offset(1.4f, y), Offset(3.2f, y), 0.5f)
        drawLine(lerp(shell, Color.Black, 0.25f), Offset(96.8f, y), Offset(98.6f, y), 0.5f)
    }

    val windowRR = RoundRect(WINDOW, CornerRadius(3.2f))
    val windowPath = Path().apply { addRoundRect(windowRR) }
    clipPath(windowPath) {
        drawRect(Color(0xFF1A1714), WINDOW.topLeft, WINDOW.size)
        drawPack(HUB_L, reels.leftRadius, reels.leftAngle)
        drawPack(HUB_R, reels.rightRadius, reels.rightAngle)
        drawLine(DeckColors.Tape, Offset(HUB_L.x - reels.leftRadius, HUB_L.y), Offset(HUB_L.x - reels.leftRadius - 1f, 64f), 0.6f)
        drawLine(DeckColors.Tape, Offset(HUB_R.x + reels.rightRadius, HUB_R.y), Offset(HUB_R.x + reels.rightRadius + 1f, 64f), 0.6f)
        drawRect(Color(0xFF6F7680).copy(alpha = 0.18f), WINDOW.topLeft, WINDOW.size)
    }
    drawHub(HUB_L, reels.leftAngle, reels.leftDelta)
    drawHub(HUB_R, reels.rightAngle, reels.rightDelta)
    clipPath(windowPath) {
        drawPath(
            Path().apply {
                moveTo(WINDOW.left + 18f, WINDOW.top); lineTo(WINDOW.left + 30f, WINDOW.top)
                lineTo(WINDOW.left + 20f, WINDOW.bottom); lineTo(WINDOW.left + 8f, WINDOW.bottom); close()
            },
            Color.White.copy(alpha = 0.07f),
        )
    }
    drawPath(windowPath, Color.Black.copy(alpha = 0.55f), style = Stroke(0.5f))

    drawLabel(ui, ui.label, measurer, windowRR, u, inverted = inverted)
    drawBottomBridge(shell, light)

    listOf(Offset(4.4f, 4.4f), Offset(95.6f, 4.4f), Offset(4.4f, 59.6f), Offset(95.6f, 59.6f), Offset(50f, 55.5f))
        .forEachIndexed { i, c -> drawScrew(c, 1.5f, 15f + i * 40f) }
    drawPath(shellPath, Color.Black.copy(alpha = 0.5f), style = Stroke(0.35f))
    drawPath(Path().apply { addRoundRect(RoundRect(0.4f, 0.4f, 99.6f, 63.6f, CornerRadius(3.1f))) }, Color.White.copy(alpha = 0.25f), style = Stroke(0.25f))
}

/** A pack of wound tape: concentric layers with a faint sheen that turns with the reel. */
private fun DrawScope.drawPack(center: Offset, radius: Float, angle: Float) {
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFF1A110D),
            0.5f to DeckColors.Tape,
            0.9f to Color(0xFF4A3122),
            1f to Color(0xFF23160F),
            center = center,
            radius = radius,
        ),
        radius,
        center,
    )
    var r = ReelMotion.R_EMPTY + 1f
    while (r < radius - 0.4f) {
        drawCircle(Color.Black.copy(alpha = 0.18f), r, center, style = Stroke(0.12f))
        r += 1.35f
    }
    rotate(angle, center) {
        for (k in 0 until 3) {
            drawArc(
                Color(0xFFA87D5C).copy(alpha = 0.10f + 0.05f * k),
                startAngle = k * 120f,
                sweepAngle = 26f,
                useCenter = true,
                topLeft = center - Offset(radius, radius),
                size = Size(radius * 2, radius * 2),
            )
        }
    }
    drawCircle(Color.Black.copy(alpha = 0.55f), radius, center, style = Stroke(0.25f))
}

/**
 * The white plastic hub and the deck's drive spindle inside it. When the reel turns faster than
 * the eye can follow, the spokes are drawn several times across the frame's sweep as motion blur.
 */
private fun DrawScope.drawHub(center: Offset, angle: Float, delta: Float) {
    drawCircle(Color.Black.copy(alpha = 0.4f), 6.6f, center + Offset(0.2f, 0.4f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFAFAF8), Color(0xFFD9D8D3)), center, 6.4f), 6.3f, center)
    drawCircle(Color(0xFF121315), 4.5f, center)
    val sweep = abs(delta)
    val copies = if (sweep > 5f) min(7, (sweep / 3.5f).roundToInt() + 1) else 1
    val alpha = if (copies == 1) 1f else (1.7f / copies).coerceAtMost(1f)
    for (i in 0 until copies) {
        val a = angle - delta * i / copies
        rotate(a, center) {
            for (t in 0 until 6) {
                rotate(t * 60f, center) {
                    drawRect(Color(0xFFEDECE8).copy(alpha = alpha), Offset(center.x - 0.55f, center.y - 4.6f), Size(1.1f, 1.4f))
                }
            }
            for (t in 0 until 3) {
                rotate(t * 120f + 30f, center) {
                    drawRect(Color(0xFF8C8F94).copy(alpha = alpha), Offset(center.x - 0.5f, center.y - 3.3f), Size(1f, 3.3f))
                }
            }
        }
    }
    drawCircle(Brush.radialGradient(listOf(Color(0xFFD5D8DC), Color(0xFF6D7075)), center - Offset(0.5f, 0.5f), 2.6f), 2.1f, center)
    drawCircle(Color(0xFF202124), 0.55f, center)
}

private fun DrawScope.drawLabel(
    ui: DeckUi,
    palette: LabelPalette,
    measurer: TextMeasurer,
    window: RoundRect,
    u: Float,
    inverted: Boolean = false,
) {
    val labelRect = RoundRect(5.5f, 4f, 94.5f, 48.5f, CornerRadius(2f))
    val labelPath = Path().apply {
        fillType = PathFillType.EvenOdd
        addRoundRect(labelRect)
        addRoundRect(RoundRect(window.left - 1f, window.top - 1f, window.right + 1f, window.bottom + 1f, CornerRadius(4f)))
    }
    val art = ui.artwork
    clipPath(labelPath) {
        if (art != null) {
            drawCroppedImage(art, Rect(labelRect.left, labelRect.top, labelRect.right, labelRect.bottom), inverted = inverted)
            drawRect(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Transparent, Color.Black.copy(alpha = 0.3f)), 4f, 48.5f),
                Offset(5.5f, 4f),
                Size(89f, 44.5f),
            )
        } else {
            drawRect(palette.paper, Offset(5.5f, 4f), Size(89f, 44.5f))
            drawRect(palette.stripe, Offset(5.5f, 27f), Size(89f, 3.6f))
            drawRect(palette.stripe2, Offset(5.5f, 31.4f), Size(89f, 2.2f))
            drawRect(palette.stripe.copy(alpha = 0.55f), Offset(5.5f, 34.4f), Size(89f, 1.0f))
        }
    }
    drawPath(labelPath, Color.Black.copy(alpha = 0.18f), style = Stroke(0.2f))

    val strip = RoundRect(10f, 13.4f, 90f, 19.4f, CornerRadius(0.9f))
    drawPath(Path().apply { addRoundRect(strip) }, palette.paper.copy(alpha = if (art != null) 0.94f else 1f))
    drawLine(DeckColors.PaperLine.copy(alpha = 0.7f), Offset(17.5f, 18.4f), Offset(89f, 18.4f), 0.12f)
    drawCircle(palette.ink, 2.4f, Offset(13.6f, 16.4f), style = Stroke(0.3f))
    val ink = palette.ink
    fun text(s: String, x: Float, y: Float, sizeMm: Float, maxWidthMm: Float, style: TextStyle) {
        if (s.isBlank()) return
        val layout = measurer.measure(
            s,
            style.copy(fontSize = (sizeMm * u).toSp()),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = (maxWidthMm * u).toInt().coerceAtLeast(1)),
        )
        val twMm = layout.size.width / u
        val thMm = layout.size.height / u
        withTransform({
            translate(x, y)
            if (inverted) {
                rotate(180f, Offset(twMm / 2f, thMm / 2f))
            }
            scale(1f / u, 1f / u, Offset.Zero)
        }) { drawText(layout) }
    }
    val hand = TextStyle(fontFamily = FontFamily.Cursive, color = ink, fontWeight = FontWeight.Bold)
    val print = TextStyle(fontFamily = FontFamily.SansSerif, color = ink, fontWeight = FontWeight.Bold, letterSpacing = (0.3f * u).toSp())
    text(if (ui.reverseDir) "B" else "A", 12.75f, 14.4f, 3.6f, 5f, print)
    val title = ui.title.ifBlank { placeholderTitle(ui) }
    val line = listOf(title, ui.artist).filter { it.isNotBlank() }.joinToString("  —  ")
    text(line, 17.5f, 13.0f, 4.6f, 71f, hand)

    val tag = TextStyle(fontFamily = FontFamily.SansSerif, color = if (art != null) Color.White else ink, fontWeight = FontWeight.Black)
    val badge = when (ui.source) {
        Source.LOCAL -> "FM 60"
        Source.PLEX -> "PX 90"
        Source.SPOTIFY -> "SP ∞"
        Source.YT_MUSIC -> "YT ∞"
        Source.APPLE_MUSIC -> "AM ∞"
    }
    text(badge, 78f, 44.2f, 2.6f, 15f, tag)
    text("TYPE IV  METAL", 12f, 44.6f, 2.2f, 30f, tag.copy(fontWeight = FontWeight.Bold))
}

private fun placeholderTitle(ui: DeckUi) = when (ui.source) {
    Source.LOCAL -> "my mixtape"
    Source.PLEX -> ui.plex.loadedAlbumTitle ?: "Plex Server"
    Source.SPOTIFY -> "Spotify"
    Source.YT_MUSIC -> "YT Music"
    Source.APPLE_MUSIC -> "Apple Music"
}

/** Centre-crops [image] to fill [dst], like a printed label. */
private fun DrawScope.drawCroppedImage(image: ImageBitmap, dst: Rect, inverted: Boolean = false) {
    val dstRatio = dst.width / dst.height
    val srcRatio = image.width.toFloat() / image.height
    val (sw, sh) = if (srcRatio > dstRatio) (image.height * dstRatio) to image.height.toFloat() else image.width.toFloat() to (image.width / dstRatio)
    val sx = ((image.width - sw) / 2f).roundToInt()
    val sy = ((image.height - sh) * 0.4f).roundToInt()
    withTransform({
        if (inverted) {
            rotate(180f, dst.center)
        }
    }) {
        drawImage(
            image,
            srcOffset = IntOffset(sx, sy),
            srcSize = IntSize(sw.roundToInt(), sh.roundToInt()),
            dstOffset = IntOffset(dst.left.roundToInt(), dst.top.roundToInt()),
            dstSize = IntSize(dst.width.roundToInt().coerceAtLeast(1), dst.height.roundToInt().coerceAtLeast(1)),
        )
    }
}

/** The trapezoid at the bottom with the head openings, capstan holes and the exposed tape. */
private fun DrawScope.drawBottomBridge(shell: Color, light: Boolean) {
    val bridge = Path().apply {
        moveTo(16.5f, 64f); lineTo(21f, 50.5f); lineTo(79f, 50.5f); lineTo(83.5f, 64f); close()
    }
    drawPath(bridge, lerp(shell, if (light) Color.Black else Color.White, 0.08f))
    drawPath(bridge, Color.Black.copy(alpha = 0.35f), style = Stroke(0.3f))
    listOf(31.5f, 68.5f).forEach { x ->
        drawCircle(Color(0xFF0E0E0F), 1.9f, Offset(x, 58.5f))
        drawCircle(Color.White.copy(alpha = 0.2f), 1.9f, Offset(x, 58.5f), style = Stroke(0.2f))
    }
    listOf(24.5f, 45.5f, 72f).forEachIndexed { i, x ->
        val w = if (i == 1) 9f else 3.5f
        drawRect(Color(0xFF0E0E0F), Offset(x, 61.2f), Size(w, 2.8f))
    }
    drawRect(DeckColors.Tape, Offset(18f, 62.6f), Size(64f, 0.9f))
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

private fun DrawScope.drawSelectedCassette(
    ui: DeckUi,
    reels: ReelMotion,
    shellTint: Color,
    measurer: TextMeasurer,
    u: Float,
    inverted: Boolean = false,
) {
    when (ui.shell) {
        CassetteShell.TDK_MAXG -> drawMaXgCassette(ui, reels, shellTint, measurer, u, inverted)
        CassetteShell.TDK_SA -> drawTdkSaCassette(ui, reels, shellTint, measurer, u, inverted)
        CassetteShell.MAXELL_XLIIS -> drawMaxellXlIIsCassette(ui, reels, shellTint, measurer, u, inverted)
        CassetteShell.MAXELL_UDII -> drawMaxellUdIICassette(ui, reels, shellTint, measurer, u, inverted)
        CassetteShell.SONY_UXPRO -> drawSonyUxProCassette(ui, reels, shellTint, measurer, u, inverted)
        CassetteShell.SONY_HF -> drawSonyHfCassette(ui, reels, shellTint, measurer, u, inverted)
        CassetteShell.TEAC_SOUND52 -> drawTeacSound52Cassette(ui, reels, shellTint, measurer, u, inverted)
    }
}

private fun DrawScope.drawCassetteText(
    s: String,
    x: Float,
    y: Float,
    sizeMm: Float,
    maxWidthMm: Float,
    style: TextStyle,
    measurer: TextMeasurer,
    u: Float,
    inverted: Boolean = false,
) {
    if (s.isBlank()) return
    val layout = measurer.measure(
        s,
        style.copy(fontSize = (sizeMm * u).toSp()),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        constraints = Constraints(maxWidth = (maxWidthMm * u).toInt().coerceAtLeast(1)),
    )
    val twMm = layout.size.width / u
    val thMm = layout.size.height / u
    withTransform({
        translate(x, y)
        if (inverted) {
            rotate(180f, Offset(twMm / 2f, thMm / 2f))
        }
        scale(1f / u, 1f / u, Offset.Zero)
    }) { drawText(layout) }
}

/** 1. TDK SA-90 (1986): Definitive Type II High Bias tape in textured charcoal shell. */
private fun DrawScope.drawTdkSaCassette(
    ui: DeckUi,
    reels: ReelMotion,
    shellTint: Color,
    measurer: TextMeasurer,
    u: Float,
    inverted: Boolean = false,
) {
    val shell = Color(0xFF1E2024)
    val shellRR = RoundRect(0f, 0f, 100f, 64f, CornerRadius(3.4f))
    val shellPath = Path().apply { addRoundRect(shellRR) }

    translate(0.8f, 1.6f) { drawPath(shellPath, Color.Black.copy(alpha = 0.45f)) }
    drawPath(shellPath, Brush.verticalGradient(listOf(Color(0xFF282A30), shell, Color(0xFF161719)), 0f, 64f))

    // Side grip ribs
    for (i in 0 until 6) {
        val y = 23f + i * 3f
        drawLine(Color.Black.copy(alpha = 0.5f), Offset(1.4f, y), Offset(3.4f, y), 0.5f)
        drawLine(Color.Black.copy(alpha = 0.5f), Offset(96.6f, y), Offset(98.6f, y), 0.5f)
    }

    // Center window
    val windowRect = Rect(32f, 21.5f, 68f, 42.5f)
    val windowRR = RoundRect(windowRect, CornerRadius(2.2f))
    val windowPath = Path().apply { addRoundRect(windowRR) }
    clipPath(windowPath) {
        drawRect(Color(0xFF141518), windowRect.topLeft, windowRect.size)
        drawPack(HUB_L, reels.leftRadius, reels.leftAngle)
        drawPack(HUB_R, reels.rightRadius, reels.rightAngle)
        drawLine(DeckColors.Tape, Offset(HUB_L.x - reels.leftRadius, HUB_L.y), Offset(HUB_L.x - reels.leftRadius - 1f, 64f), 0.6f)
        drawLine(DeckColors.Tape, Offset(HUB_R.x + reels.rightRadius, HUB_R.y), Offset(HUB_R.x + reels.rightRadius + 1f, 64f), 0.6f)
        drawRect(Color(0xFF707884).copy(alpha = 0.16f), windowRect.topLeft, windowRect.size)
    }
    drawHub(HUB_L, reels.leftAngle, reels.leftDelta)
    drawHub(HUB_R, reels.rightAngle, reels.rightDelta)

    // Center scale tick marks [ 100 - 50 - 0 ]
    for (i in 0..4) {
        val tx = 42f + i * 4f
        drawCircle(Color.White.copy(alpha = 0.45f), 0.35f, Offset(tx, 32f))
    }
    drawPath(windowPath, Color.Black.copy(alpha = 0.6f), style = Stroke(0.45f))

    // TDK SA-90 J-Card Label
    val labelRR = RoundRect(11f, 8.5f, 89f, 21.5f, CornerRadius(1.2f))
    val labelPath = Path().apply { addRoundRect(labelRR) }
    clipPath(labelPath) {
        // Upper black header
        drawRect(Color(0xFF121316), Offset(11f, 8.5f), Size(78f, 6.5f))
        // Gold foil divider
        drawRect(Color(0xFFD4AF37), Offset(11f, 15f), Size(78f, 0.6f))
        // Red and Blue pinstripes
        drawRect(Color(0xFFE53935), Offset(11f, 15.6f), Size(78f, 0.35f))
        drawRect(Color(0xFF1E88E5), Offset(11f, 15.95f), Size(78f, 0.35f))
        // Cream write-in body
        drawRect(Color(0xFFF2EFE8), Offset(11f, 16.3f), Size(78f, 5.2f))

        // Album art stamp
        ui.artwork?.let { art ->
            drawCroppedImage(art, Rect(11.5f, 9f, 20f, 21f), inverted = inverted)
        }
    }
    drawPath(labelPath, Color.Black.copy(alpha = 0.4f), style = Stroke(0.3f))

    // Text silkscreen
    val goldStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, color = Color(0xFFD4AF37))
    val subStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
    val inkStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, color = Color(0xFF1E2024))

    val titleX = if (ui.artwork != null) 22f else 13f
    drawCassetteText("TDK", titleX, 9.2f, 3.2f, 16f, goldStyle, measurer, u, inverted)
    drawCassetteText("SA 90", titleX + 16f, 9.5f, 2.8f, 18f, goldStyle.copy(fontWeight = FontWeight.Bold), measurer, u, inverted)
    drawCassetteText("HIGH POSITION · 70µs EQ", 52f, 9.8f, 2.1f, 35f, subStyle, measurer, u, inverted)

    val songTitle = ui.title ?: placeholderTitle(ui)
    val artistName = ui.artist ?: ""
    drawCassetteText(songTitle, titleX, 16.6f, 2.5f, 65f, inkStyle, measurer, u, inverted)
    if (artistName.isNotBlank()) {
        drawCassetteText(artistName, 60f, 16.6f, 2.2f, 26f, inkStyle.copy(fontWeight = FontWeight.Normal), measurer, u, inverted)
    }

    drawBottomBridge(shell, light = false)
    listOf(Offset(4.4f, 4.4f), Offset(95.6f, 4.4f), Offset(4.4f, 59.6f), Offset(95.6f, 59.6f), Offset(50f, 55.5f))
        .forEachIndexed { i, c -> drawScrew(c, 1.5f, 15f + i * 40f) }
    drawPath(shellPath, Color.Black.copy(alpha = 0.5f), style = Stroke(0.35f))
}

/** 2. Maxell XLII-S (1988): Solid opaque black shell with small central viewing window and optical reticle (photo 1). */
private fun DrawScope.drawMaxellXlIIsCassette(
    ui: DeckUi,
    reels: ReelMotion,
    shellTint: Color,
    measurer: TextMeasurer,
    u: Float,
    inverted: Boolean = false,
) {
    val shell = Color(0xFF141518)
    val shellRR = RoundRect(0f, 0f, 100f, 64f, CornerRadius(3.4f))
    val shellPath = Path().apply { addRoundRect(shellRR) }

    // 1. Solid opaque black shell body
    translate(0.8f, 1.6f) { drawPath(shellPath, Color.Black.copy(alpha = 0.55f)) }
    drawPath(shellPath, Brush.verticalGradient(listOf(Color(0xFF1E2024), shell, Color(0xFF0F1012)), 0f, 64f))

    // 2. Top Recessed Header Bay in solid black shell
    val bayRect = Rect(8.5f, 6.5f, 91.5f, 18.5f)
    val bayRR = RoundRect(bayRect, CornerRadius(1.2f))
    val bayPath = Path().apply { addRoundRect(bayRR) }
    drawPath(bayPath, Color(0xFF0D0E10))
    drawPath(bayPath, Color.White.copy(alpha = 0.10f), style = Stroke(0.3f))

    var textStartX = 11.5f
    ui.artwork?.let { art ->
        val artRect = Rect(10f, 7.5f, 20f, 17.5f)
        drawCroppedImage(art, artRect, inverted = inverted)
        drawRect(Color.White.copy(alpha = 0.2f), artRect.topLeft, artRect.size, style = Stroke(0.25f))
        textStartX = 22.5f
    }

    val gold = Color(0xFFD4AF37)
    val goldMech = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, color = gold)
    val goldSub = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, color = gold.copy(alpha = 0.9f))

    drawCassetteText("Super Silent-Phase Accuracy Cassette Mechanism", textStartX, 8.2f, 2.1f, 78f, goldMech, measurer, u, inverted)
    drawCassetteText("EXTENDED DYNAMIC RANGE · EXCELLENT LOW NOISE", textStartX, 12.8f, 1.65f, 78f, goldSub, measurer, u, inverted)

    // 3. Small Central Transparent Window (STRICTLY between the hubs, 37mm to 63mm)
    val windowRect = Rect(37.0f, 22.5f, 63.0f, 41.5f)
    val windowRR = RoundRect(windowRect, CornerRadius(1.2f))
    val windowPath = Path().apply { addRoundRect(windowRR) }
    clipPath(windowPath) {
        // Dark tape chamber interior behind the small window
        drawRect(Color(0xFF0B0C0E), windowRect.topLeft, windowRect.size)

        // Only the inner portions of the tape packs that enter this 26mm window are visible
        drawPack(HUB_L, reels.leftRadius, reels.leftAngle)
        drawPack(HUB_R, reels.rightRadius, reels.rightAngle)

        // Bottom tape ribbon across window
        drawLine(DeckColors.Tape, Offset(37.0f, 39.5f), Offset(63.0f, 39.5f), 0.6f)

        // Smoked clear acrylic window layer
        drawRect(Color(0xFF1E2228).copy(alpha = 0.16f), windowRect.topLeft, windowRect.size)

        // Optical Alignment Crosshair Reticle (photo 1)
        drawLine(Color.White.copy(alpha = 0.45f), Offset(50f, 23.0f), Offset(50f, 41.0f), 0.35f)
        drawCircle(Color.White.copy(alpha = 0.65f), radius = 1.3f, center = Offset(50f, 32f), style = Stroke(0.35f))
        for (dotX in listOf(39f, 41.5f, 44f, 46.5f, 53.5f, 56f, 58.5f, 61f)) {
            drawCircle(Color.White.copy(alpha = 0.45f), radius = 0.35f, center = Offset(dotX, 32f))
        }
    }
    // Window recessed border
    drawPath(windowPath, Color.White.copy(alpha = 0.18f), style = Stroke(0.35f))

    // 4. Physical Spindle Holes in the Solid Black Opaque Shell
    // Each hub hole has the rotating drive hub splines inside it,
    // and a faceted/hexagonal recessed collar molded into the black plastic
    for (hub in listOf(HUB_L, HUB_R)) {
        // Molded faceted/hexagonal collar on the black shell (photo 1)
        drawCircle(Color(0xFF1C1E22), 8.2f, hub, style = Stroke(1.2f))
        drawCircle(Color.Black.copy(alpha = 0.7f), 7.4f, hub, style = Stroke(0.6f))
        // Hole well background behind drive teeth
        drawCircle(Color(0xFF08090A), 5.8f, hub)
    }
    drawHub(HUB_L, reels.leftAngle, reels.leftDelta)
    drawHub(HUB_R, reels.rightAngle, reels.rightDelta)

    // 5. Direct Gold Silkscreen on Solid Black Shell (left and right of spools)
    val maxellLogo = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = gold)
    val xliisLogo = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, color = gold)
    val positionText = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, color = gold.copy(alpha = 0.9f))

    // Left of left spindle (ends before collar at 20.5mm): maxell lower-case serif logo
    drawCassetteText("maxell", 9.5f, 30.5f, 2.6f, 10f, maxellLogo, measurer, u, inverted)

    // Right of right spindle (starts after collar at 79.5mm): XLII-S 90 & POSITION HIGH
    drawCassetteText("XLII-S 90", 80.5f, 28.5f, 2.5f, 10f, xliisLogo, measurer, u, inverted)
    drawCassetteText("POSITION · HIGH", 80.5f, 32.5f, 1.35f, 10f, positionText, measurer, u, inverted)

    // 6. Inscribed Song Title at Bottom
    val songTitle = ui.title ?: placeholderTitle(ui)
    drawCassetteText(songTitle, 13f, 47.0f, 2.7f, 74f, goldMech.copy(color = Color.White), measurer, u, inverted)
    drawCassetteText("HIGH BIAS 70µs EQ · PURE EPITAXIAL", 13f, 51.5f, 1.85f, 60f, goldSub, measurer, u, inverted)

    // 7. Bottom Bridge & Screws
    drawBottomBridge(shell, light = false)
    listOf(Offset(4.4f, 4.4f), Offset(95.6f, 4.4f), Offset(4.4f, 59.6f), Offset(95.6f, 59.6f), Offset(50f, 55.5f))
        .forEachIndexed { i, c -> drawScrew(c, 1.5f, 15f + i * 40f) }
    drawPath(shellPath, Color.Black.copy(alpha = 0.5f), style = Stroke(0.35f))
}

/** 3. Maxell UD II (1990): High Resonance Damping Cassette Mechanism with signature wide curved oval window (photo 2). */
private fun DrawScope.drawMaxellUdIICassette(
    ui: DeckUi,
    reels: ReelMotion,
    shellTint: Color,
    measurer: TextMeasurer,
    u: Float,
    inverted: Boolean = false,
) {
    val shell = Color(0xFF121316)
    val shellRR = RoundRect(0f, 0f, 100f, 64f, CornerRadius(3.4f))
    val shellPath = Path().apply { addRoundRect(shellRR) }

    // 1. Solid black shell body
    translate(0.8f, 1.6f) { drawPath(shellPath, Color.Black.copy(alpha = 0.5f)) }
    drawPath(shellPath, Brush.verticalGradient(listOf(Color(0xFF1E2024), shell, Color(0xFF0C0D0F)), 0f, 64f))

    // Dual-surface resonance damping vertical grip ribs
    for (i in 0 until 14) {
        val y = 14f + i * 2.5f
        drawLine(Color.White.copy(alpha = 0.08f), Offset(2.2f, y), Offset(5.5f, y), 0.6f)
        drawLine(Color.White.copy(alpha = 0.08f), Offset(94.5f, y), Offset(97.8f, y), 0.6f)
    }

    // 2. Iconic Ultra-Wide Oval Stadium Window (encompassing both spools with 11.2mm clearance)
    // Semicircles of radius 17mm centered at HUB_L (28.75, 32) and HUB_R (71.25, 32)
    val ovalLeft = HUB_L.x - 17.0f   // 11.75f
    val ovalRight = HUB_R.x + 17.0f  // 88.25f
    val ovalTop = 32.0f - 17.0f      // 15.0f
    val ovalBottom = 32.0f + 17.0f   // 49.0f
    val ovalRect = Rect(ovalLeft, ovalTop, ovalRight, ovalBottom)
    val ovalRR = RoundRect(ovalRect, CornerRadius(17.0f))
    val ovalPath = Path().apply { addRoundRect(ovalRR) }

    clipPath(ovalPath) {
        // Dark interior bay behind the clear acrylic oval
        drawRect(Color(0xFF0B0C0E), ovalRect.topLeft, ovalRect.size)

        // Full tape packs visible through the wide oval
        drawPack(HUB_L, reels.leftRadius, reels.leftAngle)
        drawPack(HUB_R, reels.rightRadius, reels.rightAngle)

        // Tape ribbon connecting across bottom
        drawLine(DeckColors.Tape, Offset(HUB_L.x - reels.leftRadius, HUB_L.y), Offset(HUB_L.x - reels.leftRadius - 1f, 64f), 0.6f)
        drawLine(DeckColors.Tape, Offset(HUB_R.x + reels.rightRadius, HUB_R.y), Offset(HUB_R.x + reels.rightRadius + 1f, 64f), 0.6f)

        // Clear acrylic window layer
        drawRect(Color(0xFF20242C).copy(alpha = 0.20f), ovalRect.topLeft, ovalRect.size)

        // Direct silkscreen INSIDE the clear window (as on real UD II)
        val gold = Color(0xFFD4AF37)
        val goldBold = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, color = gold)
        val goldReg = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, color = gold.copy(alpha = 0.85f))

        drawCassetteText("High Resonance Damping Cassette Mechanism", 16f, 16.5f, 1.8f, 68f, goldReg, measurer, u, inverted)
        drawCassetteText("EXTENDED RANGE · LOW NOISE", 16f, 19.5f, 1.5f, 68f, goldReg, measurer, u, inverted)

        // Left of HUB_L inside window (ends before spindle hole at 22.95mm): maxell logo
        drawCassetteText("maxell", 8.0f, 30.5f, 2.2f, 10.5f, goldBold, measurer, u, inverted)

        // Center bottom inside window: POSITION HIGH & UD II 90 (cleanly between hubs)
        drawCassetteText("POSITION HIGH", 42f, 41.0f, 1.6f, 26f, goldReg, measurer, u, inverted)
        drawCassetteText("UD II 90", 42f, 43.8f, 2.6f, 26f, goldBold.copy(fontWeight = FontWeight.Black), measurer, u, inverted)

        // Inset album art square in left corner of window (cleanly outside left spindle hole)
        ui.artwork?.let { art ->
            val artRect = Rect(12.5f, 16.5f, 19.5f, 23.5f)
            drawRoundRect(Color.Black, Offset(artRect.left, artRect.top), Size(artRect.width, artRect.height), CornerRadius(1f))
            drawCroppedImage(art, artRect, inverted = inverted)
            drawRoundRect(gold.copy(alpha = 0.5f), Offset(artRect.left, artRect.top), Size(artRect.width, artRect.height), CornerRadius(1f), style = Stroke(0.3f))
        }
    }

    // Physical Spindle Holes inside the clear window:
    // Frosted teflon circular washer around each spindle hole
    for (hub in listOf(HUB_L, HUB_R)) {
        drawCircle(Color.White.copy(alpha = 0.35f), 6.8f, hub, style = Stroke(0.8f))
        drawCircle(Color.Black.copy(alpha = 0.5f), 5.8f, hub)
    }
    drawHub(HUB_L, reels.leftAngle, reels.leftDelta)
    drawHub(HUB_R, reels.rightAngle, reels.rightDelta)

    // 3. Gold Bezel on the Oval Stadium (11.2mm away from the spindle holes, NEVER overlaps!)
    drawPath(ovalPath, Color(0xFFD4AF37).copy(alpha = 0.85f), style = Stroke(0.6f))
    drawPath(ovalPath, Color.White.copy(alpha = 0.25f), style = Stroke(0.2f))

    // 4. Inscribed Song Title at Bottom
    val gold = Color(0xFFD4AF37)
    val goldBold = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, color = gold)
    val songTitle = ui.title ?: placeholderTitle(ui)
    drawCassetteText(songTitle, 13f, 51.5f, 2.7f, 74f, goldBold.copy(color = Color.White), measurer, u, inverted)

    // 5. Bottom Bridge & Screws
    drawBottomBridge(shell, light = false)
    listOf(Offset(4.4f, 4.4f), Offset(95.6f, 4.4f), Offset(4.4f, 59.6f), Offset(95.6f, 59.6f), Offset(50f, 55.5f))
        .forEachIndexed { i, c -> drawScrew(c, 1.5f, 15f + i * 40f) }
    drawPath(shellPath, Color.Black.copy(alpha = 0.5f), style = Stroke(0.35f))
}

/** 3. Sony UX-Pro (1986): Smoked translucent body with white ceramic tape guide. */
private fun DrawScope.drawSonyUxProCassette(
    ui: DeckUi,
    reels: ReelMotion,
    shellTint: Color,
    measurer: TextMeasurer,
    u: Float,
    inverted: Boolean = false,
) {
    val shellRR = RoundRect(0f, 0f, 100f, 64f, CornerRadius(3.4f))
    val shellPath = Path().apply { addRoundRect(shellRR) }

    // 1. Bay floor behind smoked shell
    drawPath(shellPath, Color(0xFF0F1114))

    // 2. Visible tape packs through smoked shell
    drawPack(HUB_L, reels.leftRadius, reels.leftAngle)
    drawPack(HUB_R, reels.rightRadius, reels.rightAngle)
    drawLine(DeckColors.Tape, Offset(HUB_L.x - reels.leftRadius, HUB_L.y), Offset(HUB_L.x - reels.leftRadius - 1f, 64f), 0.6f)
    drawLine(DeckColors.Tape, Offset(HUB_R.x + reels.rightRadius, HUB_R.y), Offset(HUB_R.x + reels.rightRadius + 1f, 64f), 0.6f)

    // Flanges
    drawMaXgFlange(HUB_L, reels.leftAngle, reels.leftDelta, hasRedClamp = false)
    drawMaXgFlange(HUB_R, reels.rightAngle, reels.rightDelta, hasRedClamp = false)

    // 3. Smoked translucent polycarbonate body
    drawPath(
        shellPath,
        Brush.verticalGradient(
            listOf(Color(0xFF22262C).copy(alpha = 0.36f), Color(0xFF16181C).copy(alpha = 0.28f), Color(0xFF1E2024).copy(alpha = 0.38f)),
            0f,
            64f,
        ),
    )

    // 4. Sony White Ceramic Tape Guide (Key signature feature of UX-Pro)
    val ceramicGuide = RoundRect(Rect(39f, 52.5f, 61f, 63.5f), CornerRadius(1.2f))
    val ceramicPath = Path().apply { addRoundRect(ceramicGuide) }
    drawPath(ceramicPath, Color(0xFFF2F4F7))
    drawPath(ceramicPath, Color(0xFFCFD5DC), style = Stroke(0.35f))
    val ceramicStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, color = Color(0xFF455A64))
    drawCassetteText("CERAMIC GUIDE", 40.5f, 55.5f, 1.8f, 19f, ceramicStyle, measurer, u, inverted)

    // 5. Header strip with artwork and Sony UX-Pro silkscreen
    val headerStrip = RoundRect(11f, 11f, 89f, 17f, CornerRadius(1.2f))
    val stripPath = Path().apply { addRoundRect(headerStrip) }
    clipPath(stripPath) {
        drawRect(Color(0xFF16181C).copy(alpha = 0.88f), Offset(11f, 11f), Size(78f, 6f))
        ui.artwork?.let { art ->
            drawCroppedImage(art, Rect(11f, 11f, 19f, 17f), inverted = inverted)
        }
        drawRect(Color(0xFF00E676), Offset(11f, 16.4f), Size(78f, 0.6f))
    }
    drawPath(stripPath, Color.White.copy(alpha = 0.2f), style = Stroke(0.25f))

    val sonyWhite = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, color = Color.White)
    val sonySub = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, color = Color(0xFFCFD8DC))

    val titleX = if (ui.artwork != null) 21f else 13f
    drawCassetteText("SONY", titleX, 11.4f, 2.7f, 14f, sonyWhite, measurer, u, inverted)
    drawCassetteText("UX-Pro 90", titleX + 15f, 11.6f, 2.3f, 18f, sonyWhite, measurer, u, inverted)
    drawCassetteText("HIGH BIAS 70µs EQ", 58f, 11.8f, 1.8f, 30f, sonySub, measurer, u, inverted)

    val songTitle = ui.title ?: placeholderTitle(ui)
    drawCassetteText(songTitle, titleX, 14.2f, 2.2f, 76f - titleX, sonyWhite, measurer, u, inverted)

    drawBottomBridge(Color(0xFF1A1C20), light = false)
    listOf(Offset(4.4f, 4.4f), Offset(95.6f, 4.4f), Offset(4.4f, 59.6f), Offset(95.6f, 59.6f), Offset(50f, 55.5f))
        .forEachIndexed { i, c -> drawScrew(c, 1.5f, 15f + i * 40f) }
    drawPath(shellPath, Color.Black.copy(alpha = 0.5f), style = Stroke(0.35f))
}

/** 4. Sony HF 90 (1985): Classic light warm-grey shell with iconic orange/red racing stripes. */
private fun DrawScope.drawSonyHfCassette(
    ui: DeckUi,
    reels: ReelMotion,
    shellTint: Color,
    measurer: TextMeasurer,
    u: Float,
    inverted: Boolean = false,
) {
    val shell = Color(0xFFD2D5D9)
    val shellRR = RoundRect(0f, 0f, 100f, 64f, CornerRadius(3.4f))
    val shellPath = Path().apply { addRoundRect(shellRR) }

    translate(0.8f, 1.6f) { drawPath(shellPath, Color.Black.copy(alpha = 0.35f)) }
    drawPath(shellPath, Brush.verticalGradient(listOf(Color(0xFFE2E5E9), shell, Color(0xFFBFC2C6)), 0f, 64f))

    // Center Window
    val windowRR = RoundRect(WINDOW, CornerRadius(3.2f))
    val windowPath = Path().apply { addRoundRect(windowRR) }
    clipPath(windowPath) {
        drawRect(Color(0xFF1E1A16), WINDOW.topLeft, WINDOW.size)
        drawPack(HUB_L, reels.leftRadius, reels.leftAngle)
        drawPack(HUB_R, reels.rightRadius, reels.rightAngle)
        drawLine(DeckColors.Tape, Offset(HUB_L.x - reels.leftRadius, HUB_L.y), Offset(HUB_L.x - reels.leftRadius - 1f, 64f), 0.6f)
        drawLine(DeckColors.Tape, Offset(HUB_R.x + reels.rightRadius, HUB_R.y), Offset(HUB_R.x + reels.rightRadius + 1f, 64f), 0.6f)
        drawRect(Color(0xFF6F7680).copy(alpha = 0.18f), WINDOW.topLeft, WINDOW.size)
    }
    drawHub(HUB_L, reels.leftAngle, reels.leftDelta)
    drawHub(HUB_R, reels.rightAngle, reels.rightDelta)
    drawPath(windowPath, Color.Black.copy(alpha = 0.5f), style = Stroke(0.5f))

    // Sony HF Paper Label with Orange & Crimson Racing Stripes
    val labelRR = RoundRect(11f, 8.5f, 89f, 21.5f, CornerRadius(1.2f))
    val labelPath = Path().apply { addRoundRect(labelRR) }
    clipPath(labelPath) {
        drawRect(Color(0xFFF7F8FA), Offset(11f, 8.5f), Size(78f, 13f))
        // Bold Sony HF Racing Stripes
        drawRect(Color(0xFFFF6D00), Offset(11f, 8.5f), Size(78f, 2.2f))
        drawRect(Color(0xFFD50000), Offset(11f, 10.7f), Size(78f, 2.0f))

        ui.artwork?.let { art ->
            drawCroppedImage(art, Rect(11.5f, 8.5f, 20f, 21.5f), inverted = inverted)
        }
    }
    drawPath(labelPath, Color.Black.copy(alpha = 0.35f), style = Stroke(0.3f))

    val blackBold = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, color = Color(0xFF181A1C))
    val blackSub = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, color = Color(0xFF37474F))

    val titleX = if (ui.artwork != null) 22f else 13f
    drawCassetteText("SONY", titleX, 13.5f, 3.2f, 16f, blackBold, measurer, u, inverted)
    drawCassetteText("HF 90", titleX + 16f, 13.8f, 2.8f, 18f, blackBold, measurer, u, inverted)
    drawCassetteText("NORMAL BIAS 120µs EQ", 52f, 14.2f, 2.0f, 35f, blackSub, measurer, u, inverted)

    val songTitle = ui.title ?: placeholderTitle(ui)
    drawCassetteText(songTitle, 13f, 47.5f, 2.8f, 74f, blackBold, measurer, u, inverted)

    drawBottomBridge(shell, light = true)
    listOf(Offset(4.4f, 4.4f), Offset(95.6f, 4.4f), Offset(4.4f, 59.6f), Offset(95.6f, 59.6f), Offset(50f, 55.5f))
        .forEachIndexed { i, c -> drawScrew(c, 1.5f, 15f + i * 40f) }
    drawPath(shellPath, Color.Black.copy(alpha = 0.45f), style = Stroke(0.35f))
}

/** 5. TEAC Sound 52 (1984): Iconic open-reel cassette with spinning aluminum flanges (tapedeck.org). */
private fun DrawScope.drawTeacSound52Cassette(
    ui: DeckUi,
    reels: ReelMotion,
    shellTint: Color,
    measurer: TextMeasurer,
    u: Float,
    inverted: Boolean = false,
) {
    val shellRR = RoundRect(0f, 0f, 100f, 64f, CornerRadius(3.4f))
    val shellPath = Path().apply { addRoundRect(shellRR) }

    // 1. Dark cassette bay floor behind crystal-clear shell
    drawPath(shellPath, Color(0xFF101214))

    // Internal structural molding lines of the clear shell
    drawLine(Color.White.copy(alpha = 0.08f), Offset(10f, 21.5f), Offset(90f, 21.5f), 0.35f)
    drawLine(Color.White.copy(alpha = 0.08f), Offset(10f, 43.5f), Offset(90f, 43.5f), 0.35f)
    drawLine(Color.White.copy(alpha = 0.06f), Offset(50f, 12f), Offset(50f, 52f), 0.35f)

    // 2. Full magnetic tape packs visible through crystal-clear shell
    drawPack(HUB_L, reels.leftRadius, reels.leftAngle)
    drawPack(HUB_R, reels.rightRadius, reels.rightAngle)

    // Tape path running from packs down to guide rollers
    drawLine(DeckColors.Tape, Offset(HUB_L.x - reels.leftRadius, HUB_L.y), Offset(HUB_L.x - reels.leftRadius - 1f, 64f), 0.6f)
    drawLine(DeckColors.Tape, Offset(HUB_R.x + reels.rightRadius, HUB_R.y), Offset(HUB_R.x + reels.rightRadius + 1f, 64f), 0.6f)

    // Guide rollers in lower corners
    drawCircle(Color(0xFFE4E8DF), 2.2f, Offset(16.5f, 55.5f))
    drawCircle(Color(0xFF8A909A), 0.9f, Offset(16.5f, 55.5f))
    drawCircle(Color(0xFFE4E8DF), 2.2f, Offset(83.5f, 55.5f))
    drawCircle(Color(0xFF8A909A), 0.9f, Offset(83.5f, 55.5f))

    // 3. Rotating Open-Reel Spun Aluminum Flanges!
    drawTeacReelFlange(HUB_L, reels.leftAngle, reels.leftDelta, measurer, u, hasRedClamp = true, isLeft = true)
    drawTeacReelFlange(HUB_R, reels.rightAngle, reels.rightDelta, measurer, u, hasRedClamp = false, isLeft = false)

    // 4. Crystal-Clear Front Polycarbonate Top Shell Layer
    drawPath(
        shellPath,
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.04f),
                Color.Black.copy(alpha = 0.15f),
                Color.White.copy(alpha = 0.09f),
            ),
            0f,
            64f,
        ),
    )

    // Molded grid-matrix texture along left & right perimeter edges
    for (row in 0 until 14) {
        val y = 14f + row * 2.6f
        for (col in 0 until 3) {
            val xl = 2.0f + col * 1.5f
            val xr = 98.0f - col * 1.5f
            drawCircle(Color.White.copy(alpha = 0.07f), 0.4f, Offset(xl, y))
            drawCircle(Color.Black.copy(alpha = 0.25f), 0.4f, Offset(xl + 0.15f, y + 0.15f))
            drawCircle(Color.White.copy(alpha = 0.07f), 0.4f, Offset(xr, y))
            drawCircle(Color.Black.copy(alpha = 0.25f), 0.4f, Offset(xr + 0.15f, y + 0.15f))
        }
    }

    // Center scale window outline
    val centerWindow = RoundRect(Rect(37f, 26f, 63f, 38f), CornerRadius(1.6f))
    drawRoundRect(
        color = Color.White.copy(alpha = 0.18f),
        topLeft = Offset(centerWindow.left, centerWindow.top),
        size = Size(centerWindow.width, centerWindow.height),
        cornerRadius = CornerRadius(1.6f),
        style = Stroke(0.35f),
    )
    for (i in 0..4) {
        val cx = 43f + i * 3.5f
        drawCircle(Color.White.copy(alpha = 0.35f), 0.35f, Offset(cx, 32f))
    }

    // 5. Classic TEAC Write-In Paper Label Strip at Top
    val labelRect = Rect(10.5f, 7.5f, 89.5f, 21.0f)
    val labelRR = RoundRect(labelRect, CornerRadius(1.4f))
    val labelPath = Path().apply { addRoundRect(labelRR) }
    clipPath(labelPath) {
        // Off-white / light vintage cream label paper
        drawRect(Color(0xFFF6F7F3), labelRect.topLeft, labelRect.size)

        // Left olive green side badge [ A ] / [ B ]
        val side = if (ui.reverseDir) "B" else "A"
        drawRect(Color(0xFF435A3E), Offset(10.5f, 7.5f), Size(11.5f, 13.5f))
        val sideLayout = measurer.measure(
            side,
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Black,
                fontSize = (7.5f * u).toSp(),
                color = Color.White,
            ),
        )
        val stw = sideLayout.size.width / u
        val sth = sideLayout.size.height / u
        withTransform({
            translate(10.5f + (11.5f - stw) / 2f, 7.5f + (13.5f - sth) / 2f)
            if (inverted) {
                rotate(180f, Offset(stw / 2f, sth / 2f))
            }
            scale(1f / u, 1f / u, Offset.Zero)
        }) { drawText(sideLayout) }

        // Album Artwork thumbnail next to the Side badge
        var textStartX = 24f
        ui.artwork?.let { art ->
            drawCroppedImage(art, Rect(23f, 8.5f, 34f, 19.5f), inverted = inverted)
            textStartX = 36f
        }

        // Ruled lines for handwritten song title
        drawLine(Color(0xFFB0BEC5).copy(alpha = 0.5f), Offset(textStartX, 13.8f), Offset(74f, 13.8f), 0.35f)
        drawLine(Color(0xFFB0BEC5).copy(alpha = 0.5f), Offset(textStartX, 18.5f), Offset(74f, 18.5f), 0.35f)

        // Right side: N.R. box & 52 min badge
        drawRect(Color(0xFF1E2228), Offset(76.5f, 8.5f), Size(11.5f, 11.5f))
        val min52Layout = measurer.measure(
            "52",
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Black,
                fontSize = (5.5f * u).toSp(),
                color = Color.White,
            ),
        )
        val mtw = min52Layout.size.width / u
        val minSubLayout = measurer.measure(
            "min",
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = (2.2f * u).toSp(),
                color = Color(0xFFB0BEC5),
            ),
        )
        val mstw = minSubLayout.size.width / u
        withTransform({
            if (inverted) {
                rotate(180f, Offset(76.5f + 5.75f, 8.5f + 5.75f))
            }
        }) {
            withTransform({
                translate(76.5f + (11.5f - mtw) / 2f, 8.5f + 1.2f)
                scale(1f / u, 1f / u, Offset.Zero)
            }) { drawText(min52Layout) }

            withTransform({
                translate(76.5f + (11.5f - mstw) / 2f, 8.5f + 7.0f)
                scale(1f / u, 1f / u, Offset.Zero)
            }) { drawText(minSubLayout) }
        }
    }
    drawPath(labelPath, Color.Black.copy(alpha = 0.4f), style = Stroke(0.3f))

    // Song Title written in authentic vintage dark blue ink on the ruled line
    val blueInk = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF10284C),
    )
    val songTitle = ui.title ?: placeholderTitle(ui)
    val titleX = if (ui.artwork != null) 36f else 24f
    drawCassetteText(songTitle, titleX, 10.2f, 3.0f, 50f, blueInk, measurer, u, inverted)

    // Silkscreened silver/white logo on clear plastic below label
    // 6. Bottom Bridge, Screws & Outer Stroke
    drawBottomBridge(Color(0xFF141618), light = false)
    listOf(Offset(4.4f, 4.4f), Offset(95.6f, 4.4f), Offset(4.4f, 59.6f), Offset(95.6f, 59.6f), Offset(50f, 55.5f))
        .forEachIndexed { i, c -> drawScrew(c, 1.5f, 15f + i * 40f) }
    drawPath(shellPath, Color.White.copy(alpha = 0.25f), style = Stroke(0.35f))
}

/** Draws the open-reel aluminum flange of the TEAC Sound 52 cassette. */
private fun DrawScope.drawTeacReelFlange(
    center: Offset,
    angle: Float,
    delta: Float,
    measurer: TextMeasurer,
    u: Float,
    hasRedClamp: Boolean = false,
    isLeft: Boolean = true,
) {
    val outerR = 17.5f
    val innerR = 7.2f

    // 1. Drop shadow under the aluminum flange onto the tape pack
    drawCircle(
        color = Color.Black.copy(alpha = 0.55f),
        radius = outerR + 0.3f,
        center = center + Offset(0.2f, 0.4f),
    )

    // 2. Solid spun aluminum disc
    drawCircle(
        brush = Brush.sweepGradient(
            listOf(
                Color(0xFFE4E7ED),
                Color(0xFFBAC0C8),
                Color(0xFFF2F5F8),
                Color(0xFFA2A8B2),
                Color(0xFFE8EBF0),
                Color(0xFFB0B6BE),
                Color(0xFFF4F7FA),
                Color(0xFF9EA4AE),
                Color(0xFFE4E7ED),
            ),
            center = center,
        ),
        radius = outerR,
        center = center,
    )

    // Outer edge bevel rings
    drawCircle(Color.White.copy(alpha = 0.85f), radius = outerR, center = center, style = Stroke(0.35f))
    drawCircle(Color.Black.copy(alpha = 0.35f), radius = outerR - 0.35f, center = center, style = Stroke(0.25f))

    // Inner hub bevel rings
    drawCircle(Color.Black.copy(alpha = 0.45f), radius = innerR, center = center, style = Stroke(0.3f))
    drawCircle(Color.White.copy(alpha = 0.7f), radius = innerR + 0.3f, center = center, style = Stroke(0.2f))

    // 3. Rotating Flange Assembly (3 cutout slots + 3 solid printed sectors)
    rotate(angle, center) {
        val slotR1 = 12.2f
        val slotR2 = 16.5f

        // Draw 3 curved window cutouts revealing the dark tape pack beneath
        // Cutouts are centered at 60°, 180°, and 300° (alternating perfectly with the sectors at 0°, 120°, 240°)
        for (i in 0 until 3) {
            rotate(60f + i * 120f, center) {
                // Curved trapezoidal slot path centered at local 12 o'clock (-90°) spanning -19° to +19° (38° total)
                val slotPath = Path().apply {
                    arcTo(
                        rect = Rect(center.x - slotR2, center.y - slotR2, center.x + slotR2, center.y + slotR2),
                        startAngleDegrees = -109f,
                        sweepAngleDegrees = 38f,
                        forceMoveTo = true,
                    )
                    arcTo(
                        rect = Rect(center.x - slotR1, center.y - slotR1, center.x + slotR1, center.y + slotR1),
                        startAngleDegrees = -71f,
                        sweepAngleDegrees = -38f,
                        forceMoveTo = false,
                    )
                    close()
                }

                // Dark tape pack visible through the cutout window
                drawPath(slotPath, Color(0xFF141210))

                // Concentric tape layer lines inside the cutout
                drawArc(
                    color = Color.White.copy(alpha = 0.08f),
                    startAngle = -107f,
                    sweepAngle = 34f,
                    useCenter = false,
                    topLeft = Offset(center.x - 14.4f, center.y - 14.4f),
                    size = Size(28.8f, 28.8f),
                    style = Stroke(0.35f),
                )

                // Cutout inner shadow & silver beveled rim
                drawPath(slotPath, Color.Black.copy(alpha = 0.5f), style = Stroke(0.35f))
                drawPath(slotPath, Color.White.copy(alpha = 0.55f), style = Stroke(0.2f))

                // Red tape clamp wedge visible through one of the cutouts
                if (hasRedClamp && i == 0) {
                    drawArc(
                        color = Color(0xFFE03C24),
                        startAngle = -102f,
                        sweepAngle = 24f,
                        useCenter = false,
                        topLeft = Offset(center.x - slotR1 - 0.5f, center.y - slotR1 - 0.5f),
                        size = Size((slotR1 + 0.5f) * 2f, (slotR1 + 0.5f) * 2f),
                        style = Stroke(1.8f),
                    )
                }

                // Tiny screw rivet near the inner edge between cutouts
                val sc = Offset(center.x, center.y - 8.2f)
                drawCircle(Color(0xFF2A2D32), radius = 0.45f, center = sc)
                drawCircle(Color(0xFFE2E5EA), radius = 0.32f, center = sc)
                drawLine(Color(0xFF1A1C1F), sc - Offset(0.2f, 0f), sc + Offset(0.2f, 0f), 0.12f)
            }
        }

        // 4. Black silkscreen typography printed strictly inside the 3 broad solid silver sectors
        // The solid sectors are centered at 0°, 120°, and 240° (each exactly halfway between cutouts)
        for (i in 0 until 3) {
            rotate(i * 120f, center) {
                // Indexing pinhole in solid sector near inner hub
                val pinholeY = center.y - 9.0f
                drawCircle(Color(0xFF0F1012), radius = 0.65f, center = Offset(center.x, pinholeY))
                drawCircle(Color.White.copy(alpha = 0.6f), radius = 0.65f, center = Offset(center.x, pinholeY), style = Stroke(0.18f))

                when (i) {
                    0 -> {
                        // "TEAC" in authentic bold serif typography
                        val teacLayout = measurer.measure(
                            "TEAC",
                            TextStyle(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Black,
                                fontSize = (2.6f * u).toSp(),
                                letterSpacing = 0.4.sp,
                                color = Color(0xFF141619),
                            ),
                        )
                        val tw = teacLayout.size.width / u
                        val th = teacLayout.size.height / u
                        withTransform({
                            translate(center.x - tw / 2f, center.y - 13.8f - th / 2f)
                            scale(1f / u, 1f / u, Offset.Zero)
                        }) { drawText(teacLayout) }
                    }
                    1 -> {
                        // "SOUND 52" in bold condensed sans-serif typography
                        val soundLayout = measurer.measure(
                            "SOUND",
                            TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = (1.8f * u).toSp(),
                                letterSpacing = 0.3.sp,
                                color = Color(0xFF141619),
                            ),
                        )
                        val numLayout = measurer.measure(
                            "52",
                            TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Black,
                                fontSize = (2.5f * u).toSp(),
                                color = Color(0xFF141619),
                            ),
                        )
                        val stw = soundLayout.size.width / u
                        val sth = soundLayout.size.height / u
                        val ntw = numLayout.size.width / u
                        val nth = numLayout.size.height / u
                        val totalH = sth + nth + 0.3f
                        val startY = center.y - 13.8f - totalH / 2f
                        withTransform({
                            translate(center.x - stw / 2f, startY)
                            scale(1f / u, 1f / u, Offset.Zero)
                        }) { drawText(soundLayout) }
                        withTransform({
                            translate(center.x - ntw / 2f, startY + sth + 0.3f)
                            scale(1f / u, 1f / u, Offset.Zero)
                        }) { drawText(numLayout) }
                    }
                    2 -> {
                        // "Bias/NORMAL" / "EQ/120µs" (as shown in reference image)
                        val line1 = measurer.measure(
                            "Bias/NORMAL",
                            TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = (1.3f * u).toSp(),
                                letterSpacing = 0.15.sp,
                                color = Color(0xFF141619),
                            ),
                        )
                        val line2 = measurer.measure(
                            "EQ/120µs",
                            TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = (1.3f * u).toSp(),
                                letterSpacing = 0.15.sp,
                                color = Color(0xFF141619),
                            ),
                        )
                        val l1w = line1.size.width / u
                        val l1h = line1.size.height / u
                        val l2w = line2.size.width / u
                        val l2h = line2.size.height / u
                        val totalH = l1h + l2h + 0.3f
                        val startY = center.y - 13.8f - totalH / 2f
                        withTransform({
                            translate(center.x - l1w / 2f, startY)
                            scale(1f / u, 1f / u, Offset.Zero)
                        }) { drawText(line1) }
                        withTransform({
                            translate(center.x - l2w / 2f, startY + l1h + 0.3f)
                            scale(1f / u, 1f / u, Offset.Zero)
                        }) { drawText(line2) }
                    }
                }
            }
        }
    }

    // 5. Center drive hub (6 white spline teeth + center drive pin)
    drawHub(center, angle, delta)
}
