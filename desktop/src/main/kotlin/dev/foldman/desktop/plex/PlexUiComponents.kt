package dev.foldman.desktop.plex

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldman.deck.LocalTrack
import dev.foldman.deck.PlexAlbum
import dev.foldman.deck.PlexArtist
import kotlinx.coroutines.launch

enum class PlexBrowserTab(val title: String) {
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
                .clickable(enabled = false) {} // Consume clicks inside dialog
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SETTINGS · PLEX SERVER",
                        color = Color(0xFFE5A00D), // Plex Gold
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = "✕",
                        color = Color(0xFF888B94),
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(4.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (currentConfig != null) {
                    // Currently connected state
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
                    Spacer(Modifier.height(14.dp))
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
                                color = Color(0xFF7E828D),
                                fontSize = 11.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (keySoundsEnabled) Color(0xFFE5A00D) else Color(0xFF282A31))
                                .border(1.dp, if (keySoundsEnabled) Color(0xFFE5A00D) else Color(0xFF3E424D), RoundedCornerShape(6.dp))
                                .clickable { onToggleKeySounds(!keySoundsEnabled) }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = if (keySoundsEnabled) "ENABLED" else "MUTED",
                                color = if (keySoundsEnabled) Color.Black else Color(0xFFB0B4BE),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                onDisconnect()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B2B2B)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Disconnect", fontSize = 12.sp)
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2D35)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Done", fontSize = 12.sp)
                        }
                    }
                } else if (isPolling && activePin != null) {
                    // Waiting for browser authorization
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFE5A00D),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Authorize in your browser",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Code: ${activePin?.code?.uppercase()}",
                            color = Color(0xFFE5A00D),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = statusText.ifBlank { "Waiting for plex.tv ($pollSeconds s)..." },
                            color = Color(0xFF888B94),
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { activePin?.let { authService.openAuthUrl(it.authUrl) } },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5A00D), contentColor = Color.Black),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Re-open Browser", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { isPolling = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2D35)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Cancel", fontSize = 12.sp)
                            }
                        }
                    }
                } else if (!showManual) {
                    // Standard sign in options
                    Button(
                        onClick = {
                            scope.launch {
                                isPolling = true
                                statusText = "Requesting link from Plex..."
                                try {
                                    val pin = authService.requestPin()
                                    activePin = pin
                                    authService.openAuthUrl(pin.authUrl)
                                    statusText = "Waiting for authorization in browser..."
                                    val token = authService.pollForToken(pin.id) { sec ->
                                        pollSeconds = sec
                                    }
                                    if (token != null) {
                                        statusText = "Discovering your Plex servers..."
                                        val servers = authService.fetchServers(token)
                                        val server = servers.firstOrNull()
                                        if (server != null) {
                                            statusText = "Testing connection to ${server.name}..."
                                            val url = authService.testConnection(server) ?: server.connections.first().uri
                                            val sections = authService.fetchMusicSections(url, server.accessToken)
                                            // Prefer a library explicitly called "Music"
                                            val musicSec = sections.find { it.second.equals("Music", ignoreCase = true) }
                                                ?: sections.firstOrNull()

                                            val config = PlexSavedConfig(
                                                clientIdentifier = authService.getClientIdentifier(),
                                                authToken = token,
                                                serverName = server.name,
                                                serverUrl = url,
                                                serverToken = server.accessToken,
                                                libraryName = musicSec?.second ?: "Music",
                                                librarySectionId = musicSec?.first,
                                                keySounds = keySoundsEnabled,
                                            )
                                            authService.saveConfig(config)
                                            onConnected(config)
                                            onDismiss()
                                        } else {
                                            statusText = "No Plex servers found under your account."
                                            isPolling = false
                                        }
                                    } else {
                                        statusText = "Sign in timed out."
                                        isPolling = false
                                    }
                                } catch (e: Exception) {
                                    statusText = "Error: ${e.message}"
                                    isPolling = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE5A00D), // Plex gold
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Sign In with Plex",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "Or enter Server URL & Token manually",
                        color = Color(0xFF888B94),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clickable { showManual = true }
                            .padding(8.dp)
                    )
                } else {
                    // Manual entry mode
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Server URL (e.g. http://192.168.1.100:32400):", color = Color(0xFFA0A4AE), fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = manualUrl,
                            onValueChange = { manualUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFE5A00D),
                                unfocusedBorderColor = Color(0xFF333842)
                            )
                        )

                        Spacer(Modifier.height(12.dp))
                        Text("X-Plex-Token:", color = Color(0xFFA0A4AE), fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = manualToken,
                            onValueChange = { manualToken = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFE5A00D),
                                unfocusedBorderColor = Color(0xFF333842)
                            )
                        )

                        Spacer(Modifier.height(12.dp))
                        Text("Library Name:", color = Color(0xFFA0A4AE), fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = manualLibrary,
                            onValueChange = { manualLibrary = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFE5A00D),
                                unfocusedBorderColor = Color(0xFF333842)
                            )
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
                                Text("Back", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        val config = PlexSavedConfig(
                                            clientIdentifier = authService.getClientIdentifier(),
                                            authToken = manualToken.trim(),
                                            serverName = "Plex Media Server",
                                            serverUrl = manualUrl.trim(),
                                            serverToken = manualToken.trim(),
                                            libraryName = manualLibrary.trim().ifBlank { "Music" },
                                            keySounds = keySoundsEnabled,
                                        )
                                        authService.saveConfig(config)
                                        onConnected(config)
                                        onDismiss()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5A00D), contentColor = Color.Black),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Two-pane or drawer desktop Plex Music browser with multi-library switcher.
 */
@Composable
fun PlexMusicBrowser(
    client: DesktopPlexClient,
    currentConfig: PlexSavedConfig?,
    onSwitchLibrary: (sectionKey: String, name: String) -> Unit,
    onTrackSelected: (LocalTrack, List<LocalTrack>, PlexAlbum) -> Unit,
    onOpenConnectDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentTab by remember { mutableStateOf(PlexBrowserTab.RECENT) }
    var searchQuery by remember { mutableStateOf("") }
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
                // Ensure client credentials match config before loading
                currentConfig?.let {
                    client.serverUrl = it.serverUrl
                    client.token = it.serverToken
                    it.librarySectionId?.let { sec -> client.sectionId = sec }
                }
                when (currentTab) {
                    PlexBrowserTab.RECENT -> recentAlbums = client.fetchRecentAlbums()
                    PlexBrowserTab.ALBUMS -> allAlbums = client.fetchAllAlbums(searchQuery)
                    PlexBrowserTab.ARTISTS -> artists = client.fetchArtists()
                    PlexBrowserTab.SEARCH -> allAlbums = client.fetchAllAlbums(searchQuery)
                }
            } finally {
                loading = false
            }
        }
    }

    // Refresh libraries and tab content when configuration or section changes
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
                    text = "Sign in to Plex to stream your albums & artists",
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
            // Album Detail / Tracklist View
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
                            text = "${selectedAlbum?.artist ?: ""} · ${selectedAlbum?.year ?: ""}",
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
            // Main Library View (Library Switcher + Tabs + Albums/Artists)
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Library Switcher Header (if multiple libraries exist)
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

                // Section Tabs: Recent / Albums / Artists / Search
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
                                .padding(horizontal = 10.dp, vertical = 5.dp)
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
                } else if (currentTab == PlexBrowserTab.ARTISTS) {
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
