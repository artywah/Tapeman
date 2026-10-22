package dev.foldman.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
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
                    if (ui.transparentShell) {
                        drawMaXgCassette(ui, anim.reels, shell, measurer, u)
                    } else {
                        drawClassicCassette(ui, anim.reels, shell, measurer, u)
                    }
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
            drawCroppedImage(art, Rect(11f, 12.6f, 20f, 18.2f))
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
        withTransform({ translate(x, y); scale(1f / u, 1f / u, Offset.Zero) }) { drawText(layout) }
    }

    val printBold = TextStyle(fontFamily = FontFamily.SansSerif, color = Color(0xFFF0F3F7), fontWeight = FontWeight.Bold)
    val printBlack = TextStyle(fontFamily = FontFamily.SansSerif, color = Color(0xFFFFFFFF), fontWeight = FontWeight.Black)
    val titleX = if (ui.artwork != null) 21.8f else 13.5f
    val title = ui.title.ifBlank { placeholderTitle(ui) }
    val trackLine = listOf(title, ui.artist).filter { it.isNotBlank() }.joinToString("  —  ")
    text(trackLine, titleX, 13.5f, 3.1f, 87f - titleX, printBold)

    // 6. Authentic Sony MA-XG46 white silkscreen typography on the right side (as in wm_rx707_front.jpg)
    text("Metal Position", 71.5f, 33.2f, 2.3f, 20f, printBold.copy(color = Color(0xFFDCE0E6)))
    text("TYPE IV", 75.0f, 35.8f, 2.2f, 16f, printBold.copy(color = Color(0xFFDCE0E6)))
    text("MA-XG46", 70.0f, 38.4f, 3.6f, 21f, printBlack)

    // Left side source & album badge
    val srcCode = when (ui.source) {
        Source.LOCAL -> "LOCAL · HI-BIAS"
        Source.PLEX -> "PLEX · DIRECT"
        Source.SPOTIFY -> "SPOTIFY · STREAM"
        Source.YT_MUSIC -> "YTM · SESSION"
        Source.APPLE_MUSIC -> "APPLE · MUSIC"
    }
    text(srcCode, 11.5f, 44.2f, 2.1f, 34f, printBold.copy(color = accent))
    if (ui.album.isNotBlank()) {
        text(ui.album.uppercase(), 44f, 44.2f, 2.1f, 44f, printBold.copy(color = Color(0xFFB8BEC6)))
    }

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

    drawLabel(ui, ui.label, measurer, windowRR, u)
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

private fun DrawScope.drawLabel(ui: DeckUi, palette: LabelPalette, measurer: TextMeasurer, window: RoundRect, u: Float) {
    val labelRect = RoundRect(5.5f, 4f, 94.5f, 48.5f, CornerRadius(2f))
    val labelPath = Path().apply {
        fillType = PathFillType.EvenOdd
        addRoundRect(labelRect)
        addRoundRect(RoundRect(window.left - 1f, window.top - 1f, window.right + 1f, window.bottom + 1f, CornerRadius(4f)))
    }
    val art = ui.artwork
    clipPath(labelPath) {
        if (art != null) {
            drawCroppedImage(art, Rect(labelRect.left, labelRect.top, labelRect.right, labelRect.bottom))
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
        withTransform({ translate(x, y); scale(1f / u, 1f / u, Offset.Zero) }) { drawText(layout) }
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
private fun DrawScope.drawCroppedImage(image: ImageBitmap, dst: Rect) {
    val dstRatio = dst.width / dst.height
    val srcRatio = image.width.toFloat() / image.height
    val (sw, sh) = if (srcRatio > dstRatio) (image.height * dstRatio) to image.height.toFloat() else image.width.toFloat() to (image.width / dstRatio)
    val sx = ((image.width - sw) / 2f).roundToInt()
    val sy = ((image.height - sh) * 0.4f).roundToInt()
    drawImage(
        image,
        srcOffset = IntOffset(sx, sy),
        srcSize = IntSize(sw.roundToInt(), sh.roundToInt()),
        dstOffset = IntOffset(dst.left.roundToInt(), dst.top.roundToInt()),
        dstSize = IntSize(dst.width.roundToInt().coerceAtLeast(1), dst.height.roundToInt().coerceAtLeast(1)),
    )
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
