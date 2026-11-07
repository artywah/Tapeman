package dev.foldman.desktop.plex

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.foldman.deck.LocalTrack
import dev.foldman.deck.PlexAlbum
import dev.foldman.deck.PlexArtist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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

class DesktopPlexClient(
    var serverUrl: String = "",
    var token: String = "",
    var sectionId: String = "",
) {
    companion object {
        const val SPECIAL_FAVORITES_ID = "special_favorites"
    }

    fun isConfigured(): Boolean = serverUrl.isNotBlank() && token.isNotBlank()

    fun authedUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val clean = if (path.startsWith("/")) path else "/$path"
        val sep = if (clean.contains("?")) "&" else "?"
        return "${serverUrl.trimEnd('/')}$clean${sep}X-Plex-Token=${URLEncoder.encode(token, "UTF-8")}"
    }

    private fun getJson(path: String): JSONObject {
        val fullUrl = if (path.startsWith("http")) path else "${serverUrl.trimEnd('/')}$path"
        val conn = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 20000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Plex-Token", token)
            setRequestProperty("X-Plex-Client-Identifier", "tapeman-desktop")
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

    suspend fun fetchAvailableLibraries(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()
        val json = runCatching { getJson("/library/sections") }.getOrNull() ?: return@withContext emptyList()
        val dirs = json.optJSONObject("MediaContainer")?.optJSONArray("Directory") ?: return@withContext emptyList()
        val sections = mutableListOf<Pair<String, String>>()
        for (i in 0 until dirs.length()) {
            val d = dirs.getJSONObject(i)
            if (d.optString("type") == "artist") {
                val key = d.getString("key")
                val title = d.optString("title", "Music")
                sections.add(key to title)
            }
        }
        sections
    }

    suspend fun resolveSectionId(libraryName: String = "Music"): String = withContext(Dispatchers.IO) {
        if (sectionId.isNotBlank()) return@withContext sectionId
        val json = getJson("/library/sections")
        val dirs = json.optJSONObject("MediaContainer")?.optJSONArray("Directory")
            ?: throw IllegalStateException("No libraries found.")
        var fallback: String? = null
        for (i in 0 until dirs.length()) {
            val d = dirs.getJSONObject(i)
            if (d.optString("type") == "artist") {
                val key = d.getString("key")
                val title = d.optString("title")
                if (title.equals(libraryName, ignoreCase = true)) {
                    sectionId = key
                    return@withContext key
                }
                if (fallback == null) fallback = key
            }
        }
        val chosen = fallback ?: throw IllegalStateException("Music library not found.")
        sectionId = chosen
        chosen
    }

    suspend fun fetchHomeData(): PlexHomeData = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext PlexHomeData()
        val sId = resolveSectionId()

        // 1. Recently Played Albums
        val recentsPath = "/library/sections/$sId/all?type=9&sort=lastViewedAt:desc&limit=12"
        val recentsJson = runCatching { getJson(recentsPath).optJSONObject("MediaContainer") }.getOrNull()
        val recentsMeta = recentsJson?.optJSONArray("Metadata")
        val recentlyPlayed = recentsMeta?.let { parseAlbums(it) } ?: emptyList()

        // 2. Recent artist for similarity
        var recentArtist: PlexArtist? = null
        var similarArtists: List<PlexArtist> = emptyList()
        val firstRecent = recentlyPlayed.firstOrNull()
        if (firstRecent != null) {
            val albumMetaJson = runCatching { getJson("/library/metadata/${firstRecent.id}").optJSONObject("MediaContainer") }.getOrNull()
            val metaObj = albumMetaJson?.optJSONArray("Metadata")?.optJSONObject(0)
            val artistKey = metaObj?.optString("grandparentRatingKey")?.takeIf { it.isNotBlank() }
                ?: metaObj?.optString("parentRatingKey")?.takeIf { it.isNotBlank() }

            if (artistKey != null) {
                val artistJson = runCatching { getJson("/library/metadata/$artistKey").optJSONObject("MediaContainer") }.getOrNull()
                val aObj = artistJson?.optJSONArray("Metadata")?.optJSONObject(0)
                if (aObj != null) {
                    recentArtist = PlexArtist(
                        id = artistKey,
                        name = aObj.optString("title", firstRecent.artist),
                        thumbUrl = authedUrl(aObj.optString("thumb")),
                        summary = aObj.optString("summary")
                    )
                }

                // Check similar artists
                val simJson = runCatching { getJson("/library/metadata/$artistKey/similar").optJSONObject("MediaContainer") }.getOrNull()
                val simMeta = simJson?.optJSONArray("Metadata")
                if (simMeta != null && simMeta.length() > 0) {
                    similarArtists = buildList {
                        for (i in 0 until minOf(simMeta.length(), 8)) {
                            val item = simMeta.getJSONObject(i)
                            add(
                                PlexArtist(
                                    id = item.optString("ratingKey"),
                                    name = item.optString("title"),
                                    thumbUrl = authedUrl(item.optString("thumb")),
                                    summary = item.optString("summary")
                                )
                            )
                        }
                    }
                }
            }
        }

        // 3. Dynamic Hubs from /hubs/sections/$sId
        val hubsPath = "/hubs/sections/$sId"
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

                val items = parseAlbums(meta)
                if (items.isNotEmpty()) {
                    parsedHubs += PlexHub(
                        title = title.ifBlank { "Featured" },
                        hubIdentifier = hubId,
                        context = context,
                        type = type,
                        items = items,
                    )
                }
            }
        }

        // If hubs was empty, fallback to recent albums
        if (parsedHubs.isEmpty()) {
            val recents = fetchRecentAlbums(limit = 12)
            if (recents.isNotEmpty()) {
                parsedHubs += PlexHub(
                    title = "Recently Added in Music",
                    hubIdentifier = "fallback.recents",
                    context = "hub.music.recent",
                    type = "album",
                    items = recents,
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

    suspend fun fetchPlaylists(searchQuery: String = ""): List<PlexAlbum> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()
        val container = runCatching { getJson("/playlists?playlistType=audio").optJSONObject("MediaContainer") }.getOrNull()
            ?: return@withContext emptyList()
        val meta = container.optJSONArray("Metadata") ?: return@withContext emptyList()
        buildList {
            // Pinned "Loved Tracks" entry at the top of playlists
            if (searchQuery.isBlank() || "loved".contains(searchQuery.trim(), ignoreCase = true) || "favorites".contains(searchQuery.trim(), ignoreCase = true)) {
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
                add(
                    PlexAlbum(
                        id = "playlist_$key",
                        title = title,
                        artist = "${count} tracks",
                        year = 0,
                        coverUrl = authedUrl(thumb),
                        isPlaylist = true,
                        trackCount = count,
                    )
                )
            }
        }
    }

    suspend fun fetchRecentAlbums(limit: Int = 30): List<PlexAlbum> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()
        val sId = resolveSectionId()
        val path = "/library/sections/$sId/all?type=9&sort=addedAt:desc&X-Plex-Container-Start=0&X-Plex-Container-Size=$limit"
        val json = runCatching { getJson(path) }.getOrNull() ?: return@withContext emptyList()
        val meta = json.optJSONObject("MediaContainer")?.optJSONArray("Metadata") ?: return@withContext emptyList()
        parseAlbums(meta)
    }

    suspend fun fetchAllAlbums(
        searchQuery: String = "",
        start: Int = 0,
        limit: Int = 100,
    ): List<PlexAlbum> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()
        val sId = resolveSectionId()
        val path = buildString {
            append("/library/sections/$sId/all?type=9")
            if (searchQuery.isNotBlank()) {
                val q = URLEncoder.encode(searchQuery.trim(), StandardCharsets.UTF_8.name())
                append("&title=$q")
            } else {
                append("&sort=titleSort:asc")
            }
            append("&X-Plex-Container-Start=$start&X-Plex-Container-Size=$limit")
        }
        val json = runCatching { getJson(path) }.getOrNull() ?: return@withContext emptyList()
        val meta = json.optJSONObject("MediaContainer")?.optJSONArray("Metadata") ?: return@withContext emptyList()
        parseAlbums(meta)
    }

    suspend fun fetchArtists(limit: Int = 100): List<PlexArtist> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()
        val sId = resolveSectionId()
        val path = "/library/sections/$sId/all?type=8&sort=titleSort:asc&X-Plex-Container-Start=0&X-Plex-Container-Size=$limit"
        val json = runCatching { getJson(path) }.getOrNull() ?: return@withContext emptyList()
        val meta = json.optJSONObject("MediaContainer")?.optJSONArray("Metadata") ?: return@withContext emptyList()
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
                        summary = item.optString("summary")
                    )
                )
            }
        }
    }

    suspend fun fetchTracks(album: PlexAlbum): List<LocalTrack> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()
        if (album.id == SPECIAL_FAVORITES_ID) {
            return@withContext fetchFavorites()
        }
        if (album.id.startsWith("radio_")) {
            val rawId = album.id.removePrefix("radio_")
            return@withContext fetchStationTracks(rawId)
        }
        val isPlaylist = album.isPlaylist || album.id.startsWith("playlist_")
        val rawId = album.id.removePrefix("playlist_")
        val path = if (isPlaylist) "/playlists/$rawId/items" else "/library/metadata/$rawId/children"
        val json = runCatching { getJson(path) }.getOrNull() ?: return@withContext emptyList()
        val meta = json.optJSONObject("MediaContainer")?.optJSONArray("Metadata") ?: return@withContext emptyList()
        parseTracksFromMetadata(meta, defaultAlbum = album.title, defaultCoverUrl = album.coverUrl)
    }

    suspend fun fetchFavorites(): List<LocalTrack> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()
        val sId = resolveSectionId()
        // 1. Try 5-star / favorited tracks
        val path = "/library/sections/$sId/all?type=10&userRating>=1&sort=rating:desc&limit=50"
        val json = runCatching { getJson(path) }.getOrNull()
        val meta = json?.optJSONObject("MediaContainer")?.optJSONArray("Metadata")
        if (meta != null && meta.length() > 0) {
            return@withContext parseTracksFromMetadata(meta, defaultAlbum = "Plex Favorites")
        }
        // 2. Fallback to top rating tracks
        val fallbackPath = "/library/sections/$sId/all?type=10&sort=rating:desc&limit=50"
        val fbJson = runCatching { getJson(fallbackPath) }.getOrNull()
        val fbMeta = fbJson?.optJSONObject("MediaContainer")?.optJSONArray("Metadata") ?: return@withContext emptyList()
        parseTracksFromMetadata(fbMeta, defaultAlbum = "Plex Favorites")
    }

    suspend fun fetchSonicallySimilarTracks(
        ratingKey: String,
        limit: Int = 15,
        maxDistance: Float = 0.25f
    ): List<LocalTrack> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()
        val cleanKey = ratingKey.removePrefix("playlist_").removePrefix("album_").removePrefix("radio_").removePrefix("artist_")

        // 1. Try /nearest acoustic distance endpoint
        val path = "/library/metadata/$cleanKey/nearest?limit=$limit&maxDistance=$maxDistance"
        val container = runCatching { getJson(path).optJSONObject("MediaContainer") }.getOrNull()
        val meta = container?.optJSONArray("Metadata")
        if (meta != null && meta.length() > 0) {
            val sonicTracks = parseTracksFromMetadata(meta, defaultAlbum = "Sonically Similar")
            if (sonicTracks.isNotEmpty()) return@withContext sonicTracks
        }

        // 2. Fallback: random flow from music section for continuous flow
        val sId = resolveSectionId()
        val randomPath = "/library/sections/$sId/all?type=10&sort=random&limit=$limit"
        val randContainer = runCatching { getJson(randomPath).optJSONObject("MediaContainer") }.getOrNull()
        val randMeta = randContainer?.optJSONArray("Metadata") ?: return@withContext emptyList()
        parseTracksFromMetadata(randMeta, defaultAlbum = "Sonic Flow")
    }

    suspend fun fetchStationTracks(ratingKey: String): List<LocalTrack> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()
        val cleanKey = ratingKey.removePrefix("playlist_").removePrefix("album_").removePrefix("radio_").removePrefix("artist_")

        // 1. Try station tracks or sonically similar
        val sonic = fetchSonicallySimilarTracks(cleanKey, limit = 25)
        if (sonic.isNotEmpty()) return@withContext sonic

        // 2. Try artist allLeaves or topTracks
        val metaJson = runCatching { getJson("/library/metadata/$cleanKey").optJSONObject("MediaContainer") }.getOrNull()
        val itemMeta = metaJson?.optJSONArray("Metadata")?.optJSONObject(0)
        val artistKey = itemMeta?.optString("grandparentRatingKey")?.takeIf { it.isNotBlank() }
            ?: itemMeta?.optString("parentRatingKey")?.takeIf { itemMeta.optString("type") == "album" }
            ?: cleanKey

        val artistTracksJson = runCatching { getJson("/library/metadata/$artistKey/allLeaves") }.getOrNull()
            ?: runCatching { getJson("/library/metadata/$artistKey/topTracks") }.getOrNull()
        val fallbackMeta = artistTracksJson?.optJSONObject("MediaContainer")?.optJSONArray("Metadata")
        if (fallbackMeta != null && fallbackMeta.length() > 0) {
            val parsed = parseTracksFromMetadata(fallbackMeta, defaultAlbum = "Plex Radio").shuffled()
            return@withContext parsed.take(25)
        }

        // 3. Fallback to random section tracks
        val sId = resolveSectionId()
        val randJson = runCatching { getJson("/library/sections/$sId/all?type=10&sort=random&limit=25") }.getOrNull()
        val randMeta = randJson?.optJSONObject("MediaContainer")?.optJSONArray("Metadata") ?: return@withContext emptyList()
        parseTracksFromMetadata(randMeta, defaultAlbum = "Plex Radio")
    }

    private fun parseTracksFromMetadata(
        meta: JSONArray,
        defaultAlbum: String = "",
        defaultCoverUrl: String? = null
    ): List<LocalTrack> = buildList {
        for (i in 0 until meta.length()) {
            val t = meta.getJSONObject(i)
            val title = t.optString("title", "Track ${i + 1}").trim()
            val artist = t.optString("originalTitle")
                .ifBlank { t.optString("grandparentTitle") }
                .ifBlank { "Unknown Artist" }
            val albumTitle = t.optString("parentTitle").ifBlank { defaultAlbum }
            val duration = t.optLong("duration", 0L)
            val thumb = t.optString("thumb").ifBlank { t.optString("parentThumb") }
            val coverUrl = authedUrl(thumb) ?: defaultCoverUrl

            val mediaArr = t.optJSONArray("Media")
            val partArr = if (mediaArr != null && mediaArr.length() > 0) {
                mediaArr.getJSONObject(0).optJSONArray("Part")
            } else null
            val partKey = if (partArr != null && partArr.length() > 0) {
                partArr.getJSONObject(0).optString("key")
            } else ""
            val streamUrl = authedUrl(partKey) ?: continue

            add(
                LocalTrack(
                    uri = streamUrl,
                    title = title,
                    artist = artist,
                    album = albumTitle,
                    durationMs = duration,
                    coverUri = coverUrl,
                    folder = albumTitle,
                )
            )
        }
    }

    private val artworkCache = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

    fun getCachedArtwork(url: String?): ImageBitmap? = if (url != null) artworkCache[url] else null

    suspend fun loadArtwork(url: String?): ImageBitmap? {
        if (url.isNullOrBlank()) return null
        artworkCache[url]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 12000
                    setRequestProperty("X-Plex-Token", token)
                }
                val bytes = conn.inputStream.use { it.readBytes() }
                conn.disconnect()
                val skiaImage = Image.makeFromEncoded(bytes)
                val bmp = skiaImage.toComposeImageBitmap()
                artworkCache[url] = bmp
                bmp
            }.getOrNull()
        }
    }

    private fun parseAlbums(meta: JSONArray): List<PlexAlbum> = buildList {
        for (i in 0 until meta.length()) {
            val item = meta.getJSONObject(i)
            if (item.has("deletedAt")) continue
            val key = item.optString("ratingKey")
            val title = item.optString("title").trim()
            val artist = item.optString("parentTitle").trim()
                .ifBlank { item.optString("grandparentTitle").trim() }
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
}
