package dev.foldman.plex

import android.net.Uri
import dev.foldman.deck.LocalTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class PlexSectionTab(val label: String) {
    HOME("Home"),
    ARTISTS("Artists"),
    ALL("Albums"),
    RECENT("Recent"),
    POPULAR("Popular"),
    PLAYLISTS("Playlists"),
    FAVORITES("Favorites"),
}

data class PlexArtist(
    val id: String,
    val name: String,
    val thumbUrl: String? = null,
    val summary: String? = null,
)

data class PlexAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int = 0,
    val coverUrl: String? = null,
    val isPlaylist: Boolean = false,
    val trackCount: Int = 0,
)

data class PlexHub(
    val title: String,
    val hubIdentifier: String,
    val context: String,
    val type: String,
    val items: List<PlexAlbum>,
)

data class PlexHomeData(
    val recentlyPlayed: List<PlexAlbum> = emptyList(),
    val hubs: List<PlexHub> = emptyList(),
    val similarArtists: List<PlexArtist> = emptyList(),
    val recentArtist: PlexArtist? = null,
)

data class PlexLetterSummary(
    val key: String,
    val title: String,
    val size: Int = 0,
)

class PlexClient(
    var serverUrl: String = DEFAULT_URL,
    var plexToken: String = DEFAULT_TOKEN,
    var libraryName: String = DEFAULT_LIBRARY,
) {
    private var cachedSectionId: String? = null

    fun updateCredentials(url: String, token: String, library: String) {
        serverUrl = url.trim().ifBlank { DEFAULT_URL }
        plexToken = token.trim()
        libraryName = library.trim().ifBlank { DEFAULT_LIBRARY }
        cachedSectionId = null
    }

    fun baseUrl(): String {
        var u = serverUrl.trim()
        if (u.isBlank()) return ""
        if (!u.startsWith("http://", ignoreCase = true) && !u.startsWith("https://", ignoreCase = true)) {
            u = if (u.contains(":")) "http://$u" else "http://$u:32400"
        }
        return u.trimEnd('/')
    }

    fun authedUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val clean = if (path.startsWith("/")) path else "/$path"
        val sep = if (clean.contains("?")) "&" else "?"
        return "${baseUrl()}$clean${sep}X-Plex-Token=${URLEncoder.encode(plexToken, "UTF-8")}"
    }

    private fun getJson(path: String): JSONObject {
        val fullUrl = if (path.startsWith("http")) path else "${baseUrl()}$path"
        val conn = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 20000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Plex-Token", plexToken)
            setRequestProperty("X-Plex-Client-Identifier", "foldman-walkman-rx707")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Plex HTTP $code")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun findMusicSectionId(): String = withContext(Dispatchers.IO) {
        cachedSectionId?.let { return@withContext it }
        val json = getJson("/library/sections")
        val dirs = json.optJSONObject("MediaContainer")?.optJSONArray("Directory")
            ?: throw IllegalStateException("No libraries found on Plex server.")
        var fallback: String? = null
        for (i in 0 until dirs.length()) {
            val d = dirs.getJSONObject(i)
            if (d.optString("type") == "artist") {
                val key = d.getString("key")
                val title = d.optString("title")
                if (title.equals(libraryName, ignoreCase = true)) {
                    cachedSectionId = key
                    return@withContext key
                }
                if (fallback == null) fallback = key
            }
        }
        val chosen = fallback ?: throw IllegalStateException("Music library '$libraryName' not found.")
        cachedSectionId = chosen
        chosen
    }

    suspend fun fetchItems(
        tab: PlexSectionTab,
        searchQuery: String = "",
        letter: String? = null,
        start: Int = 0,
        limit: Int = 200,
    ): List<PlexAlbum> = withContext(Dispatchers.IO) {
        if (plexToken.isBlank()) throw IllegalStateException("Plex token is required.")
        if (tab == PlexSectionTab.HOME) {
            return@withContext fetchHomeData().recentlyPlayed
        }
        if (tab == PlexSectionTab.PLAYLISTS) {
            return@withContext fetchPlaylists(searchQuery, letter)
        }
        if (tab == PlexSectionTab.FAVORITES) {
            val list = mutableListOf<PlexAlbum>()
            if (searchQuery.isBlank() && (letter == null || letter == "★" || letter.equals("F", ignoreCase = true))) {
                list += PlexAlbum(
                    id = SPECIAL_FAVORITES_ID,
                    title = "★ Loved Tracks",
                    artist = "All your 5-star & loved tracks",
                    coverUrl = null,
                    isPlaylist = true,
                    trackCount = 0,
                )
            }
            val sectionId = findMusicSectionId()
            val favAlbumsJson = runCatching {
                getJson("/library/sections/$sectionId/all?type=9&userRating>=9&sort=titleSort:asc&X-Plex-Container-Start=$start&X-Plex-Container-Size=$limit")
            }.getOrNull()
            val meta = favAlbumsJson?.optJSONObject("MediaContainer")?.optJSONArray("Metadata")
            if (meta != null) {
                for (i in 0 until meta.length()) {
                    val item = meta.getJSONObject(i)
                    if (item.has("deletedAt")) continue
                    val key = item.optString("ratingKey")
                    val title = item.optString("title").trim()
                    val artist = item.optString("parentTitle").trim()
                    if (key.isBlank() || (title.isBlank() && artist.isBlank())) continue
                    val thumb = item.optString("thumb").ifBlank { item.optString("parentThumb") }
                    list += PlexAlbum(
                        id = key,
                        title = title.ifBlank { "Untitled Album" },
                        artist = artist.ifBlank { "Unknown Artist" },
                        year = item.optInt("year", 0),
                        coverUrl = authedUrl(thumb),
                        isPlaylist = false,
                        trackCount = item.optInt("leafCount", 0),
                    )
                }
            }
            return@withContext list
        }
        val sectionId = findMusicSectionId()
        val path = buildString {
            if (letter != null && searchQuery.isBlank()) {
                val enc = if (letter == "#") "%23" else URLEncoder.encode(letter, StandardCharsets.UTF_8.name())
                append("/library/sections/$sectionId/firstCharacter/$enc?type=9&sort=titleSort:asc")
            } else {
                append("/library/sections/$sectionId/all?type=9")
                val sort = when (tab) {
                    PlexSectionTab.RECENT -> "addedAt:desc"
                    PlexSectionTab.POPULAR -> "viewCount:desc,lastViewedAt:desc"
                    else -> "artist.titleSort:asc,titleSort:asc"
                }
                append("&sort=$sort")
                if (searchQuery.isNotBlank()) {
                    val q = URLEncoder.encode(searchQuery.trim(), StandardCharsets.UTF_8.name())
                    append("&title=$q")
                }
            }
            append("&X-Plex-Container-Start=$start&X-Plex-Container-Size=$limit")
        }
        val container = getJson(path).optJSONObject("MediaContainer") ?: return@withContext emptyList()
        val meta = container.optJSONArray("Metadata") ?: return@withContext emptyList()
        parseAlbumsFromMetadata(meta)
    }

    private fun parseAlbumsFromMetadata(meta: org.json.JSONArray): List<PlexAlbum> = buildList {
        for (i in 0 until meta.length()) {
            val item = meta.getJSONObject(i)
            if (item.has("deletedAt")) continue
            val key = item.optString("ratingKey")
            val type = item.optString("type")
            val isArtist = type == "artist"
            val title = item.optString("title").trim()
            val artist = if (isArtist) "Artist" else item.optString("parentTitle").trim().ifBlank { item.optString("grandparentTitle").trim() }
            if (key.isBlank() || (title.isBlank() && artist.isBlank())) continue
            val thumb = item.optString("thumb").ifBlank { item.optString("parentThumb") }
            add(
                PlexAlbum(
                    id = key,
                    title = title.ifBlank { "Untitled Album" },
                    artist = artist.ifBlank { "Unknown Artist" },
                    year = item.optInt("year", 0),
                    coverUrl = authedUrl(thumb),
                    isPlaylist = false,
                    trackCount = item.optInt("leafCount", 0),
                )
            )
        }
    }

    suspend fun fetchSimilarArtists(artistRatingKey: String): List<PlexArtist> = withContext(Dispatchers.IO) {
        val path = "/library/metadata/$artistRatingKey/similar"
        val container = runCatching { getJson(path).optJSONObject("MediaContainer") }.getOrNull() ?: return@withContext emptyList()
        val meta = container.optJSONArray("Metadata") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until meta.length().coerceAtMost(8)) {
                val item = meta.getJSONObject(i)
                val key = item.optString("ratingKey")
                val name = item.optString("title").trim()
                if (key.isBlank() || name.isBlank()) continue
                val thumb = item.optString("thumb")
                val summary = item.optString("summary")
                add(
                    PlexArtist(
                        id = key,
                        name = name,
                        thumbUrl = authedUrl(thumb),
                        summary = summary.ifBlank { null },
                    )
                )
            }
        }
    }

    suspend fun fetchHomeData(): PlexHomeData = withContext(Dispatchers.IO) {
        val sectionId = findMusicSectionId()
        if (sectionId.isBlank()) return@withContext PlexHomeData()

        // 1. Recently Played Albums
        val recentsPath = "/library/sections/$sectionId/all?type=9&sort=lastViewedAt:desc&limit=12"
        val recentsJson = runCatching { getJson(recentsPath).optJSONObject("MediaContainer") }.getOrNull()
        val recentsMeta = recentsJson?.optJSONArray("Metadata")
        val recentlyPlayed = recentsMeta?.let { parseAlbumsFromMetadata(it) } ?: emptyList()

        // Recent artist for similarity
        val firstRecent = if (recentsMeta != null && recentsMeta.length() > 0) recentsMeta.getJSONObject(0) else null
        val recentArtistKey = firstRecent?.optString("parentRatingKey")?.ifBlank { null }
        val recentArtistName = firstRecent?.optString("parentTitle")?.ifBlank { null }
        val recentArtistThumb = firstRecent?.optString("parentThumb")?.ifBlank { null }
        val recentArtist = if (recentArtistKey != null && recentArtistName != null) {
            PlexArtist(
                id = recentArtistKey,
                name = recentArtistName,
                thumbUrl = authedUrl(recentArtistThumb),
            )
        } else null

        // 2. Similar Artists based on last played artist
        val similarArtists = if (recentArtistKey != null) {
            fetchSimilarArtists(recentArtistKey)
        } else emptyList()

        // 3. Dynamic Hubs from /hubs/sections/$sectionId
        val hubsPath = "/hubs/sections/$sectionId"
        val hubsJson = runCatching { getJson(hubsPath).optJSONObject("MediaContainer") }.getOrNull()
        val hubArray = hubsJson?.optJSONArray("Hub")
        val parsedHubs = mutableListOf<PlexHub>()

        if (hubArray != null) {
            for (i in 0 until hubArray.length()) {
                val h = hubArray.getJSONObject(i)
                val title = h.optString("title").trim()
                val hubId = h.optString("hubIdentifier")
                val context = h.optString("context")
                val type = h.optString("type")
                val meta = h.optJSONArray("Metadata") ?: continue
                if (meta.length() == 0) continue

                // Skip touring, videos, or raw recent played since we already have formatted recentlyPlayed
                if (context == "hub.music.recent.played" || context == "hub.music.touring" || context == "hub.music.videos.new") continue

                val items = parseAlbumsFromMetadata(meta)
                if (items.isNotEmpty()) {
                    parsedHubs += PlexHub(
                        title = title,
                        hubIdentifier = hubId,
                        context = context,
                        type = type,
                        items = items,
                    )
                }
            }
        }

        // If hubs was empty, fallback to popular and recent
        if (parsedHubs.isEmpty()) {
            val popular = runCatching { fetchItems(PlexSectionTab.POPULAR, limit = 10) }.getOrDefault(emptyList())
            if (popular.isNotEmpty()) {
                parsedHubs += PlexHub(
                    title = "Most Played",
                    hubIdentifier = "fallback.popular",
                    context = "hub.music.popular",
                    type = "album",
                    items = popular,
                )
            }
        }

        PlexHomeData(
            recentlyPlayed = recentlyPlayed,
            hubs = parsedHubs,
            similarArtists = similarArtists,
            recentArtist = recentArtist,
        )
    }

    private fun fetchPlaylists(searchQuery: String, letter: String?): List<PlexAlbum> {
        val container = getJson("/playlists?playlistType=audio").optJSONObject("MediaContainer") ?: return emptyList()
        val meta = container.optJSONArray("Metadata") ?: return emptyList()
        return buildList {
            // Pinned "Loved Tracks" entry at the top of playlists
            if (searchQuery.isBlank() && (letter == null || letter == "★" || letter.equals("F", ignoreCase = true))) {
                add(
                    PlexAlbum(
                        id = SPECIAL_FAVORITES_ID,
                        title = "★ Loved Tracks",
                        artist = "Plex Favorites",
                        coverUrl = null,
                        isPlaylist = true,
                        trackCount = 0,
                    )
                )
            }
            for (i in 0 until meta.length()) {
                val item = meta.getJSONObject(i)
                val key = item.optString("ratingKey")
                val title = item.optString("title", "Untitled Playlist").trim()
                val thumb = item.optString("composite").ifBlank { item.optString("thumb") }
                val count = item.optInt("leafCount", 0)
                if (searchQuery.isNotBlank() && !title.contains(searchQuery.trim(), ignoreCase = true)) continue
                if (letter != null) {
                    val first = title.firstOrNull()
                    val match = if (letter == "#") first == null || !first.isLetter() else title.startsWith(letter, ignoreCase = true)
                    if (!match) continue
                }
                add(
                    PlexAlbum(
                        id = "playlist_$key",
                        title = title,
                        artist = "Plex Playlist · $count tracks",
                        coverUrl = authedUrl(thumb),
                        isPlaylist = true,
                        trackCount = count,
                    )
                )
            }
        }
    }

    private fun parseTracksFromMetadata(
        meta: org.json.JSONArray,
        defaultArtist: String = "",
        defaultAlbum: String = "",
        defaultCoverUrl: String? = null,
    ): List<LocalTrack> = buildList {
        for (i in 0 until meta.length()) {
            val t = meta.getJSONObject(i)
            val ratingKey = t.optString("ratingKey")
            val albumRatingKey = t.optString("parentRatingKey").takeIf { it.isNotBlank() }
            val title = t.optString("title", "Track ${i + 1}").trim()
            val artist = t.optString("originalTitle")
                .ifBlank { t.optString("grandparentTitle") }
                .ifBlank { defaultArtist }
            val albumTitle = t.optString("parentTitle").ifBlank { defaultAlbum }
            val thumb = t.optString("thumb").ifBlank { t.optString("parentThumb") }
            val coverUri = (authedUrl(thumb) ?: defaultCoverUrl)?.let(Uri::parse)
            val userRating = t.optDouble("userRating", 0.0)
            val isFav = userRating >= 9.0

            val mediaArr = t.optJSONArray("Media")
            val partArr = if (mediaArr != null && mediaArr.length() > 0) {
                mediaArr.getJSONObject(0).optJSONArray("Part")
            } else null
            val partKey = if (partArr != null && partArr.length() > 0) {
                partArr.getJSONObject(0).optString("key")
            } else ""
            val downloadKey = if (partKey.isNotBlank()) {
                if (partKey.contains("?")) "$partKey&download=1" else "$partKey?download=1"
            } else ""
            val streamUrl = authedUrl(downloadKey) ?: continue

            add(
                LocalTrack(
                    uri = Uri.parse(streamUrl),
                    title = title,
                    artist = artist,
                    folder = albumTitle,
                    coverUri = coverUri,
                    ratingKey = ratingKey,
                    albumRatingKey = albumRatingKey,
                    isFavorite = isFav,
                )
            )
        }
    }

    suspend fun fetchTracks(album: PlexAlbum): List<LocalTrack> = withContext(Dispatchers.IO) {
        if (album.id == SPECIAL_FAVORITES_ID) {
            return@withContext fetchFavorites()
        }
        if (album.id.startsWith("radio_")) {
            val rawId = album.id.removePrefix("radio_")
            return@withContext fetchStationTracks(rawId)
        }
        val isPlaylist = album.id.startsWith("playlist_")
        val rawId = album.id.removePrefix("playlist_")
        val path = if (isPlaylist) "/playlists/$rawId/items" else "/library/metadata/$rawId/children"
        val container = getJson(path).optJSONObject("MediaContainer") ?: return@withContext emptyList()
        val meta = container.optJSONArray("Metadata") ?: return@withContext emptyList()
        parseTracksFromMetadata(meta, defaultArtist = album.artist, defaultAlbum = album.title, defaultCoverUrl = album.coverUrl)
    }

    suspend fun rateItem(ratingKey: String, rating: Int): Boolean = withContext(Dispatchers.IO) {
        if (plexToken.isBlank()) return@withContext false
        val cleanRating = if (rating < 0) -1 else rating.coerceIn(0, 10)
        val path = "/:/rate?key=$ratingKey&identifier=com.plexapp.plugins.library&rating=$cleanRating"
        runCatching {
            val fullUrl = "${baseUrl()}$path"
            val conn = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("X-Plex-Token", plexToken)
                setRequestProperty("X-Plex-Client-Identifier", "foldman-walkman-rx707")
            }
            try {
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }

    suspend fun fetchFavorites(start: Int = 0, limit: Int = 200): List<LocalTrack> = withContext(Dispatchers.IO) {
        if (plexToken.isBlank()) return@withContext emptyList()
        val sectionId = runCatching { findMusicSectionId() }.getOrNull() ?: return@withContext emptyList()
        val path = "/library/sections/$sectionId/all?type=10&userRating>=9&sort=titleSort:asc&X-Plex-Container-Start=$start&X-Plex-Container-Size=$limit"
        val container = runCatching { getJson(path).optJSONObject("MediaContainer") }.getOrNull() ?: return@withContext emptyList()
        val meta = container.optJSONArray("Metadata") ?: return@withContext emptyList()
        parseTracksFromMetadata(meta, defaultAlbum = "Plex Favorites")
    }

    private fun normalizeTitleForDedup(title: String): String =
        title.lowercase().replace(Regex("\\s*\\([^)]*\\)|\\s*\\[[^]]*\\]"), "").replace(Regex("[^a-z0-9]"), "")

    fun isHolidayTitleOrAlbum(title: String, album: String): Boolean {
        val hay = "$title $album".lowercase()
        val holidayWords = listOf(
            "christmas", "xmas", "holiday", "holidays", "santa", "jingle", "noel",
            "merry", "sleigh", "rudolph", "frosty", "mistletoe", "silent night",
            "holy night", "carol", "yuletide", "navidad", "festive", "winter wonderland",
            "little drummer boy", "silver bells", "deck the halls", "white winter"
        )
        return holidayWords.any { hay.contains(it) }
    }

    suspend fun fetchSonicallySimilarTracks(
        ratingKey: String,
        limit: Int = 25,
        maxDistance: Float = 0.25f,
        forceHolidayTheme: Boolean = false,
    ): List<LocalTrack> = withContext(Dispatchers.IO) {
        if (plexToken.isBlank()) return@withContext emptyList()
        val cleanKey = ratingKey.removePrefix("playlist_").removePrefix("album_").removePrefix("radio_").removePrefix("artist_")
        val sId = runCatching { findMusicSectionId() }.getOrNull()
        var seedNormTitle: String? = null
        var thematicStyleId: String? = null
        var thematicGenreId: String? = null
        var isHolidayTheme = forceHolidayTheme

        if (cleanKey.isNotBlank() && cleanKey.all { it.isDigit() }) {
            val trackMeta = runCatching {
                getJson("/library/metadata/$cleanKey").optJSONObject("MediaContainer")?.optJSONArray("Metadata")?.optJSONObject(0)
            }.getOrNull()
            val seedTitle = trackMeta?.optString("title").orEmpty()
            val seedAlbum = trackMeta?.optString("parentTitle").orEmpty()
            if (seedTitle.isNotBlank()) seedNormTitle = normalizeTitleForDedup(seedTitle)
            if (isHolidayTitleOrAlbum(seedTitle, seedAlbum)) isHolidayTheme = true
            val parentKey = trackMeta?.optString("parentRatingKey")?.takeIf { it.isNotBlank() } ?: cleanKey
            val albumMeta = runCatching {
                getJson("/library/metadata/$parentKey").optJSONObject("MediaContainer")?.optJSONArray("Metadata")?.optJSONObject(0)
            }.getOrNull()
            val thematicKeywords = listOf("christmas", "holiday", "holidays", "halloween", "children", "soundtrack", "classical")
            val styles = albumMeta?.optJSONArray("Style")
            if (styles != null) {
                for (i in 0 until styles.length()) {
                    val s = styles.optJSONObject(i) ?: continue
                    val tag = s.optString("tag").lowercase()
                    if (thematicKeywords.any { tag.contains(it) }) {
                        thematicStyleId = s.optString("id").takeIf { it.isNotBlank() }
                        if (tag.contains("christmas") || tag.contains("holiday")) isHolidayTheme = true
                        break
                    }
                }
            }
            if (thematicStyleId == null) {
                val genres = albumMeta?.optJSONArray("Genre")
                if (genres != null) {
                    for (i in 0 until genres.length()) {
                        val g = genres.optJSONObject(i) ?: continue
                        val tag = g.optString("tag").lowercase()
                        if (thematicKeywords.any { tag.contains(it) }) {
                            thematicGenreId = g.optString("id").takeIf { it.isNotBlank() }
                            if (tag.contains("christmas") || tag.contains("holiday")) isHolidayTheme = true
                            break
                        }
                    }
                }
            }
        }

        fun dedup(tracks: List<LocalTrack>): List<LocalTrack> {
            val seenTitles = mutableSetOf<String>()
            seedNormTitle?.let { seenTitles.add(it) }
            val out = mutableListOf<LocalTrack>()
            for (t in tracks) {
                val norm = normalizeTitleForDedup(t.title)
                if (norm.isNotBlank() && norm in seenTitles) continue
                if (norm.isNotBlank()) seenTitles.add(norm)
                out.add(t)
            }
            return out
        }

        // 0. Strict Holiday / Christmas mode: keep 100% festive tracks
        if (sId != null && isHolidayTheme) {
            val pool = mutableListOf<LocalTrack>()
            if (thematicStyleId != null || thematicGenreId != null) {
                val filterParam = if (thematicStyleId != null) "album.style=$thematicStyleId" else "album.genre=$thematicGenreId"
                val themePath = "/library/sections/$sId/all?type=10&$filterParam&sort=random&X-Plex-Container-Start=0&X-Plex-Container-Size=${limit * 4}"
                val themeMeta = runCatching { getJson(themePath).optJSONObject("MediaContainer")?.optJSONArray("Metadata") }.getOrNull()
                if (themeMeta != null) {
                    pool += parseTracksFromMetadata(themeMeta, defaultAlbum = "Holiday Mix")
                }
            }
            for (q in listOf("Christmas", "Holiday")) {
                if (pool.count { isHolidayTitleOrAlbum(it.title, it.folder) } >= limit * 2) break
                val albPath = "/library/sections/$sId/all?type=9&title=${java.net.URLEncoder.encode(q, "UTF-8")}&sort=random&X-Plex-Container-Start=0&X-Plex-Container-Size=12"
                val albMeta = runCatching { getJson(albPath).optJSONObject("MediaContainer")?.optJSONArray("Metadata") }.getOrNull()
                if (albMeta != null) {
                    val albums = parseAlbumsFromMetadata(albMeta).shuffled().take(5)
                    for (alb in albums) {
                        pool += runCatching { fetchTracks(alb) }.getOrDefault(emptyList())
                    }
                }
            }
            val strictHoliday = dedup(pool.filter { isHolidayTitleOrAlbum(it.title, it.folder) }.shuffled()).take(limit)
            if (strictHoliday.isNotEmpty()) return@withContext strictHoliday
        }

        // 0b. Other thematic genres/styles
        if (sId != null && (thematicStyleId != null || thematicGenreId != null)) {
            val filterParam = if (thematicStyleId != null) "album.style=$thematicStyleId" else "album.genre=$thematicGenreId"
            val themePath = "/library/sections/$sId/all?type=10&$filterParam&sort=random&X-Plex-Container-Start=0&X-Plex-Container-Size=${limit * 2}"
            val themeMeta = runCatching { getJson(themePath).optJSONObject("MediaContainer")?.optJSONArray("Metadata") }.getOrNull()
            if (themeMeta != null && themeMeta.length() > 0) {
                val themeTracks = dedup(parseTracksFromMetadata(themeMeta, defaultAlbum = "Sonic DJ")).take(limit)
                if (themeTracks.isNotEmpty()) return@withContext themeTracks
            }
        }

        if (cleanKey.isNotBlank() && cleanKey.all { it.isDigit() }) {
            // 1. Try /nearest with requested maxDistance
            val path = "/library/metadata/$cleanKey/nearest?limit=${limit * 2}&maxDistance=$maxDistance"
            val meta = runCatching { getJson(path).optJSONObject("MediaContainer")?.optJSONArray("Metadata") }.getOrNull()
            if (meta != null && meta.length() > 0) {
                val parsed = dedup(parseTracksFromMetadata(meta, defaultAlbum = "Sonically Similar")).take(limit)
                if (parsed.isNotEmpty()) return@withContext parsed
            }
            // 2. Retry /nearest without maxDistance cap
            val widePath = "/library/metadata/$cleanKey/nearest?limit=${limit * 2}"
            val wideMeta = runCatching { getJson(widePath).optJSONObject("MediaContainer")?.optJSONArray("Metadata") }.getOrNull()
            if (wideMeta != null && wideMeta.length() > 0) {
                val parsed = dedup(parseTracksFromMetadata(wideMeta, defaultAlbum = "Sonically Similar")).take(limit)
                if (parsed.isNotEmpty()) return@withContext parsed
            }
        }
        // 3. Fallback: random tracks from music section
        if (sId != null) {
            val randPath = "/library/sections/$sId/all?type=10&sort=random&X-Plex-Container-Start=0&X-Plex-Container-Size=$limit"
            val randMeta = runCatching { getJson(randPath).optJSONObject("MediaContainer")?.optJSONArray("Metadata") }.getOrNull()
            if (randMeta != null && randMeta.length() > 0) {
                val parsed = dedup(parseTracksFromMetadata(randMeta, defaultAlbum = "Sonic Flow")).take(limit)
                if (parsed.isNotEmpty()) return@withContext parsed
            }
        }
        emptyList()
    }

    suspend fun fetchStationTracks(ratingKey: String): List<LocalTrack> = withContext(Dispatchers.IO) {
        if (plexToken.isBlank()) return@withContext emptyList()
        val cleanKey = ratingKey.removePrefix("playlist_").removePrefix("album_").removePrefix("radio_").removePrefix("artist_")

        // 1. Try metadata item with includeStations=1 to get station key
        val metaJson = runCatching { getJson("/library/metadata/$cleanKey?includeStations=1") }.getOrNull()
        val stationsArr = metaJson?.optJSONObject("MediaContainer")?.optJSONArray("Station")
            ?: metaJson?.optJSONObject("MediaContainer")?.optJSONArray("Stations")
        val stationKey = if (stationsArr != null && stationsArr.length() > 0) {
            stationsArr.getJSONObject(0).optString("key")
        } else null

        if (!stationKey.isNullOrBlank()) {
            val stationJson = runCatching { getJson(stationKey) }.getOrNull()
            val stationTracks = stationJson?.optJSONObject("MediaContainer")?.optJSONArray("Metadata")
            if (stationTracks != null && stationTracks.length() > 0) {
                return@withContext parseTracksFromMetadata(stationTracks, defaultAlbum = "Plex Radio")
            }
        }

        // 2. Try direct /station endpoint
        val directStationJson = runCatching { getJson("/library/metadata/$cleanKey/station") }.getOrNull()
        val directMeta = directStationJson?.optJSONObject("MediaContainer")?.optJSONArray("Metadata")
        if (directMeta != null && directMeta.length() > 0) {
            return@withContext parseTracksFromMetadata(directMeta, defaultAlbum = "Plex Radio")
        }

        // 3. Try sonically similar tracks (/nearest)
        val sonicTracks = fetchSonicallySimilarTracks(cleanKey, limit = 30)
        if (sonicTracks.isNotEmpty()) {
            return@withContext sonicTracks
        }

        // 4. Robust fallback: artist tracks (allLeaves or topTracks)
        val itemMeta = metaJson?.optJSONObject("MediaContainer")?.optJSONArray("Metadata")?.optJSONObject(0)
        val artistKey = itemMeta?.optString("grandparentRatingKey")
            ?.takeIf { it.isNotBlank() }
            ?: itemMeta?.optString("parentRatingKey")?.takeIf { itemMeta.optString("type") == "album" }
            ?: cleanKey

        val artistTracksJson = runCatching { getJson("/library/metadata/$artistKey/allLeaves") }.getOrNull()
            ?: runCatching { getJson("/library/metadata/$artistKey/topTracks") }.getOrNull()
        val fallbackMeta = artistTracksJson?.optJSONObject("MediaContainer")?.optJSONArray("Metadata")
        if (fallbackMeta != null && fallbackMeta.length() > 0) {
            val parsed = parseTracksFromMetadata(fallbackMeta, defaultAlbum = "Plex Radio").shuffled()
            return@withContext parsed.take(30)
        }

        emptyList()
    }

    suspend fun fetchFirstCharacters(type: Int = 8): List<PlexLetterSummary> = withContext(Dispatchers.IO) {
        if (plexToken.isBlank()) throw IllegalStateException("Plex token is required.")
        val sectionId = findMusicSectionId()
        val path = "/library/sections/$sectionId/firstCharacter?type=$type"
        val container = runCatching { getJson(path).optJSONObject("MediaContainer") }.getOrNull() ?: return@withContext emptyList()
        val dirs = container.optJSONArray("Directory") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until dirs.length()) {
                val d = dirs.getJSONObject(i)
                val key = d.optString("key")
                val title = d.optString("title").trim()
                val size = d.optInt("size", 0)
                if (key.isNotBlank() && title.isNotBlank()) {
                    add(PlexLetterSummary(key = key, title = title, size = size))
                }
            }
        }
    }

    suspend fun fetchArtists(
        searchQuery: String = "",
        letter: String? = null,
        start: Int = 0,
        limit: Int = if (letter != null) 1000 else 200,
    ): List<PlexArtist> = withContext(Dispatchers.IO) {
        if (plexToken.isBlank()) throw IllegalStateException("Plex token is required.")
        val sectionId = findMusicSectionId()
        val path = buildString {
            if (letter != null && searchQuery.isBlank()) {
                val enc = if (letter == "#") "%23" else URLEncoder.encode(letter, StandardCharsets.UTF_8.name())
                append("/library/sections/$sectionId/firstCharacter/$enc?type=8&sort=titleSort:asc")
            } else {
                append("/library/sections/$sectionId/all?type=8&sort=titleSort:asc")
                if (searchQuery.isNotBlank()) {
                    val q = URLEncoder.encode(searchQuery.trim(), StandardCharsets.UTF_8.name())
                    append("&title=$q")
                }
            }
            append("&X-Plex-Container-Start=$start&X-Plex-Container-Size=$limit")
        }
        val container = getJson(path).optJSONObject("MediaContainer") ?: return@withContext emptyList()
        val meta = container.optJSONArray("Metadata") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until meta.length()) {
                val item = meta.getJSONObject(i)
                val key = item.optString("ratingKey")
                val name = item.optString("title").trim()
                if (key.isBlank() || name.isBlank()) continue
                val thumb = item.optString("thumb")
                add(
                    PlexArtist(
                        id = key,
                        name = name,
                        thumbUrl = authedUrl(thumb),
                        summary = item.optString("summary").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
    }

    suspend fun fetchArtistAlbums(artistId: String): List<PlexAlbum> = withContext(Dispatchers.IO) {
        if (plexToken.isBlank()) throw IllegalStateException("Plex token is required.")
        val path = "/library/metadata/$artistId/children"
        val container = getJson(path).optJSONObject("MediaContainer") ?: return@withContext emptyList()
        val meta = container.optJSONArray("Metadata") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until meta.length()) {
                val item = meta.getJSONObject(i)
                if (item.has("deletedAt")) continue
                val key = item.optString("ratingKey")
                val title = item.optString("title").trim()
                val artist = item.optString("parentTitle").trim()
                if (key.isBlank() || title.isBlank()) continue
                val thumb = item.optString("thumb").ifBlank { item.optString("parentThumb") }
                add(
                    PlexAlbum(
                        id = key,
                        title = title,
                        artist = artist,
                        year = item.optInt("year", 0),
                        coverUrl = authedUrl(thumb),
                        isPlaylist = false,
                        trackCount = item.optInt("leafCount", 0),
                    )
                )
            }
        }
    }

    companion object {
        const val DEFAULT_URL = ""
        const val DEFAULT_TOKEN = ""
        const val DEFAULT_LIBRARY = "Music"
        const val SPECIAL_FAVORITES_ID = "special_favorites"

        fun fromPreferences(context: android.content.Context): PlexClient {
            val prefs = context.getSharedPreferences("foldman", android.content.Context.MODE_PRIVATE)
            return PlexClient(
                serverUrl = prefs.getString("plex_server_url", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_URL,
                plexToken = prefs.getString("plex_token", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_TOKEN,
                libraryName = prefs.getString("plex_library_name", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_LIBRARY,
            )
        }
    }
}
