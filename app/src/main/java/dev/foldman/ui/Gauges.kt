package dev.foldman.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldman.deck.Source
import dev.foldman.deck.Transport
import dev.foldman.playback.AudioLevels
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.sin
import kotlin.random.Random

/** Three-digit mechanical tape counter: white numerals on black drums that roll like an odometer. */
@Composable
fun TapeCounter(reels: ReelMotion, modifier: Modifier = Modifier, digitWidth: Dp = 18.dp) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.size(digitWidth * 3 + 10.dp, digitWidth * 1.45f)) {
        val v = ((reels.counter % 1000.0) + 1000.0) % 1000.0
        val pad = 3.dp.toPx()
        val dw = (size.width - pad * 2) / 3f
        val dh = size.height - pad * 2
        drawRoundRect(Color(0xFF070707), cornerRadius = CornerRadius(3.dp.toPx()))
        drawRoundRect(Color.White.copy(alpha = 0.25f), cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(1f))
        val style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = (dh * 0.62f).toSp(), color = Color(0xFFF3EFE6))
        val units = v % 10.0
        for (place in 0 until 3) {
            val scale = when (place) { 0 -> 100.0; 1 -> 10.0; else -> 1.0 }
            val whole = floor(v / scale) % 10.0
            // Higher drums only turn while the one below rolls from 9 to 0.
            val roll = when (place) {
                2 -> units - floor(units)
                1 -> (units - 9.0).coerceIn(0.0, 1.0)
                else -> if (floor(v / 10.0) % 10.0 == 9.0) (units - 9.0).coerceIn(0.0, 1.0) else 0.0
            }.toFloat()
            val x = pad + place * dw
            clipRect(x + 1f, pad, x + dw - 1f, pad + dh) {
                drawRect(Color(0xFF151515), Offset(x + 1f, pad), Size(dw - 2f, dh))
                for (k in 0..1) {
                    val digit = ((whole.toInt() + k) % 10).toString()
                    val layout = measurer.measure(digit, style)
                    val y = pad + (dh - layout.size.height) / 2f + (k - roll) * dh
                    drawText(layout, topLeft = Offset(x + (dw - layout.size.width) / 2f, y))
                }
                // Drum curvature.
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.85f), 0.3f to Color.Transparent,
                        0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f),
                        startY = pad, endY = pad + dh,
                    ),
                    Offset(x + 1f, pad), Size(dw - 2f, dh),
                )
            }
        }
    }
}

/** The operate LED: steady while playing, slow blink when paused, fast flicker while winding. */
@Composable
fun StatusLed(transport: Transport, modifier: Modifier = Modifier, caption: String = "OPERATE") {
    val blink = rememberInfiniteTransition(label = "led")
    val slow by blink.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "slow")
    val on = when (transport) {
        Transport.PLAYING -> 1f
        Transport.PAUSED -> if (slow < 0.5f) 1f else 0.08f
        Transport.REWINDING, Transport.FAST_FORWARDING -> if ((slow * 6f).toInt() % 2 == 0) 1f else 0.25f
        Transport.STOPPED -> 0f
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(14.dp)) {
            val r = size.minDimension / 2f
            if (on > 0.05f) drawCircle(Brush.radialGradient(listOf(DeckColors.Led.copy(alpha = 0.55f * on), Color.Transparent), center, r * 1.0f), r)
            drawCircle(Color(0xFF2A0D08), r * 0.5f)
            drawCircle(DeckColors.Led.copy(alpha = on), r * 0.42f)
            drawCircle(Color.White.copy(alpha = 0.25f + 0.5f * on), r * 0.14f, center - Offset(r * 0.14f, r * 0.14f))
        }
        Text(caption, style = EngravedCaps.copy(fontSize = 7.sp, letterSpacing = 1.sp, color = DeckColors.EngraveLight))
    }
}

@Stable
private class Needles {
    var left by mutableFloatStateOf(0f)
    var right by mutableFloatStateOf(0f)
    var vl = 0f
    var vr = 0f
}

/**
 * A dual-needle VU meter. On the local tape it reads the decoded audio; other apps' audio isn't
 * readable, so for them it dances to a plausible programme-level signal. Needles are a damped
 * spring, so they swing and settle like the real thing.
 */
