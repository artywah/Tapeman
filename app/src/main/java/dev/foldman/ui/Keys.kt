package dev.foldman.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldman.deck.DeckUi
import dev.foldman.deck.Transport

enum class KeySymbol { STOP, REW, PLAY, FF, PAUSE, PREV, NEXT, PLAY_DIR }

/**
 * The signature Sony Walkman WM-RX707 curved hammer-tone gunmetal control pod
 * (modeled directly on wm_rx707_controls.jpg):
 *  - Upper arch with REV ◄, ● REC, and ► FWD LED indicators
 *  - 4 deep-welled spun-chrome feather-touch logic buttons:
 *    1. REW (round chrome cap, angled REVIEW label below)
 *    2. FF (round chrome cap, angled CUE label below)
 *    3. ■ STOP (round chrome cap with debossed square)
 *    4. ◄► PLAY/PAUSE/DIR (wide stadium chrome cap with dual direction triangles)
 *  - Bottom-right functional D-shaped dimpled HOLD switch
 */
@Composable
fun Rx707LogicPod(
    ui: DeckUi,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onStop: () -> Unit,
    onPlayToggle: () -> Unit,
    onToggleHold: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = ui.transport
    val hold = ui.holdLocked
    val blink = rememberInfiniteTransition(label = "podLeds")
    val pulse by blink.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "pulse")

    val recGlow = when (t) {
        Transport.PLAYING -> 1f
        Transport.PAUSED -> if (pulse < 0.5f) 0.95f else 0.12f
        Transport.REWINDING, Transport.FAST_FORWARDING -> if ((pulse * 6f).toInt() % 2 == 0) 1f else 0.2f
        Transport.STOPPED -> 0f
    }
    val revGlow = when {
        t == Transport.REWINDING -> if ((pulse * 6f).toInt() % 2 == 0) 1f else 0.25f
        t == Transport.PLAYING && ui.reverseDir -> 1f
        t == Transport.PAUSED && ui.reverseDir -> if (pulse < 0.5f) 0.85f else 0.15f
        else -> 0f
    }
    val fwdGlow = when {
        t == Transport.FAST_FORWARDING -> if ((pulse * 6f).toInt() % 2 == 0) 1f else 0.25f
        t == Transport.PLAYING && !ui.reverseDir -> 1f
        t == Transport.PAUSED && !ui.reverseDir -> if (pulse < 0.5f) 0.85f else 0.15f
        else -> 0f
    }

    Box(
        modifier = modifier
            .gunmetalPod(archHeightFraction = 0.20f)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top Arch Row: REV ◄   ● REC   ► FWD
            Row(
                Modifier
                    .fillMaxWidth(0.76f)
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // REV ◄
                Row(
                    modifier = Modifier.rotate(-9f).offset(y = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "REV",
                        style = EngravedCaps.copy(color = DeckColors.PodSilkscreen, fontSize = 9.sp, letterSpacing = 1.2.sp),
                    )
                    TriangleLed(pointingRight = false, glow = revGlow)
                }

                // ● REC
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    RoundPodLed(glow = recGlow)
                    Text(
                        "REC",
                        style = EngravedCaps.copy(color = DeckColors.PodSilkscreen, fontSize = 9.5.sp, letterSpacing = 1.2.sp),
                    )
                }

                // ► FWD
                Row(
                    modifier = Modifier.rotate(9f).offset(y = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TriangleLed(pointingRight = true, glow = fwdGlow)
                    Text(
                        "FWD",
                        style = EngravedCaps.copy(color = DeckColors.PodSilkscreen, fontSize = 9.sp, letterSpacing = 1.2.sp),
                    )
                }
            }

            // Middle Row: The 4 Spun-Chrome Logic Buttons along the arch
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 1. REW / REVIEW
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset(y = 10.dp),
                ) {
                    ChromeLogicButton(
                        label = "REW",
                        symbol = null,
                        latched = t == Transport.REWINDING,
                        locked = hold,
                        onPress = onRewind,
                        width = 48.dp,
                        height = 48.dp,
                        labelAngle = -14f,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "REVIEW",
                        style = EngravedCaps.copy(color = DeckColors.PodSilkscreen, fontSize = 8.sp, letterSpacing = 1.1.sp),
                        modifier = Modifier.rotate(-14f),
                    )
                }

                // 2. FF / CUE
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset(y = 2.dp),
                ) {
                    ChromeLogicButton(
                        label = "FF",
                        symbol = null,
                        latched = t == Transport.FAST_FORWARDING,
                        locked = hold,
                        onPress = onFastForward,
                        width = 48.dp,
                        height = 48.dp,
                        labelAngle = -7f,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "CUE",
                        style = EngravedCaps.copy(color = DeckColors.PodSilkscreen, fontSize = 8.sp, letterSpacing = 1.1.sp),
                        modifier = Modifier.rotate(-7f),
                    )
                }

                // 3. ■ STOP
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset(y = (-2).dp),
                ) {
                    ChromeLogicButton(
                        label = null,
                        symbol = KeySymbol.STOP,
                        latched = false,
                        locked = hold,
                        onPress = onStop,
                        width = 48.dp,
                        height = 48.dp,
                    )
                    Spacer(Modifier.height(14.dp))
                }

                // 4. ◄► PLAY / PAUSE / DIR (Wide Stadium Chrome Button)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset(y = 5.dp).rotate(7f),
                ) {
                    ChromeLogicButton(
                        label = null,
                        symbol = KeySymbol.PLAY_DIR,
                        latched = t == Transport.PLAYING || t == Transport.PAUSED,
                        locked = hold,
                        onPress = onPlayToggle,
                        width = 76.dp,
                        height = 46.dp,
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            // Bottom Row: Now Playing micro-line on left + HOLD slider on right
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    val trackLine = listOf(ui.title, ui.artist).filter { it.isNotBlank() }.joinToString(" — ")
                        .ifBlank { "${ui.source.label.uppercase()} · READY" }
                    Text(
                        trackLine.uppercase(),
                        style = EngravedCaps.copy(color = DeckColors.PodSilkscreen.copy(alpha = 0.88f), fontSize = 8.sp, letterSpacing = 1.2.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val metaLine = buildList {
                        add(ui.source.label.uppercase())
                        if (ui.dbbEnabled) add("EX DBB")
                        if (ui.avlsEnabled) add("AVLS")
                        if (hold) add("HOLD LOCKED")
                    }.joinToString(" · ")
                    Text(
                        metaLine,
                        style = EngravedCaps.copy(
                            color = if (hold) DeckColors.Accent else DeckColors.PodSilkscreen.copy(alpha = 0.58f),
                            fontSize = 6.5.sp,
                            letterSpacing = 1.sp,
                        ),
                        maxLines = 1,
                    )
                }
                Rx707HoldSwitch(locked = hold, onToggle = onToggleHold)
            }
        }
    }
}

