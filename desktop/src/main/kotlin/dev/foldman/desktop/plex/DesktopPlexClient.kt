package dev.foldman.desktop.plex

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.foldman.deck.LocalTrack
import dev.foldman.deck.PlexAlbum
import dev.foldman.deck.PlexArtist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class DesktopPlexClient(
    var serverUrl: String = "",
    var token: String = "",
    var sectionId: String = "",
) {
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
        val path = "/library/metadata/${album.id}/children"
        val json = runCatching { getJson(path) }.getOrNull() ?: return@withContext emptyList()
        val meta = json.optJSONObject("MediaContainer")?.optJSONArray("Metadata") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until meta.length()) {
                val t = meta.getJSONObject(i)
                val title = t.optString("title", "Track ${i + 1}").trim()
                val artist = t.optString("originalTitle")
                    .ifBlank { t.optString("grandparentTitle") }
                    .ifBlank { album.artist }
                val albumTitle = t.optString("parentTitle").ifBlank { album.title }
                val duration = t.optLong("duration", 0L)

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
                        coverUri = album.coverUrl,
                        folder = albumTitle,
                    )
                )
            }
        }
    }

    suspend fun loadArtwork(url: String?): ImageBitmap? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 12000
                setRequestProperty("X-Plex-Token", token)
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            val skiaImage = Image.makeFromEncoded(bytes)
            skiaImage.toComposeImageBitmap()
        }.getOrNull()
    }

    private fun parseAlbums(meta: org.json.JSONArray): List<PlexAlbum> = buildList {
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