@Composable
fun VuMeter(transport: Transport, source: Source, modifier: Modifier = Modifier, channel: Int? = null) {
    val needles = remember { Needles() }
    val active by rememberUpdatedState(transport == Transport.PLAYING)
    val isLocal by rememberUpdatedState(source == Source.LOCAL || source == Source.PLEX)
    val measurer = rememberTextMeasurer()
    LaunchedEffect(needles) {
        var last = 0L
        var t = 0f
        val rnd = Random(11)
        var noiseL = 0f
        var noiseR = 0f
        while (true) {
            if (!active && needles.left < 0.002f && needles.right < 0.002f && abs(needles.vl) < 0.01f) {
                snapshotFlow { active }.first { it }
                last = 0L
            }
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                t += dt
                val (tl, tr) = when {
                    !active -> 0f to 0f
                    isLocal -> AudioLevels.read().let { (l, r) -> deflection(l) to deflection(r) }
                    else -> {
                        noiseL += (rnd.nextFloat() - 0.5f - noiseL) * 0.25f
                        noiseR += (rnd.nextFloat() - 0.5f - noiseR) * 0.25f
                        val beat = exp(-((t * 2.03f) % 1f) * 5f) * 0.18f
                        val base = 0.58f + 0.08f * sin(t * 0.9f) + 0.05f * sin(t * 2.7f + 1f)
                        (base + beat + noiseL * 0.35f) to (base + beat * 0.9f + noiseR * 0.35f + 0.02f * sin(t * 1.7f))
                    }
                }
                // Critically-ish damped needle: ω≈12 rad/s, ζ≈0.6.
                val k = 140f
                val c = 14f
                needles.vl += (k * (tl - needles.left) - c * needles.vl) * dt
                needles.vr += (k * (tr - needles.right) - c * needles.vr) * dt
                needles.left = (needles.left + needles.vl * dt).coerceIn(0f, 1.08f)
                needles.right = (needles.right + needles.vr * dt).coerceIn(0f, 1.08f)
            }
        }
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val r = CornerRadius(5.dp.toPx())
        drawRoundRect(Color(0xFF0A0A0A), cornerRadius = r)
        val inset = 3.dp.toPx()
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFFFFF4D6), Color(0xFFF2E2B8), Color(0xFFE2CC97))),
            topLeft = Offset(inset, inset), size = Size(w - inset * 2, h - inset * 2), cornerRadius = CornerRadius(3.dp.toPx()),
        )
        // Warm backlight glow while the motor runs.
        if (transport.isMoving) drawRoundRect(
            Brush.radialGradient(listOf(Color(0x40FFB040), Color.Transparent), Offset(w / 2, h), w * 0.7f),
            topLeft = Offset(inset, inset), size = Size(w - inset * 2, h - inset * 2),
        )
        // Narrow faces get a smaller arc so the scale stays on the card.
        val radius = minOf(h * 0.82f, w * 0.5f)
        val pivot = Offset(w / 2f, h * 0.28f + radius)
        val start = -138f
        val sweep = 96f
        fun angleAt(d: Float) = (start + sweep * d) * (PI / 180.0).toFloat()
        fun point(d: Float, rr: Float) = Offset(pivot.x + rr * cos(angleAt(d)), pivot.y + rr * sin(angleAt(d)))
        val ink = Color(0xFF262019)
        // Scale arc, red from 0 VU up.
        val zero = deflection(dbToRms(0f))
        drawArc(
            ink, startAngle = start, sweepAngle = sweep * zero, useCenter = false,
            topLeft = pivot - Offset(radius, radius), size = Size(radius * 2, radius * 2), style = Stroke(1.2f),
        )
        drawArc(
            Color(0xFFC8261B), startAngle = start + sweep * zero, sweepAngle = sweep * (1f - zero), useCenter = false,
            topLeft = pivot - Offset(radius, radius), size = Size(radius * 2, radius * 2), style = Stroke(3.5f),
        )
        val labelStyle = TextStyle(fontSize = (radius * 0.12f).toSp(), color = ink, fontWeight = FontWeight.Bold)
        for (db in listOf(-20f, -10f, -7f, -5f, -3f, -1f, 0f, 1f, 2f, 3f)) {
            val d = deflection(dbToRms(db))
            val red = db >= 0f
            drawLine(if (red) Color(0xFFC8261B) else ink, point(d, radius), point(d, radius * (if (db % 5f == 0f || db == 3f) 1.11f else 1.067f)), 1.3f)
            if (db in listOf(-20f, -10f, -5f, 0f, 3f)) {
                val text = if (db > 0) "+${db.toInt()}" else db.toInt().toString()
                val layout = measurer.measure(text, labelStyle.copy(color = if (red) Color(0xFFC8261B) else ink))
                val p = point(d, radius * 1.24f)
                drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
            }
        }
        val vu = measurer.measure("VU", TextStyle(fontSize = (radius * 0.2f).toSp(), color = ink, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif))
        drawText(vu, topLeft = Offset(w / 2f - vu.size.width / 2f, pivot.y - radius * 0.55f))
        val lr = TextStyle(fontSize = (h * 0.09f).toSp(), color = ink.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
        if (channel != 1) drawText(measurer.measure("L", lr), topLeft = Offset(inset * 3, h * 0.72f))
        if (channel != 0) drawText(measurer.measure("R", lr.copy(color = Color(0xFFC8261B).copy(alpha = 0.7f))), topLeft = Offset(w - inset * 3 - h * 0.08f, h * 0.72f))
        clipRect(inset, inset, w - inset, h - inset) {
            val shown = when (channel) {
                0 -> listOf(needles.left to ink)
                1 -> listOf(needles.right to ink)
                else -> listOf(needles.left to ink, needles.right to Color(0xFFB0281E))
            }
            for ((value, color) in shown) {
                val tip = point(value, radius * 1.1f)
                drawLine(Color.Black.copy(alpha = 0.18f), pivot + Offset(2f, 2f), tip + Offset(2f, 2f), 1.6f, StrokeCap.Round)
                drawLine(color, pivot, tip, 1.6f, StrokeCap.Round)
            }
            drawCircle(Color(0xFF1A1A1A), radius * 0.15f, pivot)
        }
        // Glass.
        drawRoundRect(
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent, Color.Transparent), Offset(0f, 0f), Offset(w * 0.6f, h)),
            topLeft = Offset(inset, inset), size = Size(w - inset * 2, h - inset * 2), cornerRadius = CornerRadius(3.dp.toPx()),
        )
    }
}

/** RMS level to needle position (0..1): −20 VU at the left stop, +3 VU at the right. */
private fun deflection(rms: Float): Float {
    if (rms <= 0.0005f) return 0f
    val vu = 20f * log10(rms * 3.2f)
    return ((vu + 20f) / 23f).coerceIn(0f, 1.08f)
}

private fun dbToRms(db: Float): Float = Math.pow(10.0, db / 20.0).toFloat() / 3.2f