@Composable
private fun TriangleLed(pointingRight: Boolean, glow: Float) {
    Canvas(Modifier.size(11.dp, 9.dp)) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            if (pointingRight) {
                moveTo(1f, 1f); lineTo(w - 1f, h / 2f); lineTo(1f, h - 1f); close()
            } else {
                moveTo(w - 1f, 1f); lineTo(1f, h / 2f); lineTo(w - 1f, h - 1f); close()
            }
        }
        drawPath(p, Color(0xFF141814))
        if (glow > 0.05f) {
            drawPath(p, DeckColors.LedDir.copy(alpha = glow))
        }
        drawPath(p, Color.Black.copy(alpha = 0.8f), style = Stroke(1.3f))
    }
}

@Composable
private fun RoundPodLed(glow: Float) {
    Canvas(Modifier.size(11.dp)) {
        val r = size.minDimension / 2f
        if (glow > 0.05f) {
            drawCircle(
                Brush.radialGradient(listOf(DeckColors.Led.copy(alpha = 0.55f * glow), Color.Transparent), center, r * 1.4f),
                r * 1.4f,
            )
        }
        drawCircle(Color(0xFF1D0D0A), r * 0.78f)
        if (glow > 0.05f) {
            drawCircle(DeckColors.Led.copy(alpha = glow), r * 0.65f)
            drawCircle(Color.White.copy(alpha = 0.45f * glow), r * 0.22f, center - Offset(r * 0.2f, r * 0.2f))
        } else {
            drawCircle(Color(0xFF524E4A), r * 0.55f)
        }
        drawCircle(Color.Black.copy(alpha = 0.85f), r * 0.78f, style = Stroke(1.3f))
    }
}

