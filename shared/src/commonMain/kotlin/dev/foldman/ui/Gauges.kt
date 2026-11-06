package dev.foldman.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldman.deck.Transport
import kotlin.math.floor

/**
 * Three-digit mechanical tape counter, geared off the take-up reel:
 *  - rolling drums with curved shadow gradient
 *  - fractional carry when lower digit passes from 9 to 0
 */
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
                    Offset(x + 1f, pad),
                    Size(dw - 2f, dh),
                )
            }
            if (place > 0) {
                drawLine(Color(0xFF262626), Offset(x, pad), Offset(x, pad + dh), strokeWidth = 1f)
            }
        }
    }
}

/** The operate LED: steady while playing, slow blink when paused, fast flicker while winding. */
@Composable
fun StatusLed(transport: Transport, modifier: Modifier = Modifier, caption: String = "OPERATE") {
    val blink = rememberInfiniteTransition(label = "led")
    val slow by blink.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "slow"
    )
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
        Text(
            caption,
            style = EngravedCaps.copy(
                fontSize = 7.sp,
                letterSpacing = 1.sp,
                color = DeckColors.EngraveLight
            )
        )
    }
}
