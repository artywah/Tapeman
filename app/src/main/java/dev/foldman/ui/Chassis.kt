package dev.foldman.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldman.deck.Source
import kotlin.random.Random

object DeckColors {
    val Backdrop = Color(0xFF0E0E10)
    // WM-RX707 Matte Charcoal-Black Anodized Aluminum
    val BodyHi = Color(0xFF282A2E)
    val Body = Color(0xFF1C1D20)
    val BodyLo = Color(0xFF121315)
    // WM-RX707 Hammer-Tone Gunmetal Control Pod
    val PodHi = Color(0xFF666962)
    val Pod = Color(0xFF4F524C)
    val PodLo = Color(0xFF393B36)
    // Brushed Titanium Side/Top Trim
    val MetalHi = Color(0xFFDCDFE3)
    val Metal = Color(0xFFB4B8BE)
    val MetalLo = Color(0xFF7D8187)
    val Recess = Color(0xFF111214)
    val Engrave = Color(0xFF2D2F33)
    val EngraveLight = Color(0xFFC8CDD2)
    val PodSilkscreen = Color(0xFFD2D5CE)
    // Sony Recording Red & Amber/Green Indicators
    val Accent = Color(0xFFE04832)
    val AccentDeep = Color(0xFFB52E1C)
    val Led = Color(0xFFFF3B24)
    val LedDir = Color(0xFF5CE669)
    val Paper = Color(0xFFF2EBDC)
    val PaperLine = Color(0xFFB9CFE0)
    val Ink = Color(0xFF2A2522)
    val Tape = Color(0xFF2D1E16)

    fun shell(source: Source) = when (source) {
        Source.LOCAL -> Color(0xFF2E3136)
        Source.PLEX -> Color(0xFF3D301C)
        Source.SPOTIFY -> Color(0xFF1B3325)
        Source.YT_MUSIC -> Color(0xFF4A1E1C)
        Source.APPLE_MUSIC -> Color(0xFFDCD8D0)
    }

    fun sourceAccent(source: Source) = when (source) {
        Source.LOCAL -> Color(0xFFE04832)
        Source.PLEX -> Color(0xFFE5A00D)
        Source.SPOTIFY -> Color(0xFF1DB954)
        Source.YT_MUSIC -> Color(0xFFFF4E45)
        Source.APPLE_MUSIC -> Color(0xFFFA2D48)
    }
}

/**
 * Brushed titanium-aluminium trim with a bevelled edge and corner screws (used on the WM-RX707
 * top/side hardware bay).
 */
fun Modifier.chassis(corner: Dp = 22.dp, screws: Boolean = true): Modifier = drawWithCache {
    val r = corner.toPx()
    val rnd = Random(7)
    val hairlines = List(4) { Path() }
    var y = 0f
    while (y < size.height) {
        val x0 = rnd.nextFloat() * size.width * 0.2f
        val x1 = size.width - rnd.nextFloat() * size.width * 0.2f
        val p = hairlines[rnd.nextInt(4)]
        p.moveTo(x0, y)
        p.lineTo(x1, y)
        y += 0.6f + rnd.nextFloat() * 1.3f
    }
    val body = Brush.verticalGradient(
        0f to DeckColors.MetalHi,
        0.22f to DeckColors.Metal,
        0.75f to Color(0xFF989CA2),
        1f to DeckColors.MetalLo,
    )
    val sheen = Brush.linearGradient(
        0f to Color.Transparent,
        0.45f to Color.White.copy(alpha = 0.18f),
        0.55f to Color.Transparent,
        start = Offset(0f, 0f),
        end = Offset(size.width, size.height),
    )
    onDrawBehind {
        drawRoundRect(Color.Black.copy(alpha = 0.65f), topLeft = Offset(0f, 5f), cornerRadius = CornerRadius(r))
        drawRoundRect(body, cornerRadius = CornerRadius(r))
        val alphas = floatArrayOf(0.05f, 0.08f, 0.04f, 0.10f)
        hairlines.forEachIndexed { i, p ->
            drawPath(p, if (i % 2 == 0) Color.White.copy(alpha = alphas[i]) else Color.Black.copy(alpha = alphas[i]), style = Stroke(0.8f))
        }
        drawRoundRect(sheen, cornerRadius = CornerRadius(r))
        drawRoundRect(Color.White.copy(alpha = 0.65f), cornerRadius = CornerRadius(r), style = Stroke(2f))
        drawRoundRect(
            Color.Black.copy(alpha = 0.35f),
            topLeft = Offset(1.5f, 1.5f),
            size = Size(size.width - 3f, size.height - 3f),
            cornerRadius = CornerRadius(r - 1.5f),
            style = Stroke(1.5f),
        )
        if (screws) {
            val inset = (r * 0.62f).coerceAtLeast(10.dp.toPx())
            val sr = 4.5.dp.toPx()
            listOf(
                Offset(inset, inset), Offset(size.width - inset, inset),
                Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset),
            ).forEachIndexed { i, c -> drawScrew(c, sr, 20f + i * 37f) }
        }
    }
}