/**
 * Individual WM-RX707 spun-chrome logic push-button sitting inside a deep glossy black bezel well.
 */
@Composable
fun ChromeLogicButton(
    label: String?,
    symbol: KeySymbol?,
    latched: Boolean,
    locked: Boolean,
    onPress: () -> Unit,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    labelAngle: Float = 0f,
) {
    var pressed by remember { mutableStateOf(false) }
    val view = LocalView.current
    val sounds = LocalMechSounds.current
    val haptics = LocalHaptics.current
    val action by rememberUpdatedState(onPress)
    val measurer = rememberTextMeasurer()

    val depth by animateFloatAsState(
        targetValue = when {
            pressed -> 1f
            latched -> 0.55f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 2600f),
        label = "chromeBtn",
    )

    Canvas(
        modifier
            .size(width, height)
            .semantics {
                role = Role.Button
                contentDescription = label ?: symbol?.name ?: "Control"
                stateDescription = if (latched) "Active" else "Idle"
            }
            .pointerInput(locked) {
                detectTapGestures(onPress = {
                    if (locked) {
                        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        return@detectTapGestures
                    }
                    pressed = true
                    if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                    sounds?.keyDown()
                    action()
                    tryAwaitRelease()
                    pressed = false
                    if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_RELEASE)
                    sounds?.keyUp()
                })
            },
    ) {
        drawSpunChromeCap(
            depth = depth,
            latched = latched,
            label = label,
            labelAngle = labelAngle,
            symbol = symbol,
            measurer = measurer,
        )
    }
}

