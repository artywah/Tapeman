package dev.foldman.desktop.spotify

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.json.JSONArray
import org.json.JSONObject
import java.awt.Desktop
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

enum class DesktopSpotifyTab(val label: String) {
    PLAYLISTS("Playlists"),
    ALBUMS("Albums"),
}

data class DesktopSpotifyCollection(
    val id: String,
    val name: String,
    val subtitle: String,
    val artworkUrl: String?,
    val uri: String,
    val isAlbum: Boolean = false,
)

data class DesktopSpotifyTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val uri: String,
)

data class DesktopSpotifyConfig(
    val clientId: String,
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAtMs: Long = 0L,
)

data class DesktopSpotifyPlaybackState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volumePercent: Int = 50,
    val trackTitle: String = "",
    val trackArtist: String = "",
    val albumTitle: String = "",
    val artworkUrl: String? = null,
    val trackUri: String? = null,
    val deviceName: String = "",
)

class DesktopSpotifyAuthExpired : Exception("Spotify session expired.")

class DesktopSpotifyClient {
    companion object {
        const val REDIRECT_PORT = 18492
        const val REDIRECT_URI = "http://127.0.0.1:$REDIRECT_PORT/callback"
        private const val SCOPES = "playlist-read-private playlist-read-collaborative user-library-read user-read-playback-state user-modify-playback-state"
    }

    private val configFile = File(System.getProperty("user.home"), ".tapeman/spotify_config.json")
    private val artworkCache = ConcurrentHashMap<String, ImageBitmap>()

    var config: DesktopSpotifyConfig? = loadConfig()
        private set

    fun loadConfig(): DesktopSpotifyConfig? = runCatching {
        if (!configFile.exists()) return null
        val j = JSONObject(configFile.readText())
        val token = j.optString("accessToken")
        if (token.isBlank()) return null
        DesktopSpotifyConfig(
            clientId = j.optString("clientId"),
            accessToken = token,
            refreshToken = j.optString("refreshToken").takeIf { it.isNotBlank() },
            expiresAtMs = j.optLong("expiresAtMs", 0L),
        )
    }.getOrNull()

    fun saveConfig(cfg: DesktopSpotifyConfig) {
        config = cfg
        runCatching {
            configFile.parentFile?.mkdirs()
            val j = JSONObject().apply {
                put("clientId", cfg.clientId)
                put("accessToken", cfg.accessToken)
                put("refreshToken", cfg.refreshToken ?: "")
                put("expiresAtMs", cfg.expiresAtMs)
            }
            configFile.writeText(j.toString(2))
        }
    }

    fun clearConfig() {
        config = null
        runCatching { if (configFile.exists()) configFile.delete() }
    }

    fun isSignedIn(): Boolean = config?.accessToken?.isNotBlank() == true

