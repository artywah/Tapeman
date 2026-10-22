package dev.foldman.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/** Whether the keys buzz; the deck's HAPTIC switch sets it. */
val LocalHaptics = staticCompositionLocalOf { true }

/**
 * Lays the content out a quarter-turn anticlockwise (or clockwise when [clockwise] is true):
 * measured with width and height swapped, then rotated.
 */
fun Modifier.quarterTurn(enabled: Boolean = true, clockwise: Boolean = false): Modifier =
    if (!enabled) this else layout { measurable, c ->
        val p = measurable.measure(Constraints(c.minHeight, c.maxHeight, c.minWidth, c.maxWidth))
        layout(p.height, p.width) {
            p.placeWithLayer((p.height - p.width) / 2, (p.width - p.height) / 2) {
                rotationZ = if (clockwise) 90f else -90f
            }
        }
    }

/**
 * Matte charcoal-black anodized metal body of the Sony Walkman WM-RX707:
 * subtle satin sheen, micro-anodized grain, top chamfer highlight, and optional tactile blind-touch dots.
 */
fun Modifier.paintedBody(
    color: Color = DeckColors.Body,
    capHeight: Dp = 0.dp,
    corner: Dp = 26.dp,
    framed: Boolean = true,
    tactileDots: Boolean = true,
): Modifier = drawWithCache {
    val r = CornerRadius(corner.toPx())
    val rnd = Random(5)
    val grain = Path()
    repeat((size.width * size.height / 240f).toInt().coerceAtMost(32000)) {
        val x = rnd.nextFloat() * size.width
        val y = rnd.nextFloat() * size.height
        grain.addRect(Rect(x, y, x + 1.1f, y + 1.1f))
    }
    val body = Brush.verticalGradient(
        listOf(
            lerp(color, Color.White, 0.09f),
            color,
            lerp(color, Color.Black, 0.28f),
        )
    )
    val cap = capHeight.toPx()
    onDrawBehind {
        if (framed) drawRoundRect(Color.Black.copy(alpha = 0.65f), topLeft = Offset(0f, 8f), cornerRadius = r)
        drawRoundRect(body, cornerRadius = r)
        drawPath(grain, Color.White.copy(alpha = 0.032f))
        if (cap > 0f) {
            drawRoundRect(
                Brush.verticalGradient(listOf(lerp(color, Color.White, 0.16f), lerp(color, Color.White, 0.05f)), 0f, cap),
                size = Size(size.width, cap + r.x),
                cornerRadius = r,
            )
            drawRect(body, Offset(0f, cap), Size(size.width, r.x))
            drawLine(Color.Black.copy(alpha = 0.55f), Offset(0f, cap), Offset(size.width, cap), 2f)
            drawLine(Color.White.copy(alpha = 0.14f), Offset(0f, cap + 2f), Offset(size.width, cap + 2f), 1f)
        }
        // Subtle satin diagonal sheen across the anodized aluminum
        drawRoundRect(
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.07f), Color.Transparent, Color.Black.copy(alpha = 0.18f)),
                Offset.Zero,
                Offset(size.width, size.height),
            ),
            cornerRadius = r,
        )
        // Top satin chamfer lip
        drawLine(
            Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.18f), Color.Transparent)
            ),
            Offset(r.x, 1.5f),
            Offset(size.width - r.x, 1.5f),
            strokeWidth = 2f,
        )
        if (tactileDots) {
            // Three small tactile white blind-touch dots on the top bevel, as on the WM-RX707
            val dotY = 6.dp.toPx()
            val startX = size.width * 0.34f
            val gap = 7.dp.toPx()
            for (i in 0..2) {
                val cx = startX + i * gap
                drawCircle(Color.Black.copy(alpha = 0.5f), 2.1.dp.toPx(), Offset(cx, dotY + 0.8f))
                drawCircle(Color(0xFFE8ECEF), 1.8.dp.toPx(), Offset(cx, dotY))
            }
        }
        if (framed) {
            drawRoundRect(Color.White.copy(alpha = 0.18f), cornerRadius = r, style = Stroke(1.8f))
        }
    }
}

/**
 * Authentic Sony Walkman WM-RX707 header badging:
 * Left: "SONY" slab-serif metallic badge.
 * Right: Red "RECORDING", silver "WALKMAN" wordmark, "[AVLS]" pill badge, and "WM-RX707" model number.
 */
