package dev.foldman.desktop.spotify

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun DesktopSpotifyBrowser(
    client: DesktopSpotifyClient,
    activeTrackUri: String?,
    navigateToCollectionRequest: Pair<DesktopSpotifyCollection, Int>? = null,
    onSignedInChanged: (Boolean) -> Unit,
    onPlayTrack: (DesktopSpotifyCollection, DesktopSpotifyTrack, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var signedIn by remember { mutableStateOf(client.isSignedIn()) }
    var currentTab by remember { mutableStateOf(DesktopSpotifyTab.PLAYLISTS) }
    var collections by remember { mutableStateOf<List<DesktopSpotifyCollection>>(emptyList()) }
    var openCollection by remember { mutableStateOf<DesktopSpotifyCollection?>(null) }
    var tracks by remember { mutableStateOf<List<DesktopSpotifyTrack>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    var clientIdInput by remember { mutableStateOf(client.config?.clientId.orEmpty()) }
    var bearerInput by remember { mutableStateOf("") }

    fun loadTab(tab: DesktopSpotifyTab = currentTab) {
        if (!client.isSignedIn()) return
        scope.launch {
            loading = true
            statusMessage = null
            try {
                collections = when (tab) {
                    DesktopSpotifyTab.PLAYLISTS -> client.playlists()
                    DesktopSpotifyTab.ALBUMS -> client.savedAlbums()
                }
            } catch (_: DesktopSpotifyAuthExpired) {
                client.clearConfig()
                signedIn = false
                onSignedInChanged(false)
                statusMessage = "Spotify session expired. Please sign in again."
            } catch (t: Throwable) {
                statusMessage = t.message ?: "Failed to load Spotify library."
            } finally {
                loading = false
            }
        }
    }

    fun openCol(col: DesktopSpotifyCollection) {
        openCollection = col
        tracks = emptyList()
        scope.launch {
            loading = true
            statusMessage = null
            try {
                tracks = client.tracks(col)
            } catch (_: DesktopSpotifyAuthExpired) {
                client.clearConfig()
                signedIn = false
                onSignedInChanged(false)
                statusMessage = "Spotify session expired. Please sign in again."
            } catch (t: Throwable) {
                statusMessage = t.message ?: "Failed to load tracks."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(signedIn, currentTab) {
        if (signedIn && openCollection == null) {
            loadTab(currentTab)
        }
    }

    LaunchedEffect(navigateToCollectionRequest) {
        val req = navigateToCollectionRequest ?: return@LaunchedEffect
        if (signedIn) openCol(req.first)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF101114))
            .border(1.dp, Color(0xFF23252C), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        if (!signedIn) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Spotify Connect", color = Color(0xFF1DB954), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Add Redirect URI ${DesktopSpotifyClient.REDIRECT_URI} in your Spotify Developer Dashboard, enter your Client ID, or paste a Bearer token.",
                    color = Color(0xFF90949E),
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF181A20))
                        .border(1.dp, Color(0xFF282B34), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (clientIdInput.isEmpty()) {
                        Text("Spotify Client ID", color = Color(0xFF606470), fontSize = 12.sp)
                    }
                    BasicTextField(
                        value = clientIdInput,
                        onValueChange = { clientIdInput = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                        cursorBrush = SolidColor(Color(0xFF1DB954)),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            statusMessage = "Waiting for browser authorization..."
                            try {
                                client.authenticatePkce(clientIdInput)
                                signedIn = true
                                onSignedInChanged(true)
                                statusMessage = null
                            } catch (t: Throwable) {
                                statusMessage = t.message ?: "Spotify sign-in failed."
                            } finally {
                                loading = false
                            }
                        }
                    },
                    enabled = !loading && clientIdInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954), contentColor = Color.Black),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign in with Spotify (Browser PKCE)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF181A20))
                        .border(1.dp, Color(0xFF282B34), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (bearerInput.isEmpty()) {
                        Text("Or paste Web API Bearer token", color = Color(0xFF606470), fontSize = 12.sp)
                    }
                    BasicTextField(
                        value = bearerInput,
                        onValueChange = { bearerInput = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                        cursorBrush = SolidColor(Color(0xFF1DB954)),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(6.dp))

                Button(
                    onClick = {
                        val tok = bearerInput.trim().removePrefix("Bearer ").trim()
                        if (tok.isNotBlank()) {
                            client.saveConfig(DesktopSpotifyConfig(clientId = clientIdInput.trim(), accessToken = tok))
                            signedIn = true
                            onSignedInChanged(true)
                            statusMessage = null
                        }
                    },
                    enabled = bearerInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242730), contentColor = Color.White),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use Bearer Token", fontWeight = FontWeight.Medium, fontSize = 11.5.sp)
                }

                statusMessage?.let { msg ->
                    Spacer(Modifier.height(10.dp))
                    Text(msg, color = Color(0xFFE5A00D), fontSize = 11.sp)
                }
            }
        } else if (openCollection != null) {
            val col = openCollection!!
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF22252C))
                        .clickable {
                            openCollection = null
                            tracks = emptyList()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("← Back", color = Color(0xFF1DB954), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                SpotifyThumb(client, col.artworkUrl, Modifier.size(40.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(col.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(col.subtitle, color = Color(0xFFA0A4AE), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(Modifier.height(10.dp))

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF1DB954), modifier = Modifier.size(28.dp))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(tracks) { idx, t ->
                        val isCurrent = activeTrackUri != null && t.uri == activeTrackUri
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isCurrent) Color(0xFF10281E) else Color(0xFF191B20))
                                .then(
                                    if (isCurrent) Modifier.border(1.dp, Color(0xFF1DB954).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    else Modifier
                                )
                                .clickable { onPlayTrack(col, t, idx) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isCurrent) "▶" else "%02d".format(idx + 1),
                                    color = Color(0xFF1DB954),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(26.dp)
                                )
                                Column {
                                    Text(
                                        text = t.title,
                                        color = if (isCurrent) Color(0xFF1DB954) else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" · "),
                                        color = Color(0xFF70747E),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                text = formatSpotifyDuration(t.durationMs),
                                color = Color(0xFF888B94),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        } else {
            // Tabs + Sign Out
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DesktopSpotifyTab.entries.forEach { tab ->
                        val active = tab == currentTab
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) Color(0xFF10281E) else Color(0xFF181A20))
                                .border(
                                    1.dp,
                                    if (active) Color(0xFF1DB954) else Color(0xFF282B34),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { currentTab = tab }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = tab.label,
                                color = if (active) Color(0xFF1DB954) else Color(0xFFB0B4BE),
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E2026))
                        .border(1.dp, Color(0xFF333640), RoundedCornerShape(6.dp))
                        .clickable {
                            client.clearConfig()
                            signedIn = false
                            collections = emptyList()
                            onSignedInChanged(false)
                        }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text("Disconnect", color = Color(0xFFA0A4AE), fontSize = 10.5.sp)
                }
            }

            Spacer(Modifier.height(10.dp))

            statusMessage?.let { msg ->
                Text(msg, color = Color(0xFFE5A00D), fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF1DB954), modifier = Modifier.size(28.dp))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 118.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(collections) { col ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF181A20))
                                .border(1.dp, Color(0xFF282B34), RoundedCornerShape(8.dp))
                                .clickable { openCol(col) }
                                .padding(8.dp)
                        ) {
                            Column {
                                SpotifyThumb(client, col.artworkUrl, Modifier.fillMaxWidth().height(110.dp))
                                Spacer(Modifier.height(6.dp))
                                Text(col.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(col.subtitle, color = Color(0xFF888B94), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotifyThumb(
    client: DesktopSpotifyClient,
    url: String?,
    modifier: Modifier = Modifier,
) {
    var bmp by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (!url.isNullOrBlank()) bmp = client.loadArtwork(url)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF20222A)),
        contentAlignment = Alignment.Center
    ) {
        bmp?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } ?: Text("♪", color = Color(0xFF1DB954), fontSize = 20.sp)
    }
}

private fun formatSpotifyDuration(ms: Long): String {
    val sec = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(sec / 60L, sec % 60L)
}
