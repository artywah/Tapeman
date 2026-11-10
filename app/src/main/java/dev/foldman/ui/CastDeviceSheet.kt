package dev.foldman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldman.cast.AndroidCastManager
import dev.foldman.cast.CastDevice
import kotlinx.coroutines.launch

@Composable
fun CastDeviceSheet(
    castManager: AndroidCastManager,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connectedDevice by castManager.connectedDevice.collectAsState()
    val availableDevices by castManager.availableDevices.collectAsState()
    val isScanning by castManager.isScanning.collectAsState()
    val connectingDeviceId by castManager.connectingDeviceId.collectAsState()
    val connectionStatus by castManager.connectionStatus.collectAsState()
    val currentVolume by castManager.volume.collectAsState()

    var sliderVol by remember(currentVolume) { mutableFloatStateOf(currentVolume) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF16181C))
                .border(1.dp, Color(0xFF2E323B), RoundedCornerShape(14.dp))
                .clickable(enabled = false) {}
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⎚", color = Color(0xFF1DB954), fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "CONNECT TO A DEVICE",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFFE5A00D),
                                letterSpacing = 1.1.sp
                            )
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                color = Color(0xFF1DB954),
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Scan",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { castManager.startScan() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = "✕",
                            color = Color(0xFF888B94),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable(onClick = onDismiss)
                                .padding(4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Connection status banner
                if (connectionStatus != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    connectingDeviceId != null -> Color(0xFF332A15)
                                    connectionStatus?.contains("Connected", ignoreCase = true) == true -> Color(0xFF122C1C)
                                    else -> Color(0xFF331616)
                                }
                            )
                            .border(
                                1.dp,
                                when {
                                    connectingDeviceId != null -> Color(0xFFE5A00D).copy(alpha = 0.5f)
                                    connectionStatus?.contains("Connected", ignoreCase = true) == true -> Color(0xFF1DB954).copy(alpha = 0.5f)
                                    else -> Color(0xFFE57373).copy(alpha = 0.5f)
                                },
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (connectingDeviceId != null) {
                                CircularProgressIndicator(
                                    color = Color(0xFFE5A00D),
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp
                                )
                            } else if (connectionStatus?.contains("Connected", ignoreCase = true) == true) {
                                Text("✓", color = Color(0xFF1DB954), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("⚠", color = Color(0xFFE57373), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = connectionStatus ?: "",
                                color = when {
                                    connectingDeviceId != null -> Color(0xFFFFD54F)
                                    connectionStatus?.contains("Connected", ignoreCase = true) == true -> Color(0xFF80E8A8)
                                    else -> Color(0xFFEF9A9A)
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // Current Device Hero Box
                val isCastActive = connectedDevice != null
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCastActive) Color(0xFF10281E) else Color(0xFF1D2026))
                        .border(
                            1.dp,
                            if (isCastActive) Color(0xFF1DB954).copy(alpha = 0.5f) else Color(0xFF333842),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (isCastActive) Color(0xFF1DB954) else Color(0xFF888B94))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Current Device",
                                    color = if (isCastActive) Color(0xFF1DB954) else Color(0xFF888B94),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (isCastActive) {
                                Text(
                                    text = "Disconnect",
                                    color = Color(0xFFE57373),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clickable { castManager.disconnect() }
                                        .padding(4.dp)
                                )
                            } else {
                                Text(
                                    text = "This Phone",
                                    color = Color(0xFFB0B4BC),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isCastActive) "⎚" else "📱", fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = connectedDevice?.name ?: "Phone Speakers",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (isCastActive) "${connectedDevice?.model ?: "Google Cast"} · ${connectedDevice?.address}" else "Local Audio Output",
                                    color = if (isCastActive) Color(0xFFA0D4BA) else Color(0xFF70747E),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        if (isCastActive) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🔈", fontSize = 11.sp)
                                Spacer(Modifier.width(6.dp))
                                Slider(
                                    value = sliderVol,
                                    onValueChange = {
                                        sliderVol = it
                                        castManager.setVolume(it)
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF1DB954),
                                        activeTrackColor = Color(0xFF1DB954),
                                        inactiveTrackColor = Color(0xFF274D38)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("🔊", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Select Another Device Section
                Text(
                    text = "SELECT A NEARBY DEVICE",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF888B94),
                        letterSpacing = 1.sp
                    )
                )

                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val otherDevices = availableDevices.filter { it.id != connectedDevice?.id }
                    if (otherDevices.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isScanning) "Searching for Google Cast devices..." else "No other Cast devices detected on this Wi-Fi.",
                                    color = Color(0xFF70747E),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        items(otherDevices, key = { it.id }) { dev ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1B1D23))
                                    .border(1.dp, Color(0xFF282B34), RoundedCornerShape(6.dp))
                                    .clickable(enabled = connectingDeviceId == null) {
                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                            castManager.connectToDevice(dev)
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("⎚", fontSize = 14.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = dev.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "${dev.model ?: "Google Cast"} · ${dev.address}",
                                            color = Color(0xFF727680),
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                if (connectingDeviceId == dev.id) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF1DB954),
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = "Connect",
                                        color = Color(0xFFE5A00D),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Close",
                        color = Color(0xFF888B94),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}