private fun DrawScope.drawSpunChromeCap(
    depth: Float,
    latched: Boolean,
    label: String?,
    labelAngle: Float,
    symbol: KeySymbol?,
    measurer: TextMeasurer,
) {
    val w = size.width
    val h = size.height
    val wellCorner = CornerRadius(h / 2f, h / 2f)

    // 1. Deep glossy black recessed well in the gunmetal pod
    drawRoundRect(
        color = Color.White.copy(alpha = 0.22f),
        topLeft = Offset(0f, 1.2f),
        size = Size(w, h),
        cornerRadius = wellCorner,
        style = Stroke(1.5f),
    )
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF050506), Color(0xFF141618), Color(0xFF0A0B0C))),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = wellCorner,
    )
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.9f),
        topLeft = Offset(1f, 1f),
        size = Size(w - 2f, h - 2f),
        cornerRadius = wellCorner,
        style = Stroke(2.2f),
    )

    // 2. Chrome skirt bevel inside the well
    val wellInset = (minOf(w, h) * 0.11f).coerceAtLeast(3.5.dp.toPx())
    val dy = depth * 1.8.dp.toPx()
    val capW = w - wellInset * 2f
    val capH = h - wellInset * 2f
    val capLeft = wellInset
    val capTop = wellInset + dy * 0.6f
    val capCorner = CornerRadius(capH / 2f, capH / 2f)

    // Drop shadow inside well
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.75f),
        topLeft = Offset(capLeft, capTop + 2.2.dp.toPx() * (1f - depth * 0.5f)),
        size = Size(capW, capH),
        cornerRadius = capCorner,
    )

    // Outer chrome skirt ring
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFFFFFFFF), Color(0xFF8C9096), Color(0xFF42454A), Color(0xFFC2C6CC)),
            capTop,
            capTop + capH,
        ),
        topLeft = Offset(capLeft, capTop),
        size = Size(capW, capH),
        cornerRadius = capCorner,
    )

    // 3. Spun-chrome domed top face with specular metallic sheen
    val rim = 2.2.dp.toPx()
    val faceLeft = capLeft + rim
    val faceTop = capTop + rim
    val faceW = capW - rim * 2f
    val faceH = capH - rim * 2f
    val faceCorner = CornerRadius(faceH / 2f, faceH / 2f)
    val shade = depth * 0.12f

    drawRoundRect(
        brush = Brush.linearGradient(
            0.0f to lerp(Color(0xFFFFFFFF), Color.Black, shade),
            0.28f to lerp(Color(0xFFE5E8EC), Color.Black, shade),
            0.50f to lerp(Color(0xFFBCC0C6), Color.Black, shade),
            0.72f to lerp(Color(0xFFF6F8FA), Color.Black, shade),
            1.0f to lerp(Color(0xFF969A9F), Color.Black, shade),
            start = Offset(faceLeft, faceTop),
            end = Offset(faceLeft + faceW, faceTop + faceH),
        ),
        topLeft = Offset(faceLeft, faceTop),
        size = Size(faceW, faceH),
        cornerRadius = faceCorner,
    )

    // Fine concentric lathe-spun rings on the chrome cap
    val cx = faceLeft + faceW / 2f
    val cy = faceTop + faceH / 2f
    for (ring in 1..3) {
        val inset = ring * (faceH * 0.11f)
        drawRoundRect(
            color = if (ring % 2 == 0) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.08f),
            topLeft = Offset(faceLeft + inset, faceTop + inset),
            size = Size((faceW - inset * 2f).coerceAtLeast(2f), (faceH - inset * 2f).coerceAtLeast(2f)),
            cornerRadius = CornerRadius((faceH / 2f - inset).coerceAtLeast(1f)),
            style = Stroke(0.8f),
        )
    }

    // Specular rim highlight around the domed cap
    drawRoundRect(
        color = Color.White.copy(alpha = 0.85f),
        topLeft = Offset(faceLeft + 0.5f, faceTop + 0.5f),
        size = Size(faceW - 1f, faceH - 1f),
        cornerRadius = faceCorner,
        style = Stroke(1.1f),
    )

    // 4. Debossed / engraved label or symbol on the chrome cap
    val center = Offset(cx, cy)
    val debossColor = if (latched) Color(0xFF2B2D30) else Color(0xFF4A4E54)
    if (label != null) {
        rotate(labelAngle, center) {
            val textStyle = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (faceH * 0.28f).toSp(),
                letterSpacing = 0.6.sp,
                color = debossColor,
            )
            val highlightLayout = measurer.measure(label, textStyle.copy(color = Color.White.copy(alpha = 0.7f)))
            val mainLayout = measurer.measure(label, textStyle)
            val tx = center.x - mainLayout.size.width / 2f
            val ty = center.y - mainLayout.size.height / 2f
            drawText(highlightLayout, topLeft = Offset(tx + 0.7f, ty + 0.9f))
            drawText(mainLayout, topLeft = Offset(tx, ty))
        }
    } else if (symbol != null) {
        val s = minOf(faceW, faceH) * 0.22f
        drawSymbol(symbol, center + Offset(0.7f, 0.9f), s, Color.White.copy(alpha = 0.72f))
        drawSymbol(symbol, center, s, debossColor)
    }
}

/**
 * Unfolded & Flex-mode transport keys: sculpted 3D WM-RX707 gunmetal & spun-chrome keys
 * set into a recessed dark anodized well with status LED indicators.
 */