@Composable
fun Rx707Header(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                "SONY",
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Black,
                    fontSize = if (compact) 20.sp else 26.sp,
                    letterSpacing = if (compact) 3.sp else 4.sp,
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFFFFFFFF), Color(0xFFD5D9DF), Color(0xFF9EA4AC), Color(0xFFE6E9EE))
                    ),
                    shadow = Shadow(Color.Black.copy(alpha = 0.65f), Offset(0f, 2f), 3f),
                ),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "RECORDING",
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 8.sp else 10.sp,
                    letterSpacing = 2.2.sp,
                    color = DeckColors.Accent,
                ),
            )
            Text(
                "WALKMAN",
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontSize = if (compact) 17.sp else 22.sp,
                    letterSpacing = 1.5.sp,
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFFF6F8FA), Color(0xFFC2C7CE), Color(0xFF9BA0A8))
                    ),
                    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 3f),
                ),
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .border(1.dp, Color(0xFFB8BDC4), RoundedCornerShape(50))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        "AVLS",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (compact) 6.5.sp else 7.5.sp,
                            letterSpacing = 0.8.sp,
                            color = Color(0xFFC8CDD4),
                        ),
                    )
                }
                Text(
                    "WM-RX707",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (compact) 8.5.sp else 10.sp,
                        letterSpacing = 1.2.sp,
                        color = Color(0xFFC8CDD4),
                    ),
                )
            }
        }
    }
}

/** Raised chrome lettering for the name on the case. */
@Composable
fun Wordmark(text: String, size: TextUnit, modifier: Modifier = Modifier, light: Boolean = true) {
    Text(
        text,
        modifier,
        style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Black,
            fontSize = size,
            letterSpacing = size * 0.04f,
            brush = if (light) {
                Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFDDE1E6), Color(0xFFA9AFB7), Color(0xFFE9ECEF)))
            } else {
                Brush.verticalGradient(listOf(Color(0xFF55585E), Color(0xFF2B2D31)))
            },
            shadow = Shadow(Color.Black.copy(alpha = if (light) 0.45f else 0.2f), Offset(0f, 3f), 4f),
        ),
        maxLines = 1,
    )
}

/**
 * WM-RX707 sculpted D-shaped sliding HOLD switch on the bottom-right of the gunmetal pod,
 * with a 3x3 raised dimple matrix and recessed status indicator hole.
 */