    suspend fun authenticatePkce(clientId: String): DesktopSpotifyConfig = withContext(Dispatchers.IO) {
        val cleanId = clientId.trim()
        require(cleanId.isNotBlank()) { "Spotify Client ID is required." }

        val verifierBytes = ByteArray(48).also { SecureRandom().nextBytes(it) }
        val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes)
        val challengeBytes = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes)

        val authUrl = "https://accounts.spotify.com/authorize?" + listOf(
            "client_id" to cleanId,
            "response_type" to "code",
            "redirect_uri" to REDIRECT_URI,
            "code_challenge_method" to "S256",
            "code_challenge" to challenge,
            "scope" to SCOPES,
        ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

        val codeDeferred = CompletableDeferred<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", REDIRECT_PORT), 0)
        server.createContext("/callback") { ex ->
            val query = ex.requestURI.rawQuery.orEmpty()
            val params = query.split("&").mapNotNull {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8") else null
            }.toMap()
            val err = params["error"]
            val code = params["code"]
            val html = if (code != null) {
                "<html><body style='background:#101114;color:#fff;font-family:sans-serif;text-align:center;padding:48px'><h2>Tapeman connected to Spotify!</h2><p>You can close this browser tab and return to Tapeman.</p></body></html>"
            } else {
                "<html><body style='background:#101114;color:#f55;font-family:sans-serif;text-align:center;padding:48px'><h2>Spotify authorization failed</h2><p>${err ?: "Unknown error"}</p></body></html>"
            }
            val bytes = html.toByteArray(Charsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
            if (code != null) {
                codeDeferred.complete(code)
            } else {
                codeDeferred.completeExceptionally(IllegalStateException(err ?: "Authorization cancelled"))
            }
        }
        server.start()
        try {
            Desktop.getDesktop().browse(URI(authUrl))
            val code = codeDeferred.await()
            val body = listOf(
                "client_id" to cleanId,
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT_URI,
                "code_verifier" to verifier,
            ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
            val cfg = postToken(cleanId, body)
            saveConfig(cfg)
            cfg
        } finally {
            server.stop(0)
        }
    }

    private suspend fun ensureFreshToken(): String {
        val cfg = config ?: throw DesktopSpotifyAuthExpired()
        if (cfg.expiresAtMs > 0L && System.currentTimeMillis() >= cfg.expiresAtMs && !cfg.refreshToken.isNullOrBlank() && cfg.clientId.isNotBlank()) {
            val refreshed = withContext(Dispatchers.IO) {
                val body = listOf(
                    "client_id" to cfg.clientId,
                    "grant_type" to "refresh_token",
                    "refresh_token" to cfg.refreshToken,
                ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
                postToken(cfg.clientId, body, fallbackRefresh = cfg.refreshToken)
            }
            saveConfig(refreshed)
            return refreshed.accessToken
        }
        return cfg.accessToken
    }

    private fun postToken(clientId: String, formBody: String, fallbackRefresh: String? = null): DesktopSpotifyConfig {
        val conn = (URL("https://accounts.spotify.com/api/token").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            conn.outputStream.use { it.write(formBody.toByteArray()) }
            if (conn.responseCode !in 200..299) {
                val err = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                throw IllegalStateException("Spotify token error (${conn.responseCode}): ${err ?: "failed"}")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val access = json.getString("access_token")
            val refresh = json.optString("refresh_token").takeIf { it.isNotBlank() } ?: fallbackRefresh
            val expiresIn = json.optLong("expires_in", 3600L)
            return DesktopSpotifyConfig(
                clientId = clientId,
                accessToken = access,
                refreshToken = refresh,
                expiresAtMs = System.currentTimeMillis() + (expiresIn - 60) * 1000L,
            )
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) {
        val token = ensureFreshToken()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code == 401) throw DesktopSpotifyAuthExpired()
            if (code == 204) return@withContext JSONObject()
            if (code !in 200..299) throw IllegalStateException("Spotify HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            if (body.isBlank()) JSONObject() else JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun sendCommand(method: String, url: String, jsonBody: String? = null): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val token = ensureFreshToken()
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Authorization", "Bearer $token")
                if (jsonBody != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            try {
                if (jsonBody != null) {
                    conn.outputStream.use { it.write(jsonBody.toByteArray()) }
                }
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }

    suspend fun playlists(): List<DesktopSpotifyCollection> {
        val root = getJson("https://api.spotify.com/v1/me/playlists?limit=50")
        val items = root.optJSONArray("items") ?: return emptyList()
        return buildList {
            for (i in 0 until items.length()) {
                val p = items.optJSONObject(i) ?: continue
                val id = p.optString("id")
                if (id.isBlank()) continue
                val name = p.optString("name", "Untitled Playlist")
                val owner = p.optJSONObject("owner")?.optString("display_name").orEmpty()
                val total = p.optJSONObject("tracks")?.optInt("total")
                    ?: p.optJSONObject("items")?.optInt("total")
                    ?: p.optInt("total_tracks", 0)
                val countText = if (total > 0) "$total songs" else "Playlist"
                add(
                    DesktopSpotifyCollection(
                        id = id,
                        name = name,
                        subtitle = listOfNotNull(owner.takeIf { it.isNotBlank() }, countText).joinToString(" · "),
                        artworkUrl = firstImage(p.optJSONArray("images")),
                        uri = p.optString("uri", "spotify:playlist:$id"),
                        isAlbum = false,
                    )
                )
            }
        }
    }

    suspend fun savedAlbums(): List<DesktopSpotifyCollection> {
        val root = getJson("https://api.spotify.com/v1/me/albums?limit=50")
        val items = root.optJSONArray("items") ?: return emptyList()
        return buildList {
            for (i in 0 until items.length()) {
                val a = items.optJSONObject(i)?.optJSONObject("album") ?: continue
                val id = a.optString("id")
                if (id.isBlank()) continue
                add(
                    DesktopSpotifyCollection(
                        id = id,
                        name = a.optString("name", "Untitled Album"),
                        subtitle = firstArtist(a.optJSONArray("artists")).ifBlank { "Album" },
                        artworkUrl = firstImage(a.optJSONArray("images")),
                        uri = a.optString("uri", "spotify:album:$id"),
                        isAlbum = true,
                    )
                )
            }
        }
    }

    suspend fun tracks(collection: DesktopSpotifyCollection): List<DesktopSpotifyTrack> {
        return if (collection.isAlbum) {
            val root = getJson("https://api.spotify.com/v1/albums/${collection.id}/tracks?limit=50")
            val items = root.optJSONArray("items") ?: return emptyList()
            buildList {
                for (i in 0 until items.length()) {
                    val t = items.optJSONObject(i) ?: continue
                    val id = t.optString("id")
                    if (id.isBlank()) continue
                    add(
                        DesktopSpotifyTrack(
                            id = id,
                            title = t.optString("name", "Track ${i + 1}"),
                            artist = firstArtist(t.optJSONArray("artists")),
                            album = collection.name,
                            durationMs = t.optLong("duration_ms", 0L),
                            artworkUrl = collection.artworkUrl,
                            uri = t.optString("uri", "spotify:track:$id"),
                        )
                    )
                }
            }
        } else {
            val root = runCatching {
                getJson("https://api.spotify.com/v1/playlists/${collection.id}/items?limit=100")
            }.recoverCatching {
                getJson("https://api.spotify.com/v1/playlists/${collection.id}/tracks?limit=100")
            }.getOrThrow()
            val items = root.optJSONArray("items") ?: return emptyList()
            buildList {
                for (i in 0 until items.length()) {
                    val raw = items.optJSONObject(i) ?: continue
                    val t = raw.optJSONObject("item") ?: raw.optJSONObject("track") ?: raw
                    val id = t.optString("id")
                    if (id.isBlank()) continue
                    val albumObj = t.optJSONObject("album")
                    add(
                        DesktopSpotifyTrack(
                            id = id,
                            title = t.optString("name", "Track ${i + 1}"),
                            artist = firstArtist(t.optJSONArray("artists")),
                            album = albumObj?.optString("name").orEmpty(),
                            durationMs = t.optLong("duration_ms", 0L),
                            artworkUrl = firstImage(albumObj?.optJSONArray("images")) ?: collection.artworkUrl,
                            uri = t.optString("uri", "spotify:track:$id"),
                        )
                    )
                }
            }
        }
    }

    suspend fun startPlayback(contextUri: String, offsetIndex: Int): Boolean {
        val payload = JSONObject().apply {
            put("context_uri", contextUri)
            put("offset", JSONObject().put("position", offsetIndex.coerceAtLeast(0)))
            put("position_ms", 0)
        }.toString()
        if (sendCommand("PUT", "https://api.spotify.com/v1/me/player/play", payload)) return true
        val devicesObj = runCatching { getJson("https://api.spotify.com/v1/me/player/devices") }.getOrNull()
        val devId = devicesObj?.optJSONArray("devices")?.optJSONObject(0)?.optString("id")
        if (!devId.isNullOrBlank()) {
            return sendCommand("PUT", "https://api.spotify.com/v1/me/player/play?device_id=$devId", payload)
        }
        return false
    }

    suspend fun pause(): Boolean = sendCommand("PUT", "https://api.spotify.com/v1/me/player/pause")
    suspend fun resume(): Boolean = sendCommand("PUT", "https://api.spotify.com/v1/me/player/play")
    suspend fun next(): Boolean = sendCommand("POST", "https://api.spotify.com/v1/me/player/next")
    suspend fun previous(): Boolean = sendCommand("POST", "https://api.spotify.com/v1/me/player/previous")
    suspend fun seek(positionMs: Long): Boolean =
        sendCommand("PUT", "https://api.spotify.com/v1/me/player/seek?position_ms=${positionMs.coerceAtLeast(0L)}")
    suspend fun setVolume(fraction: Float): Boolean {
        val pct = (fraction.coerceIn(0f, 1f) * 100).toInt()
        return sendCommand("PUT", "https://api.spotify.com/v1/me/player/volume?volume_percent=$pct")
    }

    suspend fun getPlaybackState(): DesktopSpotifyPlaybackState? {
        val root = runCatching { getJson("https://api.spotify.com/v1/me/player") }.getOrNull() ?: return null
        if (root.length() == 0) return null
        val item = root.optJSONObject("item") ?: return null
        val albumObj = item.optJSONObject("album")
        val devObj = root.optJSONObject("device")
        return DesktopSpotifyPlaybackState(
            isPlaying = root.optBoolean("is_playing", false),
            positionMs = root.optLong("progress_ms", 0L),
            durationMs = item.optLong("duration_ms", 0L),
            volumePercent = devObj?.optInt("volume_percent", 50) ?: 50,
            trackTitle = item.optString("name", ""),
            trackArtist = firstArtist(item.optJSONArray("artists")),
            albumTitle = albumObj?.optString("name").orEmpty(),
            artworkUrl = firstImage(albumObj?.optJSONArray("images")),
            trackUri = item.optString("uri").takeIf { it.isNotBlank() },
            deviceName = devObj?.optString("name").orEmpty(),
        )
    }

    suspend fun loadArtwork(url: String?): ImageBitmap? {
        if (url.isNullOrBlank()) return null
        artworkCache[url]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 10000
                }
                val bytes = conn.inputStream.use { it.readBytes() }
                conn.disconnect()
                val bmp = Image.makeFromEncoded(bytes).toComposeImageBitmap()
                artworkCache[url] = bmp
                bmp
            }.getOrNull()
        }
    }

    private fun firstImage(arr: JSONArray?): String? {
        if (arr == null || arr.length() == 0) return null
        return arr.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }
    }

    private fun firstArtist(arr: JSONArray?): String {
        if (arr == null || arr.length() == 0) return ""
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }.joinToString(", ")
    }
}