/**
 * Curved hammer-tone stippled gunmetal control island from the Sony WM-RX707.
 * Rises from the bottom edge with a smooth convex arch at the top and a recessed seam around it.
 */
fun Modifier.gunmetalPod(archHeightFraction: Float = 0.18f): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val archH = (h * archHeightFraction).coerceIn(14f, 56f)
    val cornerR = (w * 0.09f).coerceIn(18f, 42f)

    fun buildPodPath(inset: Float): Path = Path().apply {
        val left = inset
        val right = w - inset
        val bottom = h
        val shoulderY = archH + cornerR * 0.45f + inset
        moveTo(left, bottom)
        lineTo(left, shoulderY)
        // Left rounded shoulder into the top convex arch
        quadraticTo(left, archH * 0.48f + inset, left + cornerR, archH * 0.28f + inset)
        // Smooth convex arch across the top of the pod
        quadraticTo(w * 0.5f, -archH * 0.22f + inset, right - cornerR, archH * 0.28f + inset)
        // Right rounded shoulder down to the right edge
        quadraticTo(right, archH * 0.48f + inset, right, shoulderY)
        lineTo(right, bottom)
        close()
    }

    val outerTrench = buildPodPath(0f)
    val podPath = buildPodPath(3.dp.toPx())
    val highlightPath = buildPodPath(4.2.dp.toPx())

    // Stippled hammer-tone metallic flecks
    val rnd = Random(42)
    val lightFlecks = Path()
    val darkFlecks = Path()
    val count = ((w * h) / 140f).toInt().coerceAtMost(24000)
    repeat(count) {
        val x = rnd.nextFloat() * w
        val y = rnd.nextFloat() * h
        val s = 1.1f + rnd.nextFloat() * 1.1f
        if (rnd.nextBoolean()) {
            lightFlecks.addRect(Rect(x, y, x + s, y + s))
        } else {
            darkFlecks.addRect(Rect(x, y, x + s, y + s))
        }
    }

    val podFill = Brush.verticalGradient(
        0f to DeckColors.PodHi,
        0.25f to DeckColors.Pod,
        0.85f to Color(0xFF434640),
        1f to DeckColors.PodLo,
    )
    val archSheen = Brush.radialGradient(
        listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
        center = Offset(w * 0.5f, archH * 0.35f),
        radius = w * 0.55f,
    )

    onDrawBehind {
        // Deep recessed seam around the pod
        drawPath(outerTrench, Color(0xFF090A0B))
        drawPath(outerTrench, Color.White.copy(alpha = 0.12f), style = Stroke(1.2f))
        clipPath(podPath) {
            drawPath(podPath, podFill)
            drawPath(darkFlecks, Color.Black.copy(alpha = 0.22f))
            drawPath(lightFlecks, Color.White.copy(alpha = 0.09f))
            drawPath(podPath, archSheen)
        }
        // Moulded bevel rim along the curved arch
        drawPath(highlightPath, Color.White.copy(alpha = 0.28f), style = Stroke(1.6f))
        drawPath(podPath, Color.Black.copy(alpha = 0.65f), style = Stroke(2.2f))
    }
}

fun DrawScope.drawScrew(center: Offset, radius: Float, slotAngle: Float) {
    drawCircle(Color.Black.copy(alpha = 0.45f), radius * 1.15f, center + Offset(0f, radius * 0.15f))
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFEDEEF0), Color(0xFF9A9DA2), Color(0xFF5E6166)),
            center = center - Offset(radius * 0.3f, radius * 0.3f),
            radius = radius * 1.6f,
        ),
        radius,
        center,
    )
    rotate(slotAngle, center) {
        val w = radius * 0.28f
        drawLine(Color(0xFF35373B), center - Offset(radius * 0.75f, 0f), center + Offset(radius * 0.75f, 0f), w)
        drawLine(Color(0xFF35373B), center - Offset(0f, radius * 0.75f), center + Offset(0f, radius * 0.75f), w)
    }
}

/** A dark recessed panel set into the metal, for gauges and keys. */
fun Modifier.recess(corner: Dp = 14.dp): Modifier = drawWithCache {
    val r = CornerRadius(corner.toPx())
    val inner = Brush.verticalGradient(listOf(Color(0xFF090A0B), DeckColors.Recess, Color(0xFF1F2125)))
    onDrawBehind {
        drawRoundRect(Color.White.copy(alpha = 0.28f), topLeft = Offset(0f, 1.5f), cornerRadius = r)
        drawRoundRect(inner, size = Size(size.width, size.height - 1.5f), cornerRadius = r)
        drawRoundRect(Color.Black.copy(alpha = 0.75f), size = Size(size.width, size.height - 1.5f), cornerRadius = r, style = Stroke(2f))
    }
}

val EngravedCaps = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 9.sp,
    letterSpacing = 1.6.sp,
    color = DeckColors.Engrave,
)
