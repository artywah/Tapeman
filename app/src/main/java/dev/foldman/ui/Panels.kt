package dev.foldman.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldman.deck.DeckUi
import dev.foldman.deck.QueueEntry
import dev.foldman.deck.Source
import dev.foldman.deck.SourceStatus
import dev.foldman.playback.Artwork

private val Title = TextStyle(color = Color(0xFFF0F2F5), fontSize = 14.sp, fontWeight = FontWeight.Medium)
private val Sub = TextStyle(color = Color(0xFF9DA2AA), fontSize = 11.5.sp)
private val Header = TextStyle(color = Color(0xFFF0F2F5), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)

/** Smoked charcoal glass panel matching the Sony WM-RX707 aesthetic. */
fun Modifier.glassPanel(corner: Dp = 18.dp): Modifier = drawWithCache {
    val r = CornerRadius(corner.toPx())
    val fill = Brush.verticalGradient(listOf(Color(0xFF232529), Color(0xFF16171A)))
    onDrawBehind {
        drawRoundRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(0f, 3f), cornerRadius = r)
        drawRoundRect(fill, cornerRadius = r)
        drawRoundRect(Color.White.copy(alpha = 0.09f), cornerRadius = r, style = Stroke(1.5f))
    }
}

/**
 * The Unfolded Music Selection & Now Playing Hub (occupies the left half of the unfolded display).
 * Features:
 * - Compact top horizontal source switcher pills (Local, Plex, Spotify, Apple, YT Music)
 * - Navigation tabs between "Library" browser and "Queue"
 * - Full-height browser / queue display
 * - Retro Now Playing liner notes & progress card at the bottom
 */
@Composable
fun UnfoldedMusicHub(
    ui: DeckUi,
    actions: ShelfActions,
    onPlayQueue: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeTab by rememberSaveable { mutableStateOf(0) } // 0 = Library, 1 = Queue
    val accent = DeckColors.sourceAccent(ui.source)

    Column(
        modifier
            .glassPanel()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. Top Source Capsules Row
        LazyRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(Source.entries) { s ->
                val selected = s == ui.source
                val srcAccent = DeckColors.sourceAccent(s)
                Row(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) srcAccent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
                        .border(
                            1.dp,
                            if (selected) srcAccent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(16.dp),
                        )
                        .clickable { actions.selectSource(s) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SourceTile(s, Modifier.size(16.dp))
                    Text(
                        s.label,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                        ),
                    )
                }
            }
        }

        // 2. Library vs Queue Sub-header / Tabs
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Library",
                    style = Header.copy(
                        color = if (activeTab == 0) Color.White else Color.White.copy(alpha = 0.4f),
                        fontSize = 17.sp,
                    ),
                    modifier = Modifier.clickable { activeTab = 0 }.padding(vertical = 2.dp),
                )
                Text(
                    if (ui.queue.isNotEmpty()) "Queue (${ui.queue.size})" else "Queue",
                    style = Header.copy(
                        color = if (activeTab == 1) Color.White else Color.White.copy(alpha = 0.4f),
                        fontSize = 17.sp,
                    ),
                    modifier = Modifier.clickable { activeTab = 1 }.padding(vertical = 2.dp),
                )
            }

            // Quick source status & version
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    statusLine(ui),
                    style = Sub.copy(fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "v0.2.1",
                    style = Sub.copy(fontSize = 10.sp, color = Color.White.copy(alpha = 0.35f)),
                )
            }
        }

        // 3. Main Center Content (Library Browser or Queue List)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (activeTab == 0) {
                when (ui.source) {
                    Source.LOCAL -> Albums(ui, actions)
                    Source.PLEX -> PlexBrowser(ui.plex, actions.plex)
                    Source.SPOTIFY -> SpotifyBrowser(
                        ui.spotify,
                        actions.spotify,
                        fallback = { RemoteState(ui, actions, showHeader = false) },
                    )
                    Source.APPLE_MUSIC -> AppleBrowser(
                        ui.apple,
                        actions.apple,
                        fallback = { RemoteState(ui, actions, showHeader = false) },
                    )
                    Source.YT_MUSIC -> RemoteState(ui, actions)
                }
            } else {
                QueueList(ui, onPlayQueue)
            }
        }

        // 4. Bottom Now Playing & Liner Notes Card
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF141619))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Album art square
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(ui.label.stripe, ui.label.stripe2))),
                contentAlignment = Alignment.Center,
            ) {
                ui.artwork?.let {
                    Image(it, "Album art", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } ?: Text("♪", style = TextStyle(fontSize = 26.sp, color = Color.White.copy(alpha = 0.75f)))
            }

            // Title, artist, duration and seek progress
            Column(Modifier.weight(1f)) {
                Text(
                    ui.title.ifBlank { "No tape playing" },
                    style = Title.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val byLine = listOf(ui.artist, ui.album).filter { it.isNotBlank() }.joinToString(" · ")
                if (byLine.isNotBlank()) {
                    Text(
                        byLine,
                        style = Sub.copy(fontSize = 11.5.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(5.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${formatTime(ui.positionMs)} / ${formatTime(ui.durationMs)}",
                        style = Sub.copy(fontSize = 10.sp),
                    )
                    Text(
                        buildString {
                            append(ui.source.label)
                            if (ui.dbbEnabled) append(" · DBB")
                            if (ui.avlsEnabled) append(" · AVLS")
                        },
                        style = Sub.copy(fontSize = 9.5.sp, color = accent),
                    )
                }
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(ui.progress)
                            .fillMaxHeight()
                            .background(accent),
                    )
                }
            }
        }
    }
}