@Composable
fun Rx707HoldSwitch(
    locked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val sounds = LocalMechSounds.current
    val toggle by rememberUpdatedState(onToggle)
    val state by rememberUpdatedState(locked)
    val slide by animateFloatAsState(
        targetValue = if (locked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 1800f),
        label = "holdSlide",
    )
    Row(
        modifier = modifier
            .semantics {
                role = Role.Switch
                contentDescription = "Hold lock"
                stateDescription = if (locked) "Locked" else "Unlocked"
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    sounds?.keyDown()
                    toggle(!state)
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            "HOLD",
            style = EngravedCaps.copy(
                color = DeckColors.PodSilkscreen,
                fontSize = 8.5.sp,
                letterSpacing = 1.2.sp,
            ),
        )
        // Arrow + recessed status hole
        Canvas(Modifier.size(24.dp, 12.dp)) {
            val cy = size.height / 2f
            // Left-pointing arrow tip
            val arrow = Path().apply {
                moveTo(0f, cy)
                lineTo(6.dp.toPx(), cy - 2.6.dp.toPx())
                lineTo(6.dp.toPx(), cy + 2.6.dp.toPx())
                close()
            }
            drawPath(arrow, DeckColors.PodSilkscreen)
            drawLine(DeckColors.PodSilkscreen, Offset(5.dp.toPx(), cy), Offset(13.dp.toPx(), cy), 1.4.dp.toPx())
            // Recessed indicator hole
            val holeCenter = Offset(size.width - 4.5.dp.toPx(), cy)
            val holeR = 3.8.dp.toPx()
            drawCircle(Color(0xFF0A0B0C), holeR, holeCenter)
            drawCircle(Color.White.copy(alpha = 0.22f), holeR, holeCenter, style = Stroke(1f))
            if (slide > 0.05f) {
                drawCircle(DeckColors.Accent.copy(alpha = slide), holeR * 0.58f, holeCenter)
            } else {
                drawCircle(Color(0xFFDADDE2), holeR * 0.36f, holeCenter)
            }
        }
        // Sculpted D-shaped dimpled gunmetal slider thumb
        Canvas(Modifier.size(38.dp, 30.dp)) {
            val w = size.width
            val h = size.height
            val wellR = h / 2f
            // Recessed D-shaped track well
            val wellPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(0f, 0f, w, h),
                        topLeft = CornerRadius(wellR, wellR),
                        bottomLeft = CornerRadius(wellR, wellR),
                        topRight = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        bottomRight = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    )
                )
            }
            drawPath(wellPath, Color(0xFF141615))
            drawPath(wellPath, Color.Black.copy(alpha = 0.8f), style = Stroke(2f))
            drawPath(wellPath, Color.White.copy(alpha = 0.16f), style = Stroke(1f))

            val travel = 6.dp.toPx()
            val dx = -travel * slide
            val pad = 2.5.dp.toPx()
            val capRect = Rect(pad + dx, pad, w - pad + dx, h - pad)
            val capR = capRect.height / 2f
            val capPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = capRect,
                        topLeft = CornerRadius(capR, capR),
                        bottomLeft = CornerRadius(capR, capR),
                        topRight = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                        bottomRight = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    )
                )
            }
            drawPath(
                capPath,
                Brush.verticalGradient(
                    listOf(Color(0xFF6B6E67), Color(0xFF52554F), Color(0xFF3E403B)),
                    capRect.top,
                    capRect.bottom,
                ),
            )
            drawPath(capPath, Color.White.copy(alpha = 0.25f), style = Stroke(1.2f))
            drawPath(capPath, Color.Black.copy(alpha = 0.6f), style = Stroke(1.8f))

            // 3x3 raised dimple matrix on the slider cap
            val startX = capRect.left + capRect.width * 0.34f
            val stepX = capRect.width * 0.22f
            val startY = capRect.top + capRect.height * 0.26f
            val stepY = capRect.height * 0.24f
            val dimpleR = 1.5.dp.toPx()
            for (row in 0..2) {
                for (col in 0..2) {
                    val cx = startX + col * stepX
                    val cy = startY + row * stepY
                    drawCircle(Color.Black.copy(alpha = 0.55f), dimpleR, Offset(cx + 0.6f, cy + 0.8f))
                    drawCircle(Color(0xFF7B7E77), dimpleR, Offset(cx, cy))
                    drawCircle(Color.White.copy(alpha = 0.3f), dimpleR * 0.45f, Offset(cx - 0.4f, cy - 0.4f))
                }
            }
        }
    }
}

/**
 * WM-RX707 top-edge dimpled OPEN slide-latch (from top_controls.jpg).
 * Tapping or sliding it pops open the cassette Tape Shelf.
 */
@Composable
fun Rx707OpenLatch(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val action by rememberUpdatedState(onClick)
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val sounds = LocalMechSounds.current
    var pressed by remember { mutableStateOf(false) }
    val slide by animateFloatAsState(if (pressed) 1f else 0f, spring(stiffness = 2200f), label = "openLatch")

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("OPEN", style = EngravedCaps.copy(fontSize = 7.5.sp, letterSpacing = 1.4.sp, color = Color(0xFF2D3035)))
        Spacer(Modifier.height(2.dp))
        Canvas(
            Modifier
                .size(58.dp, 26.dp)
                .semantics { role = Role.Button; contentDescription = "Open tape shelf" }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        pressed = true
                        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                        sounds?.keyDown()
                        action()
                        tryAwaitRelease()
                        pressed = false
                    })
                }
        ) {
            val r = CornerRadius(size.height / 2f)
            // Recessed stadium slot in the brushed titanium trim
            drawRoundRect(Color(0xFF141517), cornerRadius = r)
            drawRoundRect(Color.White.copy(alpha = 0.5f), cornerRadius = r, style = Stroke(1.2f))

            val pad = 2.5.dp.toPx()
            val dx = -4.dp.toPx() * slide
            val nubW = size.width - pad * 2 - 4.dp.toPx()
            val nubH = size.height - pad * 2
            val nubLeft = pad + 4.dp.toPx() + dx
            val nubR = CornerRadius(nubH / 2f)
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFF5D605A), Color(0xFF434540), Color(0xFF31332E))),
                topLeft = Offset(nubLeft, pad),
                size = Size(nubW, nubH),
                cornerRadius = nubR,
            )
            drawRoundRect(
                Color.White.copy(alpha = 0.22f),
                topLeft = Offset(nubLeft, pad),
                size = Size(nubW, nubH),
                cornerRadius = nubR,
                style = Stroke(1f),
            )
            // Engraved triangle on left of latch + 2x4 raised dimples
            val cy = size.height / 2f
            val tri = Path().apply {
                moveTo(nubLeft + 5.dp.toPx(), cy)
                lineTo(nubLeft + 10.dp.toPx(), cy - 3.5.dp.toPx())
                lineTo(nubLeft + 10.dp.toPx(), cy + 3.5.dp.toPx())
                close()
            }
            drawPath(tri, Color(0xFF232522))
            val startX = nubLeft + 15.dp.toPx()
            val stepX = 5.5.dp.toPx()
            for (col in 0..3) {
                for (row in -1..1 step 2) {
                    val cx = startX + col * stepX
                    val dy = row * 3.dp.toPx()
                    drawCircle(Color.Black.copy(alpha = 0.5f), 1.3.dp.toPx(), Offset(cx + 0.5f, cy + dy + 0.6f))
                    drawCircle(Color(0xFF72756E), 1.3.dp.toPx(), Offset(cx, cy + dy))
                }
            }
        }
    }
}

