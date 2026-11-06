package dev.foldman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldman.deck.PlexUi
import dev.foldman.plex.PlexAlbum
import dev.foldman.plex.PlexArtist
import dev.foldman.plex.PlexClient
import dev.foldman.plex.PlexSectionTab

class PlexActions(
    val setTab: (PlexSectionTab) -> Unit,
    val setSearch: (String) -> Unit,
    val setLetter: (String?) -> Unit,
    val refresh: () -> Unit,
    val loadMore: () -> Unit,
    val openArtist: (PlexArtist) -> Unit,
    val closeArtist: () -> Unit,
    val openAlbum: (PlexAlbum) -> Unit,
    val closeAlbum: () -> Unit,
    val playAlbum: (PlexAlbum?, Int) -> Unit,
    val playRadio: (ratingKey: String, title: String) -> Unit = { _, _ -> },
    val toggleFavorite: (dev.foldman.deck.LocalTrack) -> Unit = {},
    val toggleSonicDj: () -> Unit = {},
    val toggleSettings: (Boolean) -> Unit,
    val saveSettings: (String, String, String) -> Unit,
)

private val Letters = listOf("#") + ('A'..'Z').map { it.toString() }

/**
 * Plex Server browser: switches between Albums, Recent, Popular, and Playlists, with search,
 * A–Z jump bar, track list view, and an inline server settings editor.
 */
