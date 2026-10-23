package dev.foldman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldman.deck.SpotifyUi
import dev.foldman.spotify.SpotifyApi
import dev.foldman.spotify.SpotifyCollection
import dev.foldman.spotify.SpotifyTab

class SpotifyActions(
    val signIn: () -> Unit,
    val signOut: () -> Unit,
    val toggleEditClient: (Boolean) -> Unit,
    val saveConfig: (String, String) -> Unit,
    val setTab: (SpotifyTab) -> Unit,
    val refresh: () -> Unit,
    val openCollection: (SpotifyCollection) -> Unit,
    val closeCollection: () -> Unit,
    val play: (Int) -> Unit,
)

/**
 * Spotify library browser (OAuth 2.0 PKCE + Web API) with media-session fallback.
 * Browses user playlists and saved albums, and triggers playback directly in Spotify.
 */
@Composable
fun SpotifyBrowser(
    spotify: SpotifyUi,
    actions: SpotifyActions,
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    light: Boolean = false,
) {
    val ink = if (light) DeckColors.Ink else Color(0xFFF4EFE9)
    val sub = if (light) DeckColors.Ink.copy(alpha = 0.62f) else Color(0xFFA79C92)
    val green = Color(0xFF1DB954)
    val titleStyle = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val small = TextStyle(color = sub, fontSize = 11.sp, lineHeight = 15.sp)
    val link = small.copy(color = green, fontWeight = FontWeight.Bold)

    Column(modifier) {
        when {
            spotify.editingClient -> {
                var clientId by rememberSaveable(spotify.clientId) { mutableStateOf(spotify.clientId) }
                var token by rememberSaveable { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Spotify API Setup", style = titleStyle.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                    Text("Cancel", style = link.copy(color = sub), modifier = Modifier.clickable { actions.toggleEditClient(false) }.padding(4.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Add Redirect URI ${SpotifyApi.REDIRECT_URI} in your Spotify Developer Dashboard, then enter your Client ID (or paste a Web API Bearer token directly).",
                    style = small,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    singleLine = true,
                    label = { Text("Spotify Client ID", style = small) },
                    textStyle = small.copy(color = ink),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    singleLine = true,
                    label = { Text("Optional: Bearer Access Token", style = small) },
                    textStyle = small.copy(color = ink),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                SpotifyPill("Save", green, enabled = clientId.isNotBlank() || token.isNotBlank()) {
                    actions.saveConfig(clientId, token)
                }
            }
            !spotify.signedIn -> {
                fallback()
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpotifyPill(
                        if (spotify.clientId.isBlank()) "Configure Spotify API" else "Sign in to Spotify Library",
                        green,
                    ) {
                        if (spotify.clientId.isBlank()) actions.toggleEditClient(true) else actions.signIn()
                    }
                    if (spotify.clientId.isNotBlank()) {
                        Text("Client ID ⚙", style = link.copy(color = sub), modifier = Modifier.clickable { actions.toggleEditClient(true) }.padding(4.dp))
                    }
                }
            }
            spotify.openCollection != null -> {
                val col = spotify.openCollection
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("‹ ${spotify.tab.label}", style = link, modifier = Modifier.clickable(onClick = actions.closeCollection).padding(vertical = 4.dp))
                    Spacer(Modifier.weight(1f))
                    SpotifyPill("Play Tape", green) { actions.play(0) }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UrlThumb(col.artworkUrl, Modifier.size(40.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(col.name, style = titleStyle.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(col.subtitle, style = small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (spotify.loading) {
                    Text("Loading tracks…", style = small)
                } else if (spotify.tracks.isEmpty() && spotify.message == null) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ink.copy(alpha = 0.06f))
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Followed Playlist",
                            style = titleStyle.copy(fontWeight = FontWeight.SemiBold),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap PLAY TAPE above to load and stream directly on your deck.",
                            style = small,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                LazyColumn {
                    itemsIndexed(spotify.tracks, key = { i, t -> "${i}_${t.id}" }) { i, t ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { actions.play(i) }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UrlThumb(t.artworkUrl, Modifier.size(34.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, style = titleStyle.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" · "), style = small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(formatTime(t.durationMs), style = small)
                        }
                    }
                }
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SpotifyTab.entries.forEach { t ->
                        val sel = t == spotify.tab
                        Text(
                            t.label.uppercase(),
                            style = small.copy(
                                color = if (sel) Color(0xFF0E1410) else ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (sel) green else ink.copy(alpha = 0.1f))
                                .clickable { actions.setTab(t) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text("↻", style = link.copy(fontSize = 14.sp), modifier = Modifier.clickable(onClick = actions.refresh).padding(horizontal = 4.dp))
                    Text("Sign out", style = link.copy(color = sub), modifier = Modifier.clickable(onClick = actions.signOut).padding(4.dp))
                }
                Spacer(Modifier.height(6.dp))
                if (spotify.loading) Text("Loading Spotify library…", style = small)
                LazyColumn {
                    items(spotify.collections, key = { it.id }) { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { actions.openCollection(c) }
                                .padding(vertical = 5.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UrlThumb(c.artworkUrl, Modifier.size(38.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(c.subtitle, style = small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("›", style = small.copy(fontSize = 18.sp))
                        }
                    }
                }
            }
        }
        spotify.message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = small.copy(color = Color(0xFFE0604A)))
        }
    }
}

@Composable
private fun SpotifyPill(text: String, green: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text.uppercase(),
        style = TextStyle(color = Color(0xFF0B140E), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) green else green.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
