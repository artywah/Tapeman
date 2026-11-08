package dev.foldman.desktop.plex

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldman.deck.LocalTrack
import dev.foldman.deck.PlexAlbum
import dev.foldman.deck.PlexArtist
import dev.foldman.desktop.device.AudioOutputDevice
import dev.foldman.desktop.device.DeviceDiscoveryService
import dev.foldman.desktop.device.DeviceType
import kotlinx.coroutines.launch

enum class PlexBrowserTab(val title: String) {
    HOME("Home"),
    PLAYLISTS("Playlists"),
    RECENT("Recent"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    SEARCH("Search"),
}

@Composable
fun PlexConnectionDialog(
    authService: PlexAuthService,
    currentConfig: PlexSavedConfig?,
    keySoundsEnabled: Boolean,
    deviceService: DeviceDiscoveryService? = null,
    onToggleKeySounds: (Boolean) -> Unit,
    onConnected: (PlexSavedConfig) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isPolling by remember { mutableStateOf(false) }
    var activePin by remember { mutableStateOf<PlexPin?>(null) }
    var pollSeconds by remember { mutableStateOf(0) }
    var statusText by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }

    // Multi-library detection
    var availableLibraries by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loadingLibraries by remember { mutableStateOf(false) }

    LaunchedEffect(currentConfig) {
        if (currentConfig != null) {
            loadingLibraries = true
            availableLibraries = authService.fetchMusicSections(currentConfig.serverUrl, currentConfig.serverToken)
            loadingLibraries = false
        }
    }