/**
 * WM-RX707 top-edge recessed red circular REC button and ribbed guard plate (from top_controls.jpg).
 */
@Composable
fun Rx707RecButton(active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val action by rememberUpdatedState(onClick)
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val sounds = LocalMechSounds.current
    var down by remember { mutableStateOf(false) }
    val depth by animateFloatAsState(if (down) 1f else 0f, spring(stiffness = 2600f), label = "recBtn")

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(56.dp, 30.dp)
                .semantics { role = Role.Button; contentDescription = "Record / Transparent Shell Toggle" }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        down = true
                        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                        sounds?.keyDown()
                        action()
                        tryAwaitRelease()
                        down = false
                    })
                }
        ) {
            val r = CornerRadius(6.dp.toPx())
            drawRoundRect(Color(0xFF1A1B1E), cornerRadius = r)
            drawRoundRect(Color.White.copy(alpha = 0.55f), cornerRadius = r, style = Stroke(1.2f))
            // Left gunmetal guard plate with 3 vertical ribs
            val guardW = size.width * 0.56f
            val pad = 2.5.dp.toPx()
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFF636660), Color(0xFF464843))),
                topLeft = Offset(pad, pad),
                size = Size(guardW, size.height - pad * 2),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            for (i in -1..1) {
                val rx = pad + guardW * 0.5f + i * 4.5.dp.toPx()
                drawLine(Color.Black.copy(alpha = 0.45f), Offset(rx, pad + 5.dp.toPx()), Offset(rx, size.height - pad - 5.dp.toPx()), 1.5.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.25f), Offset(rx + 1f, pad + 5.dp.toPx()), Offset(rx + 1f, size.height - pad - 5.dp.toPx()), 1f)
            }
            // Right circular red REC button in chrome well
            val btnCenter = Offset(pad + guardW + (size.width - pad * 2 - guardW) * 0.52f, size.height / 2f)
            val btnR = (size.height * 0.32f) * (1f - depth * 0.06f)
            drawCircle(Color(0xFF0A0A0C), btnR * 1.25f, btnCenter)
            drawCircle(
                Brush.radialGradient(
                    listOf(
                        if (active) Color(0xFFFF6B52) else Color(0xFFF04A32),
                        Color(0xFFC42714),
                        Color(0xFF8C190B),
                    ),
                    center = btnCenter - Offset(btnR * 0.25f, btnR * 0.25f),
                    radius = btnR * 1.5f,
                ),
                btnR,
                btnCenter + Offset(0f, depth * 1.2f),
            )
            drawCircle(Color.White.copy(alpha = 0.35f), btnR * 0.3f, btnCenter - Offset(btnR * 0.25f, btnR * 0.25f))
        }
        Spacer(Modifier.height(2.dp))
        Text("REC · SHELL", style = EngravedCaps.copy(fontSize = 6.5.sp, letterSpacing = 1.sp, color = Color(0xFF2D3035)))
    }
}