/** Library band: sources, then albums/playlists/server browser, then the active queue. */
@Composable
fun LibraryBand(ui: DeckUi, actions: ShelfActions, onPlayQueue: (Long) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.glassPanel().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(0.28f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            Text("Sources", style = Header)
            Spacer(Modifier.height(6.dp))
            Source.entries.forEach { s ->
                val selected = s == ui.source
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable { actions.selectSource(s) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SourceTile(s, Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.label, style = Title, maxLines = 1)
                        Text(if (selected) statusLine(ui) else "Tap to load", style = Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Column(Modifier.weight(0.40f).fillMaxHeight()) {
            when (ui.source) {
                Source.LOCAL -> Albums(ui, actions)
                Source.PLEX -> {
                    Text(
                        ui.plex.openAlbum?.title ?: "Plex · ${ui.plex.libraryName}",
                        style = Header,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    PlexBrowser(ui.plex, actions.plex)
                }
                Source.SPOTIFY -> {
                    Text(
                        ui.spotify.openCollection?.name ?: "Spotify",
                        style = Header,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    SpotifyBrowser(ui.spotify, actions.spotify, fallback = { RemoteState(ui, actions, showHeader = false) })
                }
                Source.APPLE_MUSIC -> {
                    Text(ui.apple.openPlaylist?.let { "Playlist" } ?: "Playlists", style = Header)
                    Spacer(Modifier.height(8.dp))
                    AppleBrowser(ui.apple, actions.apple, fallback = { RemoteState(ui, actions, showHeader = false) })
                }
                Source.YT_MUSIC -> RemoteState(ui, actions)
            }
        }
        Column(Modifier.weight(0.32f).fillMaxHeight()) {
            Text("Queue", style = Header)
            Spacer(Modifier.height(8.dp))
            QueueList(ui, onPlayQueue)
        }
    }
}

private fun statusLine(ui: DeckUi): String = when (val s = ui.status) {
    SourceStatus.Ready -> when (ui.source) {
        Source.LOCAL -> "${ui.tracks.size} songs"
        Source.PLEX -> ui.plex.loadedAlbumTitle ?: "${ui.tracks.size} songs"
        else -> "Connected"
    }
    SourceStatus.NeedsFolder -> "Choose a folder"
    SourceStatus.NeedsPlexAlbum -> if (ui.plex.loading) "Connecting…" else "Pick an album"
    is SourceStatus.Scanning -> "Scanning… ${s.found}"
    SourceStatus.EmptyFolder -> "No music found"
    SourceStatus.NeedsAccess -> "Needs notification access"
    SourceStatus.AppNotRunning -> "Not playing"
    SourceStatus.NotInstalled -> "Not installed"
    SourceStatus.NeedsDeveloperToken -> "Needs developer token"
    SourceStatus.NeedsAppleSignIn -> "Sign in to browse"
}

@Composable
private fun SourceTile(source: Source, modifier: Modifier) {
    val accent = DeckColors.sourceAccent(source)
    val shell = DeckColors.shell(source)
    Box(
        modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Brush.linearGradient(listOf(androidx.compose.ui.graphics.lerp(shell, accent, 0.35f), shell))),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth(0.66f).height(9.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF0E1012)))
    }
}

@Composable
private fun Albums(ui: DeckUi, actions: ShelfActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Albums", style = Header, modifier = Modifier.weight(1f))
        Text(
            if (ui.status == SourceStatus.NeedsFolder) "Choose folder" else "Change folder",
            style = Sub.copy(color = DeckColors.Accent, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.clickable(onClick = actions.pickFolder).padding(4.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
    when (val s = ui.status) {
        SourceStatus.NeedsFolder -> Text("Pick a folder of music on this phone. Covers come from the files or a cover.jpg beside them.", style = Sub)
        is SourceStatus.Scanning -> Text("Scanning… ${s.found} songs", style = Sub)
        SourceStatus.EmptyFolder -> Text("No music in that folder.", style = Sub)
        else -> {
            val albums = remember(ui.tracks) {
                ui.tracks.withIndex().groupBy { it.value.folder }.map { (folder, items) ->
                    Triple(folder, items.first(), items.size)
                }
            }
            LazyColumn {
                items(albums, key = { it.first }) { (folder, first, count) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { actions.playTrack(first.index) }
                            .padding(vertical = 5.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Thumb(null, first.value.coverUri, Modifier.size(38.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(folder, style = Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${first.value.artist ?: "Album"} · $count songs", style = Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteState(ui: DeckUi, actions: ShelfActions, showHeader: Boolean = true) {
    val app = ui.source.label
    val accent = DeckColors.sourceAccent(ui.source)
    if (showHeader) {
        Text(app, style = Header)
        Spacer(Modifier.height(8.dp))
    }
    val (text, button, action) = when (ui.status) {
        SourceStatus.NeedsAccess -> Triple(
            "Tapeman plays $app through its media session, which Android only shares with apps that have notification access. If the switch is greyed out: App info → ⋮ → Allow restricted settings.",
            "Grant access", actions.openAccess,
        )
        SourceStatus.NotInstalled -> Triple("$app isn't installed.", "Get $app", { actions.installApp(ui.source) })
        SourceStatus.AppNotRunning -> Triple("Press PLAY to resume your last $app queue, or open it and pick something.", "Open $app", actions.openApp)
        else -> Triple("Choose playlists and albums in $app; the deck follows along with art, queue and transport.", "Open $app", actions.openApp)
    }
    Text(text, style = Sub.copy(lineHeight = 16.sp))
    Spacer(Modifier.height(10.dp))
    Text(
        button.uppercase(),
        style = Sub.copy(color = Color(0xFF101214), fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent)
            .clickable(onClick = action)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun QueueList(ui: DeckUi, onPlay: (Long) -> Unit) {
    val accent = DeckColors.sourceAccent(ui.source)
    if (ui.queue.isEmpty()) {
        val empty = when {
            ui.source == Source.LOCAL -> "Nothing loaded"
            ui.source == Source.PLEX -> "Pick a Plex album or playlist to load the tape"
            ui.source == Source.APPLE_MUSIC && ui.apple.sdkPlayback -> "Pick a playlist to load the queue"
            else -> "This app doesn't share its queue"
        }
        Text(empty, style = Sub)
        return
    }
    val list = rememberLazyListState()
    val currentIndex = ui.queue.indexOfFirst { it.id == ui.currentQueueId }
    LaunchedEffect(currentIndex) { if (currentIndex >= 0) list.animateScrollToItem(currentIndex) }
    LazyColumn(state = list) {
        itemsIndexed(ui.queue, key = { i, q -> "${i}_${q.id}" }) { _, q ->
            QueueRow(q, current = q.id == ui.currentQueueId, accent = accent, onClick = { onPlay(q.id) })
        }
    }
}

@Composable
private fun QueueRow(
    q: QueueEntry,
    current: Boolean,
    accent: Color = DeckColors.Accent,
    onClick: () -> Unit,
    showArt: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .drawWithCache {
                onDrawBehind {
                    drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, size.height), Offset(size.width, size.height), 1f)
                }
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArt) {
            Thumb(q.artwork, q.artworkUri, Modifier.size(44.dp))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(q.title, style = Title.copy(color = if (current) accent else Title.color), maxLines = 1, overflow = TextOverflow.Ellipsis)
            q.subtitle?.let { Text(it, style = Sub, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (current) Text("▶", style = Sub.copy(color = accent))
    }
}

@Composable
fun UrlThumb(url: String?, modifier: Modifier) = Thumb(null, url?.let(Uri::parse), modifier)

/** Cover thumbnail from a bitmap the source handed us, or loaded (and cached) from a URI. */
@Composable
private fun Thumb(bitmap: Bitmap?, uri: Uri?, modifier: Modifier) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(bitmap?.asImageBitmap(), bitmap, uri) {
        value = bitmap?.asImageBitmap() ?: uri?.let { Artwork.thumbnail(context, it)?.asImageBitmap() }
    }
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF2C2F35)), contentAlignment = Alignment.Center) {
        image?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            ?: Text("♪", style = Sub.copy(fontSize = 16.sp))
    }
}

/** Now-playing band: the cover big, what's up next, and liner notes. */
@Composable
fun NowPlayingBand(ui: DeckUi, onPlayQueue: (Long) -> Unit, modifier: Modifier = Modifier) {
    val accent = DeckColors.sourceAccent(ui.source)
    Row(modifier.glassPanel().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(ui.label.stripe, ui.label.stripe2))),
            contentAlignment = Alignment.Center,
        ) {
            ui.artwork?.let { Image(it, "Album art", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                ?: Text("A", style = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.85f)))
        }
        Column(Modifier.weight(0.5f).fillMaxHeight()) {
            Text("Up next", style = Header)
            Spacer(Modifier.height(6.dp))
            val i = ui.queue.indexOfFirst { it.id == ui.currentQueueId }
            val next = if (i >= 0) ui.queue.drop(i + 1).take(4) else emptyList()
            if (next.isEmpty()) Text("End of side A", style = Sub)
            next.forEach { q -> QueueRow(q, current = false, accent = accent, onClick = { onPlayQueue(q.id) }, showArt = true) }
        }
        Column(Modifier.weight(0.5f).fillMaxHeight()) {
            Text("Liner notes", style = Header)
            Spacer(Modifier.height(8.dp))
            Text(ui.title.ifBlank { "No tape playing" }, style = Title.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (ui.artist.isNotBlank()) Text(ui.artist, style = Sub.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (ui.album.isNotBlank()) Text(ui.album, style = Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            val details = buildList {
                add(buildString {
                    append(ui.source.label)
                    if (ui.dbbEnabled) append(" · EX DBB")
                    if (ui.avlsEnabled) append(" · AVLS")
                })
                if (ui.trackCount > 0) add("Track ${ui.trackIndex + 1} of ${ui.trackCount}")
                add("${formatTime(ui.positionMs)} / ${formatTime(ui.durationMs)}")
            }
            details.forEach { Text(it, style = Sub) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.1f))) {
                Box(Modifier.fillMaxWidth(ui.progress).fillMaxHeight().background(accent))
            }
        }
    }
}
