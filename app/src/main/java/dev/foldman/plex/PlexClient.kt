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
    ARTISTS("Artists"),
    ALL("Albums"),
    RECENT("Recent"),
    POPULAR("Popular"),
    PLAYLISTS("Playlists"),
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
        if (tab == PlexSectionTab.PLAYLISTS) {
            return@withContext fetchPlaylists(searchQuery, letter)
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
        buildList {
            for (i in 0 until meta.length()) {
                val item = meta.getJSONObject(i)
                if (item.has("deletedAt")) continue
                val key = item.optString("ratingKey")
                val title = item.optString("title").trim()
                val artist = item.optString("parentTitle").trim()
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

    private fun fetchPlaylists(searchQuery: String, letter: String?): List<PlexAlbum> {
        val container = getJson("/playlists?playlistType=audio").optJSONObject("MediaContainer") ?: return emptyList()
        val meta = container.optJSONArray("Metadata") ?: return emptyList()
        return buildList {
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

    suspend fun fetchTracks(album: PlexAlbum): List<LocalTrack> = withContext(Dispatchers.IO) {
        val isPlaylist = album.id.startsWith("playlist_")
        val rawId = album.id.removePrefix("playlist_")
        val path = if (isPlaylist) "/playlists/$rawId/items" else "/library/metadata/$rawId/children"
        val container = getJson(path).optJSONObject("MediaContainer") ?: return@withContext emptyList()
        val meta = container.optJSONArray("Metadata") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until meta.length()) {
                val t = meta.getJSONObject(i)
                val title = t.optString("title", "Track ${i + 1}").trim()
                val artist = t.optString("originalTitle")
                    .ifBlank { t.optString("grandparentTitle") }
                    .ifBlank { album.artist }
                val albumTitle = t.optString("parentTitle").ifBlank { album.title }
                val thumb = t.optString("thumb").ifBlank { t.optString("parentThumb") }
                val coverUri = (authedUrl(thumb) ?: album.coverUrl)?.let(Uri::parse)

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
                        uri = Uri.parse(streamUrl),
                        title = title,
                        artist = artist,
                        folder = albumTitle,
                        coverUri = coverUri,
                    )
                )
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
    }
}