@Composable
fun TransportKeys(
    ui: DeckUi,
    onStop: () -> Unit,
    onRewind: () -> Unit,
    onPlay: () -> Unit,
    onFastForward: () -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 62.dp,
    vertical: Boolean = false,
) {
    val t = ui.transport
    val cc = DeckColors.PodSilkscreen
    val keys: @Composable (Modifier, Modifier) -> Unit = { k, play ->
        DeckKey(
            symbol = KeySymbol.REW,
            caption = "REW · REVIEW",
            latched = t == Transport.REWINDING,
            onPress = onRewind,
            modifier = k,
            height = keyHeight,
            captionColor = cc,
            mark = DeckColors.LedDir,
        )
        DeckKey(
            symbol = KeySymbol.PLAY_DIR,
            caption = "PLAY ◄►",
            latched = t == Transport.PLAYING || t == Transport.PAUSED,
            onPress = onPlay,
            modifier = play,
            height = keyHeight,
            captionColor = cc,
            accent = true,
            mark = DeckColors.Accent,
        )
        DeckKey(
            symbol = KeySymbol.FF,
            caption = "FF · CUE",
            latched = t == Transport.FAST_FORWARDING,
            onPress = onFastForward,
            modifier = k,
            height = keyHeight,
            captionColor = cc,
            mark = DeckColors.LedDir,
        )
        DeckKey(
            symbol = KeySymbol.PAUSE,
            caption = "PAUSE",
            latched = t == Transport.PAUSED,
            onPress = onPause,
            modifier = k,
            height = keyHeight,
            captionColor = cc,
            mark = Color(0xFFF5A623),
        )
        DeckKey(
            symbol = KeySymbol.STOP,
            caption = "STOP · EJECT",
            latched = false,
            onPress = onStop,
            modifier = k,
            height = keyHeight,
            captionColor = cc,
            mark = DeckColors.Accent,
        )
    }
    if (vertical) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            keys(Modifier.fillMaxWidth(), Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier.recess(18.dp).padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { keys(Modifier.weight(1f), Modifier.weight(1.35f)) }
    }
}

/**
 * One sculpted 3D WM-RX707 deck key for the unfolded deck: a deep glossy bezel well housing a
 * machined spun-metal pill/stadium cap with an integrated LED indicator jewel.
 */
@Composable
fun DeckKey(
    symbol: KeySymbol,
    caption: String,
    latched: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 62.dp,
    accent: Boolean = false,
    mark: Color? = null,
    captionColor: Color = DeckColors.PodSilkscreen,
) {
    var pressed by remember { mutableStateOf(false) }
    val view = LocalView.current
    val sounds = LocalMechSounds.current
    val haptics = LocalHaptics.current
    val action by rememberUpdatedState(onPress)
    val depth by animateFloatAsState(
        when {
            pressed -> 1f
            latched -> 0.65f
            else -> 0f
        },
        spring(dampingRatio = 0.55f, stiffness = 2400f),
        label = "deckKey",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            caption,
            style = EngravedCaps.copy(color = captionColor, fontSize = 7.5.sp, letterSpacing = 1.sp),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics {
                    role = Role.Button
                    contentDescription = caption
                    stateDescription = if (latched) "Down" else "Up"
                }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        pressed = true
                        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                        sounds?.keyDown()
                        action()
                        tryAwaitRelease()
                        pressed = false
                        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_RELEASE)
                        sounds?.keyUp()
                    })
                },
        ) { drawSculptedDeckKey(symbol, depth, accent, latched, mark) }
    }
}