    // Manual input state
    var manualUrl by remember { mutableStateOf(currentConfig?.serverUrl ?: "http://localhost:32400") }
    var manualToken by remember { mutableStateOf(currentConfig?.serverToken ?: "") }
    var manualLibrary by remember { mutableStateOf(currentConfig?.libraryName ?: "Music") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF16181C))
                .border(1.dp, Color(0xFF2E323B), RoundedCornerShape(12.dp))
                .clickable(enabled = false) {}
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SETTINGS · PLEX SERVER & AUDIO",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFFE5A00D),
                            letterSpacing = 1.5.sp
                        )
                    )
                    Text(
                        text = "✕",
                        color = Color(0xFF888B94),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(4.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (currentConfig != null) {
                    // Already connected info box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E2127), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF333842), RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF4CAF50), CircleShape)
                                        .border(1.dp, Color(0xFF81C784), CircleShape)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Connected to ${currentConfig.serverName.ifBlank { "Plex Server" }}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = currentConfig.serverUrl,
                                color = Color(0xFF70747E),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )

                            Spacer(Modifier.height(14.dp))

                            // Library Switcher
                            Text(
                                text = "Active Library:",
                                color = Color(0xFFA0A4AE),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))

                            if (loadingLibraries) {
                                CircularProgressIndicator(color = Color(0xFFE5A00D), modifier = Modifier.size(18.dp))
                            } else if (availableLibraries.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    availableLibraries.forEach { (secId, secName) ->
                                        val isSelected = currentConfig.librarySectionId == secId ||
                                                (currentConfig.librarySectionId.isNullOrBlank() && currentConfig.libraryName.equals(secName, ignoreCase = true))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSelected) Color(0xFFE5A00D) else Color(0xFF2A2D35))
                                                .clickable {
                                                    val updated = currentConfig.copy(
                                                        libraryName = secName,
                                                        librarySectionId = secId
                                                    )
                                                    authService.saveConfig(updated)
                                                    onConnected(updated)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 7.dp)
                                        ) {
                                            Text(
                                                text = secName,
                                                color = if (isSelected) Color.Black else Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = currentConfig.libraryName,
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Sound Effects & Mechanism Noise Toggle
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E2127), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF333842), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Sound Effects",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Tactile clicks & solenoid transport noises",
                                color = Color(0xFF70747E),
                                fontSize = 11.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (keySoundsEnabled) Color(0xFFE5A00D) else Color(0xFF2A2D35))
                                .clickable { onToggleKeySounds(!keySoundsEnabled) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (keySoundsEnabled) "ENABLED" else "MUTED",
                                color = if (keySoundsEnabled) Color.Black else Color(0xFF888B94),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Connected Device Quick Info (Spotify Connect style)
                    if (deviceService != null) {
                        val currDev by deviceService.currentDevice.collectAsState()
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E2127), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF333842), RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(currDev.type.icon, fontSize = 13.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Audio Target: ${currDev.name}",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = currDev.description.ifBlank { currDev.model ?: "Audio Device" },
                                    color = Color(0xFF70747E),
                                    fontSize = 11.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF2A2D35))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = currDev.type.label,
                                    color = Color(0xFF00E5FF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B2525)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Disconnect", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2D35)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Done", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (!showManual) {
                    // Browser Sign-in flow
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Sign in with your Plex account in your browser to discover and connect to your servers automatically.",
                            color = Color(0xFFA0A4AE),
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(Modifier.height(20.dp))

                        if (isPolling && activePin != null) {
                            CircularProgressIndicator(
                                color = Color(0xFFE5A00D),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Code: ${activePin?.code}",
                                color = Color(0xFFE5A00D),
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Waiting for authorization in browser (${pollSeconds}s)...",
                                color = Color(0xFF888B94),
                                fontSize = 11.sp
                            )
                            if (statusText.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(text = statusText, color = Color(0xFFE5A00D), fontSize = 11.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isPolling = true
                                        statusText = "Requesting PIN from Plex..."
                                        try {
                                            val pin = authService.requestPin()
                                            activePin = pin
                                            authService.openAuthUrl(pin.authUrl)
                                            statusText = "Opening browser..."

                                            val authToken = authService.pollForToken(pin.id) { sec ->
                                                pollSeconds = sec
                                            }

                                            if (authToken != null) {
                                                statusText = "Signed in! Discovering servers..."
                                                val servers = authService.fetchServers(authToken)
                                                if (servers.isNotEmpty()) {
                                                    val server = servers.first()
                                                    val bestUrl = authService.testConnection(server)
                                                        ?: server.connections.firstOrNull()?.uri
                                                        ?: "http://localhost:32400"

                                                    val sections = authService.fetchMusicSections(bestUrl, server.accessToken)
                                                    val musicSec = sections.firstOrNull { it.second.equals("Music", ignoreCase = true) }
                                                        ?: sections.firstOrNull()

                                                    val config = PlexSavedConfig(
                                                        clientIdentifier = authService.getClientIdentifier(),
                                                        authToken = authToken,
                                                        serverName = server.name,
                                                        serverUrl = bestUrl,
                                                        serverToken = server.accessToken,
                                                        libraryName = musicSec?.second ?: "Music",
                                                        librarySectionId = musicSec?.first,
                                                        keySounds = keySoundsEnabled,
                                                    )
                                                    authService.saveConfig(config)
                                                    onConnected(config)
                                                    onDismiss()
                                                } else {
                                                    statusText = "No accessible Plex servers found."
                                                }
                                            } else {
                                                statusText = "Sign-in timed out. Please try again."
                                            }
                                        } catch (t: Throwable) {
                                            statusText = "Error: ${t.localizedMessage}"
                                        } finally {
                                            isPolling = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5A00D), contentColor = Color.Black),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Text("Sign In with Plex Browser", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Spacer(Modifier.height(12.dp))

                            Text(
                                text = "Or configure server connection manually →",
                                color = Color(0xFF888B94),
                                fontSize = 11.sp,
                                modifier = Modifier.clickable { showManual = true }
                            )
                        }
                    }
                } else {
                    // Manual connection fields
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = manualUrl,
                            onValueChange = { manualUrl = it },
                            label = { Text("Server URL (e.g. https://plex.mydomain.com)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFE5A00D),
                                unfocusedBorderColor = Color(0xFF333842),
                                focusedLabelColor = Color(0xFFE5A00D),
                                cursorColor = Color(0xFFE5A00D),
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = manualToken,
                            onValueChange = { manualToken = it },
                            label = { Text("X-Plex-Token") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFE5A00D),
                                unfocusedBorderColor = Color(0xFF333842),
                                focusedLabelColor = Color(0xFFE5A00D),
                                cursorColor = Color(0xFFE5A00D),
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = manualLibrary,
                            onValueChange = { manualLibrary = it },
                            label = { Text("Library Name (default: Music)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFE5A00D),
                                unfocusedBorderColor = Color(0xFF333842),
                                focusedLabelColor = Color(0xFFE5A00D),
                                cursorColor = Color(0xFFE5A00D),
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = { showManual = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2D35)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Back to Browser Sign-In", color = Color.White, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        val sections = authService.fetchMusicSections(manualUrl, manualToken)
                                        val sec = sections.firstOrNull { it.second.equals(manualLibrary, ignoreCase = true) }
                                            ?: sections.firstOrNull()

                                        val config = PlexSavedConfig(
                                            clientIdentifier = authService.getClientIdentifier(),
                                            authToken = manualToken,
                                            serverName = "Plex Server",
                                            serverUrl = manualUrl,
                                            serverToken = manualToken,
                                            libraryName = manualLibrary,
                                            librarySectionId = sec?.first,
                                            keySounds = keySoundsEnabled,
                                        )
                                        authService.saveConfig(config)
                                        onConnected(config)
                                        onDismiss()
                                    }
                                },
                                enabled = manualUrl.isNotBlank() && manualToken.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5A00D), contentColor = Color.Black),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Save & Connect", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Spotify Connect style device picker dialog.
 * Lists Google Cast, AirPlay, Bluetooth, and local system audio targets.
 */
@Composable
fun ConnectToDeviceDialog(
    deviceService: DeviceDiscoveryService,
    onDismiss: () -> Unit,
) {
    val currentDevice by deviceService.currentDevice.collectAsState()
    val availableDevices by deviceService.availableDevices.collectAsState()
    val isScanning by deviceService.isScanning.collectAsState()
    val connectingDeviceId by deviceService.connectingDeviceId.collectAsState()
    val connectionStatus by deviceService.connectionStatus.collectAsState()

    var sliderVol by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF16181C))
                .border(1.dp, Color(0xFF2E323B), RoundedCornerShape(12.dp))
                .clickable(enabled = false) {}
                .padding(20.dp)
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
                                fontSize = 12.sp,
                                color = Color(0xFFE5A00D),
                                letterSpacing = 1.2.sp
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
                                    .clickable { deviceService.scanAllDevices() }
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

                // Live Connection Status Banner (Connecting / Connected / Error)
                if (!connectionStatus.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    connectingDeviceId != null -> Color(0xFF2A2210)
                                    connectionStatus?.contains("Connected", ignoreCase = true) == true -> Color(0xFF10281E)
                                    else -> Color(0xFF2D1616)
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
                } else {
                    Spacer(Modifier.height(4.dp))
                }

                // Current Device Hero Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF10281E))
                        .border(1.dp, Color(0xFF1DB954).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Animated Equalizer LED
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF1DB954), CircleShape)
                                        .border(1.dp, Color(0xFF69F0AE), CircleShape)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Current Device",
                                    color = Color(0xFF1DB954),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = currentDevice.type.label,
                                color = Color(0xFF80E8A8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(currentDevice.type.icon, fontSize = 20.sp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = currentDevice.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = currentDevice.description.ifBlank { "Tapeman Audio Engine" },
                                    color = Color(0xFFA0D4BA),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Device Volume Slider
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🔈", fontSize = 12.sp)
                            Spacer(Modifier.width(6.dp))
                            Slider(
                                value = sliderVol,
                                onValueChange = { sliderVol = it },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF1DB954),
                                    activeTrackColor = Color(0xFF1DB954),
                                    inactiveTrackColor = Color(0xFF274D38)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("🔊", fontSize = 12.sp)
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
                        .height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val otherDevices = availableDevices.filter { it.id != currentDevice.id }
                    if (otherDevices.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isScanning) "Searching for Google Cast & Bluetooth devices..." else "No other devices detected on this network.",
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
                                        deviceService.selectDevice(dev)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(dev.type.icon, fontSize = 16.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = dev.name,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (dev.address != null) "${dev.model ?: dev.type.label} · ${dev.address}" else (dev.model ?: dev.type.label),
                                            color = Color(0xFF70747E),
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                if (dev.isConnecting || connectingDeviceId == dev.id) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color(0xFFE5A00D),
                                            modifier = Modifier.size(13.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = "Connecting...",
                                            color = Color(0xFFE5A00D),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Connect",
                                        color = if (connectingDeviceId != null) Color(0xFF555760) else Color(0xFFE5A00D),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282B33)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Close", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Two-pane desktop Plex Music browser with Home, Playlists, Sonic DJ, and multi-library switching.
 */
@Composable
fun PlexMusicBrowser(
    client: DesktopPlexClient,
    currentConfig: PlexSavedConfig?,
    isSonicDjActive: Boolean = false,
    onToggleSonicDj: () -> Unit = {},
    onSwitchLibrary: (sectionKey: String, name: String) -> Unit,
    onTrackSelected: (LocalTrack, List<LocalTrack>, PlexAlbum) -> Unit,
    onPlayAlbumDirectly: (PlexAlbum) -> Unit = {},
    onOpenConnectDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentTab by remember { mutableStateOf(PlexBrowserTab.HOME) }
    var searchQuery by remember { mutableStateOf("") }

    var homeData by remember { mutableStateOf<PlexHomeData?>(null) }
    var playlists by remember { mutableStateOf<List<PlexAlbum>>(emptyList()) }
    var recentAlbums by remember { mutableStateOf<List<PlexAlbum>>(emptyList()) }
    var allAlbums by remember { mutableStateOf<List<PlexAlbum>>(emptyList()) }
    var artists by remember { mutableStateOf<List<PlexArtist>>(emptyList()) }

    var selectedAlbum by remember { mutableStateOf<PlexAlbum?>(null) }
    var albumTracks by remember { mutableStateOf<List<LocalTrack>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    // Multi-library switcher list
    var availableLibraries by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    val scope = rememberCoroutineScope()
    val isConfigured = currentConfig != null && currentConfig.serverUrl.isNotBlank() && currentConfig.serverToken.isNotBlank()

    fun loadCurrentTab() {
        if (!isConfigured) return
        scope.launch {
            loading = true
            try {
                currentConfig?.let {
                    client.serverUrl = it.serverUrl
                    client.token = it.serverToken
                    it.librarySectionId?.let { sec -> client.sectionId = sec }
                }
                when (currentTab) {
                    PlexBrowserTab.HOME -> {
                        homeData = client.fetchHomeData()
                    }
                    PlexBrowserTab.PLAYLISTS -> {
                        playlists = client.fetchPlaylists(searchQuery)
                    }
                    PlexBrowserTab.RECENT -> {
                        recentAlbums = client.fetchRecentAlbums()
                    }
                    PlexBrowserTab.ALBUMS -> {
                        allAlbums = client.fetchAllAlbums(searchQuery)
                    }
                    PlexBrowserTab.ARTISTS -> {
                        artists = client.fetchArtists()
                    }
                    PlexBrowserTab.SEARCH -> {
                        allAlbums = client.fetchAllAlbums(searchQuery)
                    }
                }
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(currentTab, currentConfig?.serverUrl, currentConfig?.serverToken, currentConfig?.librarySectionId) {
        if (isConfigured) {
            currentConfig?.let {
                client.serverUrl = it.serverUrl
                client.token = it.serverToken
                it.librarySectionId?.let { sec -> client.sectionId = sec }
            }
            availableLibraries = client.fetchAvailableLibraries()
            loadCurrentTab()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF131417))
            .border(1.dp, Color(0xFF22242B), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        if (!isConfigured) {
            // Not connected placeholder
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "PLEX MUSIC LIBRARY",
                    color = Color(0xFFE5A00D),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Sign in to Plex to stream your albums, playlists & Sonic DJ",
                    color = Color(0xFF888B94),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onOpenConnectDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5A00D), contentColor = Color.Black),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Connect to Plex", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        } else if (selectedAlbum != null) {
            // Album or Playlist Tracklist View
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF22252C))
                            .clickable {
                                selectedAlbum = null
                                albumTracks = emptyList()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("← Back", color = Color(0xFFE5A00D), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    AlbumThumbnail(
                        client = client,
                        url = selectedAlbum?.coverUrl,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedAlbum?.title ?: "",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                        Text(
                            text = if (selectedAlbum?.isPlaylist == true) "Playlist · ${selectedAlbum?.trackCount ?: 0} tracks" else "${selectedAlbum?.artist ?: ""} · ${selectedAlbum?.year ?: ""}",
                            color = Color(0xFFA0A4AE),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFE5A00D), modifier = Modifier.size(28.dp))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(albumTracks) { index, track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF191B20))
                                    .clickable {
                                        selectedAlbum?.let { alb ->
                                            onTrackSelected(track, albumTracks, alb)
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "%02d".format(index + 1),
                                        color = Color(0xFFE5A00D),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.width(26.dp)
                                    )
                                    Column {
                                        Text(
                                            text = track.title,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = track.artist,
                                            color = Color(0xFF70747E),
                                            fontSize = 10.sp,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Text(
                                    text = formatDuration(track.durationMs),
                                    color = Color(0xFF888B94),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Main Browser View
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Library Switcher Header
                if (availableLibraries.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Library: ",
                            color = Color(0xFF888B94),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.width(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            availableLibraries.forEach { (secId, secName) ->
                                val isSelected = client.sectionId == secId ||
                                        (client.sectionId.isBlank() && currentConfig?.librarySectionId == secId) ||
                                        (client.sectionId.isBlank() && currentConfig?.libraryName.equals(secName, ignoreCase = true))

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) Color(0xFFE5A00D) else Color(0xFF1E2026))
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFFE5A00D) else Color(0xFF2C2F38),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable {
                                            selectedAlbum = null
                                            onSwitchLibrary(secId, secName)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = secName,
                                        color = if (isSelected) Color.Black else Color(0xFFDFDFE5),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Section Tabs: Home / Playlists / Recent / Albums / Artists / Search
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlexBrowserTab.entries.forEach { tab ->
                        val isSelected = tab == currentTab
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) Color(0xFF2D2F38) else Color(0xFF181A20))
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFFE5A00D) else Color(0xFF262832),
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    currentTab = tab
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = tab.title,
                                color = if (isSelected) Color(0xFFE5A00D) else Color(0xFFA0A4AE),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                if (currentTab == PlexBrowserTab.SEARCH) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1A1C22))
                            .border(1.dp, Color(0xFF2E323C), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                loadCurrentTab()
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                            cursorBrush = SolidColor(Color(0xFFE5A00D)),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text("Search artists, albums...", color = Color(0xFF666A74), fontSize = 12.sp)
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFE5A00D), modifier = Modifier.size(30.dp))
                    }
                } else if (currentTab == PlexBrowserTab.HOME) {
                    // ==========================================
                    // HOME VIEW (Sonic DJ + Loved Tracks + Recents + Hubs)
                    // ==========================================
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        // 1. Hero DJ Booth: Sonic DJ Card + Loved Tracks Card
                        item(key = "home_dj_booth") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Sonic DJ Hero Card
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSonicDjActive) Color(0xFF003840) else Color(0xFF191C22))
                                        .border(
                                            1.dp,
                                            if (isSonicDjActive) Color(0xFF00E5FF) else Color(0xFF282C36),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable(onClick = onToggleSonicDj)
                                        .padding(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🎧", fontSize = 13.sp)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (isSonicDjActive) "SONIC DJ: ON" else "SONIC DJ: OFF",
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSonicDjActive) Color(0xFF00E5FF) else Color.White,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = if (isSonicDjActive) "Continuous Flow Active" else "Acoustic DJ · Tap to Start",
                                        fontSize = 10.sp,
                                        color = if (isSonicDjActive) Color(0xFF80F2FF) else Color(0xFF888B94),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Loved Tracks Hero Card
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF241E15))
                                        .border(1.dp, Color(0xFFE5A00D).copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            val favAlbum = PlexAlbum(
                                                id = DesktopPlexClient.SPECIAL_FAVORITES_ID,
                                                title = "★ Loved Tracks",
                                                artist = "Plex Favorites",
                                                isPlaylist = true
                                            )
                                            selectedAlbum = favAlbum
                                            scope.launch {
                                                loading = true
                                                albumTracks = client.fetchFavorites()
                                                loading = false
                                            }
                                        }
                                        .padding(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("★", color = Color(0xFFE5A00D), fontSize = 13.sp)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "LOVED TRACKS",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE5A00D),
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = "Your Favorited Songs",
                                        fontSize = 10.sp,
                                        color = Color(0xFFB0B4BE),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // 2. Jump Back In (Recently Played)
                        val recents = homeData?.recentlyPlayed ?: emptyList()
                        if (recents.isNotEmpty()) {
                            item(key = "home_recents") {
                                Column {
                                    HomeSectionHeader("JUMP BACK IN", "⚡")
                                    Spacer(Modifier.height(8.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(recents, key = { "recent_${it.id}" }) { album ->
                                            HomeAlbumCard(
                                                client = client,
                                                album = album,
                                                onClick = {
                                                    selectedAlbum = album
                                                    scope.launch {
                                                        loading = true
                                                        albumTracks = client.fetchTracks(album)
                                                        loading = false
                                                    }
                                                },
                                                onPlayDirectly = {
                                                    onPlayAlbumDirectly(album)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Fans Also Like (Similar Artists)
                        val similar = homeData?.similarArtists ?: emptyList()
                        if (similar.isNotEmpty()) {
                            item(key = "home_similar_artists") {
                                Column {
                                    HomeSectionHeader(
                                        title = "FANS ALSO LIKE (${homeData?.recentArtist?.name?.uppercase() ?: "RECENT ARTIST"})",
                                        icon = "✨"
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(similar, key = { "sim_${it.id}" }) { artist ->
                                            HomeArtistAvatarCard(
                                                client = client,
                                                artist = artist,
                                                onOpen = {
                                                    searchQuery = artist.name
                                                    currentTab = PlexBrowserTab.ALBUMS
                                                    loadCurrentTab()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Dynamic Hubs
                        val hubs = homeData?.hubs ?: emptyList()
                        hubs.forEach { hub ->
                            item(key = "hub_${hub.hubIdentifier}") {
                                Column {
                                    HomeSectionHeader(title = hub.title.uppercase(), icon = "💿")
                                    Spacer(Modifier.height(8.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(hub.items, key = { "hub_${hub.hubIdentifier}_${it.id}" }) { album ->
                                            HomeAlbumCard(
                                                client = client,
                                                album = album,
                                                onClick = {
                                                    selectedAlbum = album
                                                    scope.launch {
                                                        loading = true
                                                        albumTracks = client.fetchTracks(album)
                                                        loading = false
                                                    }
                                                },
                                                onPlayDirectly = {
                                                    onPlayAlbumDirectly(album)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (currentTab == PlexBrowserTab.PLAYLISTS) {
                    // ==========================================
                    // PLAYLISTS VIEW
                    // ==========================================
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 125.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(playlists) { playlist ->
                            val isLoved = playlist.id == DesktopPlexClient.SPECIAL_FAVORITES_ID
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isLoved) Color(0xFF241E15) else Color(0xFF181A20))
                                    .border(
                                        1.dp,
                                        if (isLoved) Color(0xFFE5A00D).copy(alpha = 0.6f) else Color(0xFF282B34),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedAlbum = playlist
                                        scope.launch {
                                            loading = true
                                            albumTracks = client.fetchTracks(playlist)
                                            loading = false
                                        }
                                    }
                                    .padding(8.dp)
                            ) {
                                Column {
                                    if (isLoved) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(115.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF33291B)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("★", color = Color(0xFFE5A00D), fontSize = 38.sp)
                                        }
                                    } else {
                                        AlbumThumbnail(
                                            client = client,
                                            url = playlist.coverUrl,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(115.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = playlist.title,
                                        color = if (isLoved) Color(0xFFE5A00D) else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = playlist.artist,
                                        color = Color(0xFF888B94),
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                } else if (currentTab == PlexBrowserTab.ARTISTS) {
                    // ==========================================
                    // ARTISTS VIEW
                    // ==========================================
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 105.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(artists) { artist ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF181A20))
                                    .border(1.dp, Color(0xFF282B34), RoundedCornerShape(8.dp))
                                    .clickable {
                                        searchQuery = artist.name
                                        currentTab = PlexBrowserTab.ALBUMS
                                        loadCurrentTab()
                                    }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    AlbumThumbnail(
                                        client = client,
                                        url = artist.thumbUrl,
                                        shape = CircleShape,
                                        iconSize = 20.sp,
                                        modifier = Modifier.size(76.dp)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = artist.name,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ==========================================
                    // ALBUMS & RECENT VIEW
                    // ==========================================
                    val itemsToDisplay = if (currentTab == PlexBrowserTab.RECENT) recentAlbums else allAlbums

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 125.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(itemsToDisplay) { album ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF181A20))
                                    .border(1.dp, Color(0xFF282B34), RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedAlbum = album
                                        scope.launch {
                                            loading = true
                                            albumTracks = client.fetchTracks(album)
                                            loading = false
                                        }
                                    }
                                    .padding(8.dp)
                            ) {
                                Column {
                                    AlbumThumbnail(
                                        client = client,
                                        url = album.coverUrl,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(115.dp)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = album.title,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = album.artist,
                                        color = Color(0xFF888B94),
                                        fontSize = 10.sp,
                                        maxLines = 1
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

@Composable
private fun HomeSectionHeader(title: String, icon: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 11.sp, color = Color(0xFFE5A00D))
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            style = TextStyle(
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        )
    }
}

@Composable
private fun HomeAlbumCard(
    client: DesktopPlexClient,
    album: PlexAlbum,
    onClick: () -> Unit,
    onPlayDirectly: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(108.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp)
    ) {
        Box(modifier = Modifier.size(108.dp)) {
            AlbumThumbnail(
                client = client,
                url = album.coverUrl,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
            )
            // Play badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC000000))
                    .border(1.dp, Color(0xFFE5A00D).copy(alpha = 0.5f), CircleShape)
                    .clickable(onClick = onPlayDirectly),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = Color(0xFFE5A00D), fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = album.title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artist,
            color = Color(0xFF888B94),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomeArtistAvatarCard(
    client: DesktopPlexClient,
    artist: PlexArtist,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(84.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AlbumThumbnail(
            client = client,
            url = artist.thumbUrl,
            shape = CircleShape,
            modifier = Modifier.size(68.dp)
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = artist.name,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AlbumThumbnail(
    client: DesktopPlexClient,
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
    iconSize: TextUnit = 24.sp,
) {
    var bitmap by remember(url) { mutableStateOf(client.getCachedArtwork(url)) }
    LaunchedEffect(url) {
        if (bitmap == null && !url.isNullOrBlank()) {
            bitmap = client.loadArtwork(url)
        }
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF20232B)),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("♪", color = Color(0xFFE5A00D).copy(alpha = 0.7f), fontSize = iconSize)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val sec = ms / 1000
    val m = sec / 60
    val s = sec % 60
    return "%d:%02d".format(m, s)
}