/** A two-position slide switch with a dimpled gunmetal nub, labelled above it. */
@Composable
fun SlideSwitch(
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    caption: String,
    modifier: Modifier = Modifier,
    onLabel: String = "ON",
    offLabel: String = "OFF",
    height: Dp = 54.dp,
) {
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val sounds = LocalMechSounds.current
    val toggle by rememberUpdatedState(onToggle)
    val state by rememberUpdatedState(on)
    val pos by animateFloatAsState(if (on) 0f else 1f, spring(dampingRatio = 0.7f, stiffness = 1600f), label = "slide")
    val measurer = rememberTextMeasurer()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(caption, style = EngravedCaps.copy(fontSize = 7.5.sp, color = Color(0xFF25272B)), maxLines = 1)
        Canvas(
            Modifier
                .size(44.dp, height)
                .semantics {
                    role = Role.Switch
                    contentDescription = caption
                    stateDescription = if (on) onLabel else offLabel
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        sounds?.keyDown()
                        toggle(!state)
                    }
                },
        ) {
            val small = TextStyle(fontSize = 6.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF25272B))
            val trackW = size.width * 0.40f
            val x = size.width - trackW - 2f
            val top = 4f
            val trackH = size.height - 8f
            val onL = measurer.measure(onLabel, small)
            val offL = measurer.measure(offLabel, small)
            drawText(onL, topLeft = Offset(0f, top + trackH * 0.18f - onL.size.height / 2f))
            drawText(offL, topLeft = Offset(0f, top + trackH * 0.82f - offL.size.height / 2f))
            drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(x, top + 1.5f), Size(trackW, trackH), CornerRadius(trackW / 2f))
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF0A0B0D), Color(0xFF222428))), Offset(x, top), Size(trackW, trackH), CornerRadius(trackW / 2f))
            val nubH = trackH * 0.46f
            val ny = top + 2f + (trackH - nubH - 4f) * pos
            drawRoundRect(
                Brush.horizontalGradient(listOf(Color(0xFF686B64), Color(0xFF484B45), Color(0xFF5D6059)), x, x + trackW),
                Offset(x + 2f, ny),
                Size(trackW - 4f, nubH),
                CornerRadius((trackW - 4f) / 2f),
            )
            // Raised dimples on the switch nub (like the REC MODE switch on WM-RX707)
            for (i in 1..3) {
                val dy = ny + nubH * i / 4f
                drawCircle(Color.Black.copy(alpha = 0.5f), 1.3.dp.toPx(), Offset(x + trackW / 2f, dy + 0.6f))
                drawCircle(Color(0xFF83867E), 1.3.dp.toPx(), Offset(x + trackW / 2f, dy))
            }
        }
    }
}

/** Engraved outline arrow, pointing the way the tape comes out. Tapping it opens the tape shelf. */
@Composable
fun EjectArrow(onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Color(0xFFD0D5DC), up: Boolean = true) {
    val action by rememberUpdatedState(onClick)
    Canvas(
        modifier
            .semantics { role = Role.Button; contentDescription = "Eject: choose a tape" }
            .pointerInput(Unit) { detectTapGestures { action() } },
    ) {
        val w = size.width
        val h = size.height
        val p = if (up) Path().apply {
            moveTo(w / 2f, 0f); lineTo(w, h * 0.35f); lineTo(w * 0.68f, h * 0.35f); lineTo(w * 0.68f, h)
            lineTo(w * 0.32f, h); lineTo(w * 0.32f, h * 0.35f); lineTo(0f, h * 0.35f); close()
        } else Path().apply {
            moveTo(0f, h / 2f); lineTo(w * 0.3f, 0f); lineTo(w * 0.3f, h * 0.3f); lineTo(w, h * 0.3f)
            lineTo(w, h * 0.7f); lineTo(w * 0.3f, h * 0.7f); lineTo(w * 0.3f, h); close()
        }
        drawPath(p, Color.Black.copy(alpha = 0.35f), style = Stroke(3.5f))
        drawPath(p, color, style = Stroke(2.0f))
    }
}

/** A dark bezel holding gauges, like the meter window on a deck's faceplate. */
fun Modifier.bezel(corner: Dp = 6.dp): Modifier = drawWithCache {
    val r = CornerRadius(corner.toPx())
    onDrawBehind {
        drawRoundRect(Color.White.copy(alpha = 0.35f), topLeft = Offset(0f, 1.5f), cornerRadius = r)
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF42454A), Color(0xFF202226))), size = Size(size.width, size.height - 1.5f), cornerRadius = r)
        drawRoundRect(Color.Black, topLeft = Offset(3f, 3f), size = Size(size.width - 6f, size.height - 7.5f), cornerRadius = CornerRadius(r.x * 0.6f))
    }
}