private fun DrawScope.drawSculptedDeckKey(
    symbol: KeySymbol,
    depth: Float,
    accent: Boolean,
    latched: Boolean,
    mark: Color?,
) {
    val w = size.width
    val h = size.height
    val wellR = CornerRadius(h * 0.42f, h * 0.42f)

    // 1. Deep glossy recessed well
    drawRoundRect(
        color = Color.White.copy(alpha = 0.18f),
        topLeft = Offset(0f, 1.2f),
        size = Size(w, h - 1.2f),
        cornerRadius = wellR,
        style = Stroke(1.3f),
    )
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF060608), Color(0xFF131518), Color(0xFF090A0C))),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = wellR,
    )

    // 2. Sculpted spun-chrome / gunmetal keycap inside the well
    val inset = 4.dp.toPx()
    val dy = depth * 2.2.dp.toPx()
    val capLeft = inset
    val capTop = inset + dy
    val capW = w - inset * 2f
    val capH = h - inset * 2f - 2.dp.toPx()
    val capR = CornerRadius(capH * 0.42f, capH * 0.42f)

    // Key shadow in well
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.8f),
        topLeft = Offset(capLeft, capTop + 2.5.dp.toPx() * (1f - depth * 0.6f)),
        size = Size(capW, capH),
        cornerRadius = capR,
    )

    // Chrome bevel skirt
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFFF6F8FA), Color(0xFF8A8E94), Color(0xFF46494E)),
            capTop,
            capTop + capH,
        ),
        topLeft = Offset(capLeft, capTop),
        size = Size(capW, capH),
        cornerRadius = capR,
    )

    // Domed face (spun silver-chrome for Play/accent, satin champagne-chrome for others)
    val rim = 2.dp.toPx()
    val fLeft = capLeft + rim
    val fTop = capTop + rim
    val fW = capW - rim * 2f
    val fH = capH - rim * 2f
    val fR = CornerRadius(fH * 0.42f, fH * 0.42f)
    val shade = 0.12f * depth

    val topColors = if (accent) {
        listOf(
            lerp(Color(0xFFFFFFFF), Color.Black, shade),
            lerp(Color(0xFFDCE0E6), Color.Black, shade),
            lerp(Color(0xFFB4B9C0), Color.Black, shade),
            lerp(Color(0xFFEEEFF2), Color.Black, shade),
        )
    } else {
        listOf(
            lerp(Color(0xFFF2F4F7), Color.Black, shade),
            lerp(Color(0xFFCACED4), Color.Black, shade),
            lerp(Color(0xFFA2A6AC), Color.Black, shade),
            lerp(Color(0xFFDEDFE3), Color.Black, shade),
        )
    }
    drawRoundRect(
        brush = Brush.linearGradient(topColors, Offset(fLeft, fTop), Offset(fLeft + fW, fTop + fH)),
        topLeft = Offset(fLeft, fTop),
        size = Size(fW, fH),
        cornerRadius = fR,
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.82f),
        topLeft = Offset(fLeft + 0.5f, fTop + 0.5f),
        size = Size(fW - 1f, fH - 1f),
        cornerRadius = fR,
        style = Stroke(1f),
    )

    // 3. Recessed LED indicator bar near bottom of cap
    val ledColor = mark ?: DeckColors.Accent
    val ledW = (fW * 0.34f).coerceIn(14.dp.toPx(), 32.dp.toPx())
    val ledH = 3.2.dp.toPx()
    val ledLeft = fLeft + (fW - ledW) / 2f
    val ledTop = fTop + fH - ledH - 4.5.dp.toPx()
    drawRoundRect(
        color = Color(0xFF1A1C1E),
        topLeft = Offset(ledLeft, ledTop),
        size = Size(ledW, ledH),
        cornerRadius = CornerRadius(ledH / 2f),
    )
    drawRoundRect(
        color = if (latched) ledColor else ledColor.copy(alpha = 0.38f),
        topLeft = Offset(ledLeft + 0.6f, ledTop + 0.6f),
        size = Size(ledW - 1.2f, ledH - 1.2f),
        cornerRadius = CornerRadius(ledH / 2f),
    )

    // 4. Debossed symbol in center of cap
    val c = Offset(fLeft + fW / 2f, fTop + fH * 0.44f)
    val s = minOf(fW, fH) * 0.20f
    val ink = if (latched && accent) DeckColors.AccentDeep else Color(0xFF33363B)
    drawSymbol(symbol, c + Offset(0.7f, 0.9f), s, Color.White.copy(alpha = 0.72f))
    drawSymbol(symbol, c, s, ink)
}

