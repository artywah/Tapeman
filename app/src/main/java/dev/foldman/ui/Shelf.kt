package dev.foldman.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldman.deck.DeckUi
import dev.foldman.deck.Source
import dev.foldman.deck.SourceStatus

private val Hand = TextStyle(fontFamily = FontFamily.Cursive, color = DeckColors.Ink, fontSize = 20.sp)
private val Typed = TextStyle(fontFamily = FontFamily.Monospace, color = DeckColors.Ink, fontSize = 12.sp)

class ShelfActions(
    val selectSource: (Source) -> Unit,
    val pickFolder: () -> Unit,
    val playTrack: (Int) -> Unit,
    val openAccess: () -> Unit,
    val openApp: () -> Unit,
    val installApp: (Source) -> Unit,
    val apple: AppleActions,
    val plex: PlexActions,
    val spotify: SpotifyActions,
)

/**
 * The tape shelf: a spine for each source (tap one to load it into the deck) above the J-card of
 * the tape that's in, with its cover and track list.
 */
@Composable
fun TapeShelf(ui: DeckUi, actions: ShelfActions, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Brush.verticalGradient(listOf(Color(0xFF1F2124), Color(0xFF121316))))
            .padding(16.dp),
    ) {
        Text("TAPES · SONY WM-RX707", style = EngravedCaps.copy(color = Color(0xFFB8BEC6), fontSize = 10.sp, letterSpacing = 2.5.sp))
        Spacer(Modifier.height(8.dp))
        Source.entries.forEach { s ->
            TapeSpine(s, inDeck = s == ui.source, onClick = { actions.selectSource(s) })
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        JCard(ui, actions, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun TapeSpine(source: Source, inDeck: Boolean, onClick: () -> Unit) {
    val shell = DeckColors.shell(source)
    val accent = DeckColors.sourceAccent(source)
    Row(
        Modifier
            .fillMaxWidth()
            .offset(x = if (inDeck) 8.dp else 0.dp)
            .shadow(if (inDeck) 10.dp else 3.dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(Brush.verticalGradient(listOf(lerp(shell, Color.White, 0.15f), shell, lerp(shell, Color.Black, 0.25f))))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(2.dp))
                .background(DeckColors.Paper)
                .drawBehind {
                    drawRect(accent, topLeft = Offset(0f, size.height - 3.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx()))
                }
                .padding(horizontal = 10.dp, vertical = 2.dp),
        ) {
            Text(source.label, style = Hand.copy(fontSize = 17.sp), maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (inDeck) "IN DECK" else "LOAD",
            style = EngravedCaps.copy(color = if (inDeck) accent else Color.White.copy(alpha = 0.7f), fontSize = 8.sp),
        )
    }
}

@Composable
private fun JCard(ui: DeckUi, actions: ShelfActions, modifier: Modifier) {
    val accent = DeckColors.sourceAccent(ui.source)
    Column(
        modifier
            .shadow(8.dp, RoundedCornerShape(3.dp))
            .clip(RoundedCornerShape(3.dp))
            .background(DeckColors.Paper)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            CoverArt(ui, Modifier.size(92.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ui.title.ifBlank { "—" }, style = Hand.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (ui.artist.isNotBlank()) Text(ui.artist, style = Hand.copy(fontSize = 17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (ui.album.isNotBlank()) Text(ui.album, style = Typed.copy(fontSize = 11.sp, color = DeckColors.Ink.copy(alpha = 0.6f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text("${formatTime(ui.positionMs)} / ${formatTime(ui.durationMs)}", style = Typed.copy(fontSize = 11.sp, color = DeckColors.Ink.copy(alpha = 0.6f)))
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(accent))
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF2B33D)))
        Spacer(Modifier.height(8.dp))
        when (ui.source) {
            Source.LOCAL -> LocalCard(ui, actions)
            Source.PLEX -> PlexBrowser(ui.plex, actions.plex, light = true)
            Source.SPOTIFY -> SpotifyBrowser(ui.spotify, actions.spotify, fallback = { RemoteCard(ui, actions) }, light = true)
            Source.APPLE_MUSIC -> AppleBrowser(ui.apple, actions.apple, fallback = { RemoteCard(ui, actions) }, light = true)
            Source.YT_MUSIC -> RemoteCard(ui, actions)
        }
    }
}

@Composable
private fun CoverArt(ui: DeckUi, modifier: Modifier) {
    val art = ui.artwork
    Box(modifier.shadow(4.dp).background(ui.label.stripe), contentAlignment = Alignment.Center) {
        if (art != null) {
            Image(art, contentDescription = "Album art", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val step = size.height / 7f
                for (i in 0 until 7) drawRect(if (i % 2 == 0) ui.label.stripe else ui.label.stripe2, Offset(0f, i * step), androidx.compose.ui.geometry.Size(size.width, step))
            }
            Text("A", style = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Black, color = DeckColors.Paper))
        }
    }
}

@Composable
private fun LocalCard(ui: DeckUi, actions: ShelfActions) {
    when (val status = ui.status) {
        SourceStatus.NeedsFolder -> Prompt(
            "Pick a folder of music on this phone — MP3, FLAC, M4A, OGG, WAV. Album art comes from the files or a cover.jpg beside them.",
            "Choose music folder", actions.pickFolder,
        )
        is SourceStatus.Scanning -> Text("Winding on… ${status.found} songs found", style = Hand)
        SourceStatus.EmptyFolder -> Prompt("No music in that folder.", "Choose another folder", actions.pickFolder)
        else -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${ui.folderName ?: "Folder"} · ${ui.tracks.size} songs",
                    style = Typed.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("CHANGE", style = EngravedCaps.copy(color = DeckColors.AccentDeep), modifier = Modifier.clickable(onClick = actions.pickFolder).padding(6.dp))
            }
            Spacer(Modifier.height(6.dp))
            TrackList(ui, actions)
        }
    }
}

@Composable
private fun TrackList(ui: DeckUi, actions: ShelfActions) {
    val list = rememberLazyListState()
    LaunchedEffect(ui.trackIndex) {
        if (ui.trackIndex >= 0) list.animateScrollToItem((ui.trackIndex - 2).coerceAtLeast(0))
    }
    LazyColumn(state = list, modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(ui.tracks, key = { _, t -> t.uri.toString() }) { i, track ->
            val current = i == ui.trackIndex
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { actions.playTrack(i) }
                    .drawBehind {
                        drawLine(DeckColors.PaperLine, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
                        if (current) drawRect(ui.label.stripe.copy(alpha = 0.18f))
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("%02d".format(i + 1), style = Typed.copy(color = DeckColors.Ink.copy(alpha = 0.5f)), modifier = Modifier.width(30.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = Hand.copy(fontSize = 19.sp, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(track.artist, track.folder.takeIf { it != ui.folderName }).joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, style = Typed.copy(fontSize = 10.sp, color = DeckColors.Ink.copy(alpha = 0.55f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (current) Text("◀", style = Typed.copy(color = DeckColors.AccentDeep))
            }
        }
    }
}

@Composable
private fun RemoteCard(ui: DeckUi, actions: ShelfActions) {
    val app = ui.source.label
    when (ui.status) {
        SourceStatus.NeedsAccess -> Column {
            Prompt(
                "Foldman plays $app by driving its media session, which Android only shares with apps that have notification access. Turn on Foldman in the list that opens.",
                "Grant notification access", actions.openAccess,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Installed the APK yourself? Android may grey the switch out: open App info → ⋮ → Allow restricted settings first.",
                style = Typed.copy(fontSize = 10.sp, color = DeckColors.Ink.copy(alpha = 0.6f)),
            )
        }
        SourceStatus.NotInstalled -> Prompt("$app isn't installed on this phone.", "Get $app", { actions.installApp(ui.source) })
        SourceStatus.AppNotRunning -> Prompt(
            "$app isn't playing anything yet. Press PLAY to resume its last queue, or open it and pick something.",
            "Open $app", actions.openApp,
        )
        else -> Column {
            Text("Playing through $app.", style = Hand)
            Text(
                "Keys, REVIEW/CUE and the counter work here; pick songs and playlists below or in $app.",
                style = Typed.copy(fontSize = 11.sp, color = DeckColors.Ink.copy(alpha = 0.65f)),
            )
            Spacer(Modifier.height(8.dp))
            PaperButton("Open $app", actions.openApp)
        }
    }
}

@Composable
private fun Prompt(text: String, button: String, onClick: () -> Unit) {
    Column {
        Text(text, style = Typed.copy(fontSize = 12.sp, lineHeight = 17.sp))
        Spacer(Modifier.height(10.dp))
        PaperButton(button, onClick)
    }
}

@Composable
private fun PaperButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(3.dp))
            .border(1.5.dp, DeckColors.Ink, RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text.uppercase(), style = EngravedCaps.copy(color = DeckColors.Ink, fontSize = 11.sp))
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
