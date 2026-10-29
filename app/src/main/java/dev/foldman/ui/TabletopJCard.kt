package dev.foldman.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.foldman.deck.CassetteShell
import dev.foldman.deck.DeckUi
import dev.foldman.deck.Source

/**
 * Option C for Flex Mode / Tabletop Posture:
 * The right 50% half of the folded bottom display.
 * Renders an authentic folded Cassette J-Card jewel case insert,
 * liner notes, tape index, and an interactive cassette shell picker (tapedeck.org).
 */
@Composable
fun FlexTabletopJCardPanel(
    ui: DeckUi,
    onCycleShell: () -> Unit,
    onSelectShell: (CassetteShell) -> Unit,
    onPlayQueue: (Long) -> Unit = {},
    onOpenInspector: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val currentShell = ui.shell

    Column(
        modifier
            .glassPanel(18.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. J-Card Spine Header: Brand / Formulation / EQ / Audio Badges (Tap to inspect)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF14161A))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .clickable {
                    if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onOpenInspector()
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Shell Brand Badge
                Text(
                    currentShell.brand.uppercase(),
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        color = when (currentShell) {
                            CassetteShell.TDK_SA, CassetteShell.TDK_MAXG -> Color(0xFFD4AF37)
                            CassetteShell.MAXELL_XLIIS, CassetteShell.MAXELL_UDII -> Color(0xFFFFB300)
                            CassetteShell.SONY_UXPRO -> Color(0xFFE0E0E0)
                            CassetteShell.SONY_HF -> Color(0xFFFF5722)
                            CassetteShell.TEAC_SOUND52 -> Color(0xFFB0BEC5)
                        },
                    ),
                )
                Text(
                    currentShell.displayName.removePrefix(currentShell.brand).trim(),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.9f),
                    ),
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        currentShell.tapeType,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            fontSize = 8.5.sp,
                            color = Color.White.copy(alpha = 0.7f),
                        ),
                    )
                }
            }

            // Hardware Audio Badges
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (ui.dbbEnabled) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFFF8A00).copy(alpha = 0.2f))
                            .border(0.8.dp, Color(0xFFFF8A00).copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "EX DBB",
                            style = TextStyle(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF8A00),
                            ),
                        )
                    }
                }
                if (ui.avlsEnabled) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF00E676).copy(alpha = 0.2f))
                            .border(0.8.dp, Color(0xFF00E676).copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "AVLS",
                            style = TextStyle(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676),
                            ),
                        )
                    }
                }
            }
        }

        // 2. Ruled Paper J-Card Insert (Now Playing / Liner Notes)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF202328), Color(0xFF181A1F))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top Section: Album Artwork + Track Information
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // J-Card Album Art Stamp
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0E1012))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        val art = ui.artwork
                        if (art != null) {
                            Image(
                                bitmap = art,
                                contentDescription = "Album Art",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                "TAPE\nSIDE A",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = Color.White.copy(alpha = 0.3f),
                                ),
                            )
                        }
                    }

                    // Metadata Text
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFFE53935))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    if (ui.reverseDir) "SIDE B" else "SIDE A",
                                    style = TextStyle(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                    ),
                                )
                            }
                            Text(
                                ui.folderName ?: ui.source.label,
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.5f),
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Text(
                            ui.title ?: "No track loaded",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Text(
                            listOfNotNull(ui.artist, ui.album).joinToString(" · ").ifEmpty { "Insert tape or select track" },
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Middle Section: Ruled J-Card Track Index / Liner Notes
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "LINER NOTES & TRACK INDEX",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = Color.White.copy(alpha = 0.45f),
                            ),
                        )
                        Text(
                            if (ui.queue.isNotEmpty()) "${ui.queue.size} TRACKS" else "TYPE IV · 70µs",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                color = Color(0xFFFF8A00).copy(alpha = 0.7f),
                            ),
                        )
                    }

                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF111316))
                            .border(0.8.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        if (ui.queue.isNotEmpty()) {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                itemsIndexed(ui.queue) { index, entry ->
                                    val isCurrent = entry.title.equals(ui.title, ignoreCase = true) || entry.id == ui.currentQueueId
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isCurrent) Color(0xFFFF8A00).copy(alpha = 0.15f) else Color.Transparent)
                                            .clickable {
                                                if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                onPlayQueue(entry.id)
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Row(
                                            Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                "%02d".format(index + 1),
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.sp,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isCurrent) Color(0xFFFF8A00) else Color.White.copy(alpha = 0.4f),
                                                ),
                                            )
                                            Text(
                                                entry.title,
                                                style = TextStyle(
                                                    fontFamily = FontFamily.SansSerif,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.75f),
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (isCurrent) {
                                            Text(
                                                "▶ PLAYING",
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFFF8A00),
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Authentic Vintage Cassette Specification Liner Notes
                            Column(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("TAPE FORMULATION", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = Color.White.copy(alpha = 0.4f)))
                                    Text(currentShell.tapeType, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = Color(0xFFD4AF37)))
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("HEADROOM EXTENSION", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = Color.White.copy(alpha = 0.4f)))
                                    Text("Dolby HX-Pro / DBB Dynamic", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = Color.White.copy(alpha = 0.7f)))
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("SHELL CONSTRUCTION", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = Color.White.copy(alpha = 0.4f)))
                                    Text("Resonance-Damped Polycarbonate", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = Color.White.copy(alpha = 0.7f)))
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("OPTIMUM RECORD LEVEL", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = Color.White.copy(alpha = 0.4f)))
                                    Text("+4 dB Peak / 250 nWb/m", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = Color(0xFF00E676)))
                                }
                            }
                        }
                    }
                }

                // Middle Ruled Lines with Typewriter / Cassette Counter Info
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    // Vintage Ruled Index Line 1
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "INDEX COUNTER",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.45f),
                            ),
                        )
                        val counterVal = ((ui.positionMs / 1000) % 1000).toInt()
                        Text(
                            "[ %03d ]".format(counterVal),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF8A00),
                            ),
                        )
                    }

                    // Vintage Ruled Index Line 2 (Progress Scrubber)
                    val posSec = ui.positionMs / 1000
                    val durSec = ui.durationMs / 1000
                    val timeStr = "%02d:%02d / %02d:%02d".format(posSec / 60, posSec % 60, durSec / 60, durSec % 60)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "ELAPSED TIME",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.45f),
                            ),
                        )
                        Text(
                            timeStr,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.8f),
                            ),
                        )
                    }

                    // Analog Progress Bar
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(ui.progress.coerceIn(0f, 1f))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFFF8A00), Color(0xFFFFC107))
                                    )
                                )
                        )
                    }
                }
            }
        }

        // 3. Cassette Shell Selector (tapedeck.org Archive)
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "CASSETTE SHELL ARCHIVE",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color.White.copy(alpha = 0.5f),
                    ),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "INSPECT ↗",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF8A00),
                        ),
                        modifier = Modifier.clickable {
                            if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onOpenInspector()
                        },
                    )
                    Text(
                        "·",
                        style = TextStyle(color = Color.White.copy(alpha = 0.3f), fontSize = 9.sp),
                    )
                    Text(
                        "TAP TO LOAD",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp,
                            color = Color.White.copy(alpha = 0.6f),
                        ),
                    )
                }
            }

            LazyRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(CassetteShell.entries) { itemShell ->
                    val isSelected = itemShell == currentShell
                    val shellBorder by animateColorAsState(
                        if (isSelected) Color(0xFFFF8A00) else Color.White.copy(alpha = 0.1f),
                        tween(250),
                        label = "shellBorder"
                    )

                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF262A32) else Color(0xFF141619))
                            .border(if (isSelected) 1.2.dp else 0.8.dp, shellBorder, RoundedCornerShape(8.dp))
                            .clickable {
                                if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onSelectShell(itemShell)
                            }
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // LED indicator dot
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) Color(0xFFFF8A00) else Color.White.copy(alpha = 0.2f)
                                )
                        )

                        Column {
                            Text(
                                itemShell.brand.uppercase(),
                                style = TextStyle(
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color(0xFFFF8A00) else Color.White.copy(alpha = 0.5f),
                                ),
                            )
                            Text(
                                itemShell.displayName.removePrefix(itemShell.brand).trim(),
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive Cassette Shell Inspector Modal Dialog:
 * Shows authentic vintage specs, tape formulation, release year, history lore from tapedeck.org,
 * a real-time spinning preview of the shell, and a 1-tap loader into the Walkman deck.
 */
@Composable
fun CassetteShellInspectorDialog(
    currentShell: CassetteShell,
    ui: DeckUi,
    anim: DeckAnimations,
    onSelectShell: (CassetteShell) -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val sounds = LocalMechSounds.current
    var inspectingShell by remember { mutableStateOf(currentShell) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF16181C))
                    .border(1.2.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                    .shadow(24.dp, RoundedCornerShape(18.dp))
                    .clickable(enabled = false) {}
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 1. Header Bar: Title + Close Button
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF8A00))
                        )
                        Text(
                            "CASSETTE SHELL SPECIFICATIONS",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.2.sp,
                                color = Color.White.copy(alpha = 0.7f),
                            ),
                        )
                    }

                    // Close Button
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable {
                                if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "✕",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.8f),
                            ),
                        )
                    }
                }

                // 2. Real-Time Full Cassette Preview (Uncropped, showing the complete cassette shell)
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .height(204.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0C0E10))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val targetW = minOf(maxWidth, maxHeight * FULL_CASSETTE_ASPECT)
                    val targetH = targetW / FULL_CASSETTE_ASPECT
                    val previewUi = remember(inspectingShell, ui) {
                        ui.copy(shell = inspectingShell)
                    }
                    FullCassette(
                        ui = previewUi,
                        anim = anim,
                        stationary = true,
                        modifier = Modifier.size(targetW, targetH),
                    )
                }

                // 3. Cassette Brand, Model, Year & Type Banner
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F1114))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                inspectingShell.brand.uppercase(),
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = when (inspectingShell) {
                                        CassetteShell.TDK_SA, CassetteShell.TDK_MAXG -> Color(0xFFD4AF37)
                                        CassetteShell.MAXELL_XLIIS, CassetteShell.MAXELL_UDII -> Color(0xFFFFB300)
                                        CassetteShell.SONY_UXPRO -> Color(0xFFE0E0E0)
                                        CassetteShell.SONY_HF -> Color(0xFFFF5722)
                                        CassetteShell.TEAC_SOUND52 -> Color(0xFFB0BEC5)
                                    },
                                ),
                            )
                            Text(
                                "· ${inspectingShell.year}",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.5f),
                                ),
                            )
                        }
                        Text(
                            inspectingShell.displayName,
                            style = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            ),
                        )
                    }

                    Box(
                        Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFFF8A00).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFFFF8A00).copy(alpha = 0.5f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            inspectingShell.tapeType,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = Color(0xFFFF8A00),
                            ),
                        )
                    }
                }

                // 4. Specifications & Archive Notes
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1B1E24))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "MAGNETIC FORMULATION",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.45f),
                            ),
                        )
                        Text(
                            inspectingShell.formulation,
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.9f),
                            ),
                        )
                    }

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1B1E24))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "ARCHIVE NOTES",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.45f),
                            ),
                        )
                        Text(
                            inspectingShell.description,
                            style = TextStyle(
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = Color.White.copy(alpha = 0.75f),
                            ),
                        )
                    }
                }

                // 5. Load Into Deck Action Button
                val isLoaded = inspectingShell == currentShell
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isLoaded) Color(0xFF1C221A) else Color(0xFFFF8A00))
                        .border(1.dp, if (isLoaded) Color(0xFF4CAF50).copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable(enabled = !isLoaded) {
                            if (haptics) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            sounds?.keyDown()
                            onSelectShell(inspectingShell)
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isLoaded) Color(0xFF4CAF50) else Color.Black.copy(alpha = 0.4f))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isLoaded) "LOADED IN WALKMAN DECK" else "LOAD THIS CASSETTE INTO DECK",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isLoaded) Color(0xFF81C784) else Color.Black,
                        ),
                    )
                }

                // 6. Shell Selector Carousel
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "SWITCH CASSETTE ARCHIVE",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.45f),
                        ),
                    )
                    LazyRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(CassetteShell.entries) { itemShell ->
                            val isCurrentInspecting = itemShell == inspectingShell
                            val isCurrentlyLoaded = itemShell == currentShell
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrentInspecting) Color(0xFF262A32) else Color(0xFF141619))
                                    .border(
                                        1.dp,
                                        if (isCurrentInspecting) Color(0xFFFF8A00) else Color.White.copy(alpha = 0.1f),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable {
                                        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        inspectingShell = itemShell
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(if (isCurrentlyLoaded) Color(0xFF4CAF50) else if (isCurrentInspecting) Color(0xFFFF8A00) else Color.White.copy(alpha = 0.2f))
                                )
                                Column {
                                    Text(
                                        itemShell.brand.uppercase(),
                                        style = TextStyle(
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCurrentInspecting) Color(0xFFFF8A00) else Color.White.copy(alpha = 0.4f),
                                        ),
                                    )
                                    Text(
                                        itemShell.displayName.removePrefix(itemShell.brand).trim(),
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            fontWeight = if (isCurrentInspecting) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isCurrentInspecting) Color.White else Color.White.copy(alpha = 0.7f),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