fun DrawScope.drawSymbol(symbol: KeySymbol, c: Offset, s: Float, ink: Color) {
    fun tri(cx: Float, dir: Float, scaleY: Float = 1f) = Path().apply {
        moveTo(cx - s * 0.8f * dir, c.y - s * scaleY)
        lineTo(cx + s * 0.8f * dir, c.y)
        lineTo(cx - s * 0.8f * dir, c.y + s * scaleY)
        close()
    }
    when (symbol) {
        KeySymbol.STOP -> drawRect(ink, Offset(c.x - s * 0.85f, c.y - s * 0.85f), Size(s * 1.7f, s * 1.7f))
        KeySymbol.PLAY -> drawPath(tri(c.x + s * 0.1f, 1f), ink)
        KeySymbol.PLAY_DIR -> {
            // WM-RX707 dual opposing direction triangles: ◄  ►
            val leftTri = Path().apply {
                moveTo(c.x - s * 0.35f, c.y - s * 0.85f)
                lineTo(c.x - s * 1.85f, c.y)
                lineTo(c.x - s * 0.35f, c.y + s * 0.85f)
                close()
            }
            val rightTri = Path().apply {
                moveTo(c.x + s * 0.35f, c.y - s * 0.85f)
                lineTo(c.x + s * 1.85f, c.y)
                lineTo(c.x + s * 0.35f, c.y + s * 0.85f)
                close()
            }
            drawPath(leftTri, ink)
            drawPath(rightTri, ink)
        }
        KeySymbol.FF -> {
            drawPath(tri(c.x - s * 0.7f, 1f), ink)
            drawPath(tri(c.x + s * 0.7f, 1f), ink)
        }
        KeySymbol.REW -> {
            drawPath(tri(c.x - s * 0.7f, -1f), ink)
            drawPath(tri(c.x + s * 0.7f, -1f), ink)
        }
        KeySymbol.PAUSE -> {
            drawRect(ink, Offset(c.x - s * 0.8f, c.y - s), Size(s * 0.55f, s * 2f))
            drawRect(ink, Offset(c.x + s * 0.25f, c.y - s), Size(s * 0.55f, s * 2f))
        }
        KeySymbol.PREV -> {
            drawRect(ink, Offset(c.x - s * 1.25f, c.y - s), Size(s * 0.3f, s * 2f))
            drawPath(tri(c.x - s * 0.15f, -1f), ink)
            drawPath(tri(c.x + s * 1.0f, -1f), ink)
        }
        KeySymbol.NEXT -> {
            drawPath(tri(c.x - s * 1.0f, 1f), ink)
            drawPath(tri(c.x + s * 0.15f, 1f), ink)
            drawRect(ink, Offset(c.x + s * 0.95f, c.y - s), Size(s * 0.3f, s * 2f))
        }
    }
}

/** A small round spun-chrome push button (AMS skip, counter reset, tape shelf). */
@Composable
fun MiniButton(
    caption: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    symbol: KeySymbol? = null,
    diameter: Dp = 34.dp,
    enabled: Boolean = true,
    captionColor: Color = DeckColors.EngraveLight,
) {
    var pressed by remember { mutableStateOf(false) }
    val view = LocalView.current
    val sounds = LocalMechSounds.current
    val haptics = LocalHaptics.current
    val action by rememberUpdatedState(onPress)
    val depth by animateFloatAsState(if (pressed) 1f else 0f, spring(stiffness = 3000f), label = "mini")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(diameter)
                .semantics { role = Role.Button; contentDescription = caption }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(onPress = {
                        pressed = true
                        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        sounds?.keyUp()
                        action()
                        tryAwaitRelease()
                        pressed = false
                    })
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val r = size.minDimension / 2f
                // Deep dark well
                drawCircle(Color(0xFF070809), r, c)
                drawCircle(Color.White.copy(alpha = 0.35f), r, c + Offset(0f, 1f), style = Stroke(1.1f))
                val cap = r * (0.82f - depth * 0.04f)
                val capCenter = c + Offset(0f, depth * 1.4f)
                drawCircle(
                    Brush.linearGradient(
                        listOf(
                            lerp(Color(0xFFFFFFFF), Color.Black, depth * 0.15f),
                            lerp(Color(0xFFD0D4DA), Color.Black, depth * 0.15f),
                            lerp(Color(0xFF90949B), Color.Black, depth * 0.15f),
                            lerp(Color(0xFFE8EBEF), Color.Black, depth * 0.15f),
                        ),
                        capCenter - Offset(cap, cap),
                        capCenter + Offset(cap, cap),
                    ),
                    cap,
                    capCenter,
                )
                drawCircle(Color.White.copy(alpha = 0.8f), cap - 0.5f, capCenter, style = Stroke(0.9f))
                symbol?.let {
                    drawSymbol(it, capCenter + Offset(0.5f, 0.7f), cap * 0.3f, Color.White.copy(alpha = 0.65f))
                    drawSymbol(it, capCenter, cap * 0.3f, if (enabled) Color(0xFF2D3035) else Color(0xFF7B7F86))
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(caption, style = EngravedCaps.copy(fontSize = 7.sp, letterSpacing = 1.sp, color = captionColor), maxLines = 1)
    }
}