/**
 * Precision knurled rotary/vertical thumbwheel volume control for the WM-RX707 side strip.
 */
@Composable
fun VolumeSlider(volume: Float, onVolume: (Float) -> Unit, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(volume)
    val set by rememberUpdatedState(onVolume)
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier
            .semantics {
                contentDescription = "Volume"
                progressBarRangeInfo = ProgressBarRangeInfo(current, 0f..1f)
                setProgress { set(it.coerceIn(0f, 1f)); true }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    set((current - drag.y / (size.height * 0.8f)).coerceIn(0f, 1f))
                }
            },
    ) {
        val ink = Color(0xFF2B2D32)
        val small = TextStyle(fontSize = 6.5.sp, fontWeight = FontWeight.Bold, color = ink)
        val labelH = 10.dp.toPx()
        val top = 4f
        val bottom = size.height - labelH - 4f
        val trackW = size.width * 0.14f
        val xs = listOf(size.width * 0.44f, size.width * 0.68f)
        for (i in 0..10) {
            val y = bottom - (bottom - top) * i / 10f
            drawLine(ink, Offset(size.width * 0.22f, y), Offset(size.width * (if (i % 5 == 0) 0.34f else 0.29f), y), 1.2f)
            if (i % 2 == 0) {
                val l = measurer.measure(i.toString(), small)
                drawText(l, topLeft = Offset(size.width * 0.19f - l.size.width, y - l.size.height / 2f))
            }
        }
        xs.forEach { x ->
            drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(x - trackW / 2f, top + 1.5f), Size(trackW, bottom - top), CornerRadius(3f))
            drawRoundRect(Color(0xFF0A0A0B), Offset(x - trackW / 2f, top), Size(trackW, bottom - top), CornerRadius(3f))
        }
        val nubH = 28.dp.toPx().coerceAtMost((bottom - top) * 0.32f)
        val ny = bottom - nubH - (bottom - top - nubH) * current
        val left = xs[0] - trackW * 1.5f
        val right = xs[1] + trackW * 1.5f
        drawRoundRect(Color.Black.copy(alpha = 0.5f), Offset(left, ny + 2.5f), Size(right - left, nubH), CornerRadius(4f))
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color(0xFF70736C), Color(0xFF4A4D47), Color(0xFF666962), Color(0xFF3E403B)), left, right),
            Offset(left, ny),
            Size(right - left, nubH),
            CornerRadius(4f),
        )
        drawRoundRect(Color.White.copy(alpha = 0.28f), Offset(left, ny), Size(right - left, nubH), CornerRadius(4f), style = Stroke(1f))
        for (i in 1..7) {
            val y = ny + nubH * i / 8f
            drawLine(Color.Black.copy(alpha = 0.45f), Offset(left + 3f, y), Offset(right - 3f, y), 1.2f)
            drawLine(Color.White.copy(alpha = 0.18f), Offset(left + 3f, y + 1f), Offset(right - 3f, y + 1f), 0.8f)
        }
        val l = measurer.measure("L", small)
        val r = measurer.measure("R", small)
        drawText(l, topLeft = Offset(xs[0] - l.size.width / 2f, size.height - labelH))
        drawText(r, topLeft = Offset(xs[1] - r.size.width / 2f, size.height - labelH))
    }
}

/** The hotline/open button on top of the case; opens the tape shelf. */
@Composable
fun HotlineButton(onClick: () -> Unit, modifier: Modifier = Modifier) = Rx707OpenLatch(onClick, modifier)

/** Perforated microphone grille. */
@Composable
fun MicGrille(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val step = 5.dp.toPx()
        var y = step / 2
        while (y < size.height) {
            var x = step / 2
            while (x < size.width) {
                drawCircle(Color.White.copy(alpha = 0.4f), 1.2f, Offset(x, y + 0.8f))
                drawCircle(Color(0xFF202225), 1.2f, Offset(x, y))
                x += step
            }
            y += step
        }
    }
}

/** Headphone & Sony wired remote socket (as on the side of the WM-RX707). */
@Composable
fun PhonesJack(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        drawCircle(Brush.radialGradient(listOf(Color(0xFFE6E8EB), Color(0xFF7D8085)), center, r), r)
        drawCircle(Color(0xFF050505), r * 0.62f)
        drawCircle(Color(0xFF2A2B2E), r * 0.3f)
    }
}
