package dev.foldman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldman.apple.AppleMusicApi
import dev.foldman.apple.ApplePlaylist
import dev.foldman.deck.AppleUi

class AppleActions(
    val signIn: () -> Unit,
    val signOut: () -> Unit,
    val setDeveloperToken: (String) -> Unit,
    val refresh: () -> Unit,
    val openPlaylist: (ApplePlaylist) -> Unit,
    val closePlaylist: () -> Unit,
    val play: (Int) -> Unit,
)

/**
 * The Apple Music shelf: walks through setup (SDK → developer token → sign-in), then lists the
 * library's playlists and their songs. Tap a playlist to open it, a song to play from there.
 * [light] draws it for the paper J-card on the cover screen, otherwise for the dark glass panel.
 */
@Composable
fun AppleBrowser(apple: AppleUi, actions: AppleActions, fallback: @Composable () -> Unit, modifier: Modifier = Modifier, light: Boolean = false) {
    val ink = if (light) DeckColors.Ink else Color(0xFFF4EFE9)
    val sub = if (light) DeckColors.Ink.copy(alpha = 0.6f) else Color(0xFFA79C92)
    val title = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    val small = TextStyle(color = sub, fontSize = 12.sp, lineHeight = 16.sp)
    val link = small.copy(color = DeckColors.Accent, fontWeight = FontWeight.SemiBold)

    Column(modifier) {
        when {
            !apple.sdkAuth -> {
                // No SDK in this build: say so, and keep media-session control working underneath.
                Text("To browse playlists here, add Apple's MusicKit for Android AARs to app/libs and rebuild.", style = small)
                Spacer(Modifier.height(10.dp))
                fallback()
            }
            !apple.hasDeveloperToken -> {
                var token by rememberSaveable { mutableStateOf("") }
                Text("Paste your Apple Music developer token (a JWT from your Apple Developer account).", style = small)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    token, { token = it },
                    singleLine = true,
                    placeholder = { Text("eyJhbGciOiJFUzI1NiIs…", style = small) },
                    textStyle = small.copy(color = ink),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Pill("Save token", enabled = token.count { it == '.' } == 2) { actions.setDeveloperToken(token) }
            }
            !apple.signedIn -> {
                Text("Sign in so Tapeman can read your library playlists. Apple's sign-in opens in the Apple Music app.", style = small)
                Spacer(Modifier.height(10.dp))
                Pill("Sign in to Apple Music", onClick = actions.signIn)
            }
            apple.openPlaylist != null -> {
                val p = apple.openPlaylist
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("‹ Playlists", style = link, modifier = Modifier.clickable(onClick = actions.closePlaylist).padding(vertical = 4.dp))
                    Spacer(Modifier.weight(1f))
                    if (apple.tracks.isNotEmpty()) Pill(if (apple.sdkPlayback) "Play" else "Play in Apple Music") { actions.play(0) }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UrlThumb(AppleMusicApi.artwork(p.artworkUrl, 160), Modifier.size(40.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(p.name, style = title.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                if (apple.loading) Text("Loading songs…", style = small)
                LazyColumn {
                    itemsIndexed(apple.tracks, key = { _, t -> t.id }) { i, t ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = t.catalogId != null || !apple.sdkPlayback) { actions.play(i) }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UrlThumb(AppleMusicApi.artwork(t.artworkUrl, 120), Modifier.size(34.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, style = title.copy(fontSize = 14.sp, color = if (t.catalogId == null) sub else ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" · "), style = small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(formatTime(t.durationMs), style = small)
                        }
                    }
                }
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (apple.loading) "Loading playlists…" else "${apple.playlists.size} playlists", style = small, modifier = Modifier.weight(1f))
                    Text("Refresh", style = link, modifier = Modifier.clickable(onClick = actions.refresh).padding(4.dp))
                    Text("Sign out", style = link.copy(color = sub), modifier = Modifier.clickable(onClick = actions.signOut).padding(4.dp))
                }
                Spacer(Modifier.height(4.dp))
                LazyColumn {
                    items(apple.playlists, key = { it.id }) { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { actions.openPlaylist(p) }
                                .padding(vertical = 5.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UrlThumb(AppleMusicApi.artwork(p.artworkUrl, 160), Modifier.size(38.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(p.name, style = title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("›", style = small.copy(fontSize = 18.sp))
                        }
                    }
                }
            }
        }
        apple.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = small.copy(color = Color(0xFFE0604A)))
        }
    }
}

@Composable
private fun Pill(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text.uppercase(),
        style = TextStyle(color = Color(0xFF1E1916), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) DeckColors.Accent else DeckColors.Accent.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}