@Composable
fun PlexBrowser(
    plex: PlexUi,
    actions: PlexActions,
    modifier: Modifier = Modifier,
    light: Boolean = false,
) {
    val ink = if (light) DeckColors.Ink else Color(0xFFF4EFE9)
    val sub = if (light) DeckColors.Ink.copy(alpha = 0.62f) else Color(0xFFA79C92)
    val accent = Color(0xFFE5A00D) // Plex amber gold
    val titleStyle = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val small = TextStyle(color = sub, fontSize = 11.sp, lineHeight = 15.sp)
    val link = small.copy(color = accent, fontWeight = FontWeight.Bold)

    var searchFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = plex.searchQuery,
                selection = TextRange(plex.searchQuery.length)
            )
        )
    }

    LaunchedEffect(plex.searchQuery) {
        if (plex.searchQuery != searchFieldValue.text) {
            searchFieldValue = TextFieldValue(
                text = plex.searchQuery,
                selection = TextRange(plex.searchQuery.length)
            )
        }
    }

    Column(modifier) {
        if (plex.editingSettings || plex.token.isBlank()) {
            var url by rememberSaveable(plex.serverUrl) { mutableStateOf(plex.serverUrl) }
            var token by rememberSaveable(plex.token) { mutableStateOf(plex.token) }
            var lib by rememberSaveable(plex.libraryName) { mutableStateOf(plex.libraryName) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Plex Server Settings", style = titleStyle.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                if (plex.token.isNotBlank()) {
                    Text("Cancel", style = link.copy(color = sub), modifier = Modifier.clickable { actions.toggleSettings(false) }.padding(4.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                label = { Text("Server URL", style = small) },
                textStyle = small.copy(color = ink),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                singleLine = true,
                label = { Text("X-Plex-Token", style = small) },
                textStyle = small.copy(color = ink),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = lib,
                onValueChange = { lib = it },
                singleLine = true,
                label = { Text("Music Library Name", style = small) },
                textStyle = small.copy(color = ink),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            PlexPill("Save & Connect", accent, enabled = url.isNotBlank() && token.isNotBlank()) {
                actions.saveSettings(url, token, lib)
            }
            return@Column
        }

        if (plex.openAlbum != null) {
            val album = plex.openAlbum
            Row(verticalAlignment = Alignment.CenterVertically) {
                val backLabel = if (plex.openArtist != null) plex.openArtist.name else plex.tab.label
                Text("‹ $backLabel", style = link, modifier = Modifier.clickable(onClick = actions.closeAlbum).padding(vertical = 4.dp))
                Spacer(Modifier.weight(1f))
                if (plex.tracks.isNotEmpty()) {
                    PlexPill("Load Tape", accent) { actions.playAlbum(album, 0) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                UrlThumb(album.coverUrl, Modifier.size(42.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(album.title, style = titleStyle.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(album.artist, style = small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(6.dp))
            if (plex.loading) Text("Loading tracks from Plex…", style = small)
            LazyColumn {
                itemsIndexed(plex.tracks, key = { i, t -> "${i}_${t.uri}" }) { i, t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { actions.playAlbum(album, i) }
                            .padding(vertical = 5.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("%02d".format(i + 1), style = small.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = titleStyle.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            t.artist?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        t.ratingKey?.let {
                            Text(
                                if (t.isFavorite) "★" else "☆",
                                style = small.copy(color = if (t.isFavorite) Color(0xFFFFB800) else ink.copy(alpha = 0.5f), fontSize = 16.sp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { actions.toggleFavorite(t) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("▶", style = small.copy(color = accent))
                    }
                }
            }
        } else if (plex.openArtist != null) {
            val artist = plex.openArtist
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹ Artists", style = link, modifier = Modifier.clickable(onClick = actions.closeArtist).padding(vertical = 4.dp))
                Spacer(Modifier.weight(1f))
                PlexPill("Artist Radio 📻", accent) {
                    actions.playRadio(artist.id, "${artist.name} Radio")
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                UrlThumb(artist.thumbUrl, Modifier.size(42.dp).clip(RoundedCornerShape(50)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(artist.name, style = titleStyle.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (plex.artistAlbums.isNotEmpty()) {
                        Text("${plex.artistAlbums.size} albums", style = small)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (plex.loading) {
                Text("Loading albums by ${artist.name}…", style = small)
            } else if (plex.artistAlbums.isEmpty() && plex.message == null) {
                Text("No albums found.", style = small)
            }
            LazyColumn {
                items(plex.artistAlbums, key = { it.id }) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { actions.openAlbum(item) }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UrlThumb(item.coverUrl, Modifier.size(38.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val subText = buildList {
                                if (item.year > 0) add(item.year.toString())
                                if (item.trackCount > 0) add("${item.trackCount} tracks")
                            }.joinToString(" · ")
                            if (subText.isNotEmpty()) {
                                Text(subText, style = small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Text(
                            "▶",
                            style = link.copy(fontSize = 13.sp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { actions.playAlbum(item, 0) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        } else {
            // Section tabs + server config button
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlexSectionTab.entries.forEach { t ->
                    val selected = t == plex.tab
                    Text(
                        t.label.uppercase(),
                        style = small.copy(
                            color = if (selected) Color(0xFF1A1510) else ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) accent else ink.copy(alpha = 0.1f))
                            .clickable { actions.setTab(t) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text("↻", style = link.copy(fontSize = 14.sp), modifier = Modifier.clickable(onClick = actions.refresh).padding(horizontal = 4.dp))
                Text("⚙", style = link.copy(color = sub, fontSize = 13.sp), modifier = Modifier.clickable { actions.toggleSettings(true) }.padding(horizontal = 4.dp))
            }
            Spacer(Modifier.height(6.dp))

            // Compact Search + A-Z bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ink.copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⌕", style = small.copy(fontSize = 12.sp))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) {
                    if (searchFieldValue.text.isEmpty()) {
                        val placeholder = when (plex.tab) {
                            PlexSectionTab.ARTISTS -> "Search artists…"
                            PlexSectionTab.PLAYLISTS -> "Search playlists…"
                            PlexSectionTab.FAVORITES -> "Search favorites…"
                            else -> "Search ${plex.libraryName}…"
                        }
                        Text(placeholder, style = small)
                    }
                    BasicTextField(
                        value = searchFieldValue,
                        onValueChange = { newValue ->
                            searchFieldValue = newValue
                            if (newValue.text != plex.searchQuery) {
                                actions.setSearch(newValue.text)
                            }
                        },
                        singleLine = true,
                        textStyle = small.copy(color = ink),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (searchFieldValue.text.isNotEmpty()) {
                    Text(
                        "✕",
                        style = small.copy(color = accent),
                        modifier = Modifier
                            .clickable {
                                searchFieldValue = TextFieldValue("")
                                actions.setSearch("")
                            }
                            .padding(horizontal = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            if (plex.tab != PlexSectionTab.HOME) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Letters.forEach { l ->
                        val sel = plex.selectedLetter == l
                        Text(
                            l,
                            style = small.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sel) Color(0xFF1A1510) else sub,
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (sel) accent else Color.Transparent)
                                .clickable { actions.setLetter(l) }
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            if (plex.loading && plex.items.isEmpty() && plex.artists.isEmpty() && plex.homeData == null) {
                Text("Connecting to ${plex.serverUrl}…", style = small)
            } else if (plex.tab == PlexSectionTab.HOME) {
                PlexHomeView(
                    plex = plex,
                    actions = actions,
                    accent = accent,
                    ink = ink,
                    sub = sub,
                    titleStyle = titleStyle,
                    small = small,
                    link = link,
                )
            } else if (plex.tab == PlexSectionTab.ARTISTS) {
                if (plex.artists.isEmpty() && plex.message == null) {
                    Text("No artists found.", style = small)
                }

                LazyColumn {
                    items(plex.artists, key = { it.id }) { artist ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { actions.openArtist(artist) }
                                .padding(vertical = 5.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UrlThumb(artist.thumbUrl, Modifier.size(38.dp).clip(RoundedCornerShape(50)))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(artist.name, style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                artist.summary?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Text("›", style = link.copy(fontSize = 16.sp, color = sub), modifier = Modifier.padding(horizontal = 8.dp))
                        }
                    }
                    if (plex.canLoadMore && plex.artists.isNotEmpty()) {
                        item(key = "load_more_artists") {
                            LaunchedEffect(Unit) {
                                actions.loadMore()
                            }
                            if (plex.loadingMore) {
                                Row(
                                    Modifier.fillMaxWidth().padding(8.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text("Loading more…", style = small)
                                }
                            }
                        }
                    }
                }
            } else {
                if (plex.items.isEmpty() && plex.message == null) {
                    Text("No items found.", style = small)
                }

                LazyColumn {
                    items(plex.items, key = { it.id }) { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { actions.openAlbum(item) }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UrlThumb(item.coverUrl, Modifier.size(38.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val subText = buildList {
                                    add(item.artist)
                                    if (item.year > 0) add(item.year.toString())
                                }.joinToString(" · ")
                                Text(subText, style = small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(
                                "▶",
                                style = link.copy(fontSize = 13.sp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { actions.playAlbum(item, 0) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    if (plex.canLoadMore && plex.items.isNotEmpty()) {
                        item(key = "load_more_items") {
                            LaunchedEffect(Unit) {
                                actions.loadMore()
                            }
                            if (plex.loadingMore) {
                                Row(
                                    Modifier.fillMaxWidth().padding(8.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text("Loading more…", style = small)
                                }
                            }
                        }
                    }
                }
            }
        }

        plex.message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = small.copy(color = Color(0xFFE0604A)))
        }
    }
}

@Composable
private fun PlexPill(text: String, accent: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text.uppercase(),
        style = TextStyle(color = Color(0xFF1A1510), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) accent else accent.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun PlexHomeView(
    plex: PlexUi,
    actions: PlexActions,
    accent: Color,
    ink: Color,
    sub: Color,
    titleStyle: TextStyle,
    small: TextStyle,
    link: TextStyle,
) {
    val home = plex.homeData
    if (home == null && plex.loading) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
            Text("Digging into your Plex collection…", style = small)
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        // 1. Hero DJ Booth (Quick Actions)
        item(key = "home_dj_booth") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Sonic DJ Card
                val isDjActive = plex.isSonicDjActive
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDjActive) Color(0xFF003840) else ink.copy(alpha = 0.08f))
                        .border(1.dp, if (isDjActive) Color(0xFF00E5FF) else ink.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .clickable(onClick = actions.toggleSonicDj)
                        .padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎧", style = TextStyle(fontSize = 14.sp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isDjActive) "SONIC DJ: ON" else "SONIC DJ: OFF",
                            style = small.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isDjActive) Color(0xFF00E5FF) else ink,
                                fontSize = 10.sp,
                            ),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isDjActive) "Continuous Flow Active" else "Acoustic DJ · Tap to Start",
                        style = small.copy(fontSize = 9.sp, color = if (isDjActive) Color(0xFF80F2FF) else sub),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Loved Tracks Card
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF241E15))
                        .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .clickable {
                            actions.openAlbum(
                                PlexAlbum(
                                    id = PlexClient.SPECIAL_FAVORITES_ID,
                                    title = "★ Loved Tracks",
                                    artist = "Plex Favorites",
                                    isPlaylist = true,
                                )
                            )
                        }
                        .padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("★", style = TextStyle(fontSize = 14.sp, color = accent))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "LOVED TRACKS",
                            style = small.copy(
                                fontWeight = FontWeight.Bold,
                                color = accent,
                                fontSize = 10.sp,
                            ),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Your Favorited Songs",
                        style = small.copy(fontSize = 9.sp, color = sub),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // 2. Jump Back In (Recently Played)
        if (home?.recentlyPlayed?.isNotEmpty() == true) {
            item(key = "home_recents") {
                Column {
                    HomeSectionHeader(title = "JUMP BACK IN", icon = "⚡", accent = accent, ink = ink)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) {
                        items(home.recentlyPlayed, key = { "recent_${it.id}" }) { album ->
                            HomeAlbumCard(
                                album = album,
                                onClick = { actions.openAlbum(album) },
                                onPlay = { actions.playAlbum(album, 0) },
                                ink = ink,
                                sub = sub,
                                accent = accent,
                            )
                        }
                    }
                }
            }
        }

        // 3. Similar to [Recent Artist]
        if (home?.recentArtist != null && home.similarArtists.isNotEmpty()) {
            item(key = "home_similar_artists") {
                Column {
                    HomeSectionHeader(
                        title = "FANS ALSO LIKE (${home.recentArtist.name.uppercase()})",
                        icon = "✨",
                        accent = accent,
                        ink = ink,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) {
                        items(home.similarArtists, key = { "sim_${it.id}" }) { artist ->
                            HomeArtistAvatarCard(
                                artist = artist,
                                onOpen = { actions.openArtist(artist) },
                                onRadio = { actions.playRadio(artist.id, artist.name) },
                                ink = ink,
                                sub = sub,
                                accent = accent,
                            )
                        }
                    }
                }
            }
        }

        // 4. Dynamic Hubs
        home?.hubs?.forEach { hub ->
            item(key = "hub_${hub.hubIdentifier}") {
                Column {
                    HomeSectionHeader(title = hub.title.uppercase(), icon = "💿", accent = accent, ink = ink)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) {
                        items(hub.items, key = { "hub_${hub.hubIdentifier}_${it.id}" }) { album ->
                            HomeAlbumCard(
                                album = album,
                                onClick = { actions.openAlbum(album) },
                                onPlay = { actions.playAlbum(album, 0) },
                                ink = ink,
                                sub = sub,
                                accent = accent,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(title: String, icon: String, accent: Color, ink: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, style = TextStyle(fontSize = 11.sp, color = accent))
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            style = TextStyle(
                color = ink,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            ),
        )
    }
}

@Composable
private fun HomeAlbumCard(
    album: PlexAlbum,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    ink: Color,
    sub: Color,
    accent: Color,
) {
    Column(
        Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
    ) {
        Box(Modifier.size(100.dp)) {
            UrlThumb(album.coverUrl, Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)))
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC000000))
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Text("▶", style = TextStyle(color = accent, fontSize = 10.sp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            album.title,
            style = TextStyle(color = ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            album.artist,
            style = TextStyle(color = sub, fontSize = 10.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeArtistAvatarCard(
    artist: PlexArtist,
    onOpen: () -> Unit,
    onRadio: () -> Unit,
    ink: Color,
    sub: Color,
    accent: Color,
) {
    Column(
        Modifier
            .width(86.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UrlThumb(
            artist.thumbUrl,
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .border(1.dp, accent.copy(alpha = 0.4f), CircleShape),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            artist.name,
            style = TextStyle(color = ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.15f))
                .clickable(onClick = onRadio)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📻 Radio", style = TextStyle(color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold))
        }
    }
}
