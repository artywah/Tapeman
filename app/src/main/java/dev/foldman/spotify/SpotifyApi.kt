package dev.foldman.spotify

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

enum class SpotifyTab(val label: String) {
    PLAYLISTS("Playlists"),
    ALBUMS("Albums"),
}

data class SpotifyCollection(
    val id: String,
    val name: String,
    val subtitle: String,
    val artworkUrl: String?,
    val uri: String,
    val isAlbum: Boolean = false,
)

data class SpotifyTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val uri: String,
)

data class SpotifyTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMs: Long,
)

class SpotifyAuthExpired : Exception("Spotify session expired.")

class SpotifyApi(private val accessToken: String) {

    suspend fun playlists(): List<SpotifyCollection> = withContext(Dispatchers.IO) {
        val root = get("https://api.spotify.com/v1/me/playlists?limit=50")
        val items = root.optJSONArray("items") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until items.length()) {
                val p = items.optJSONObject(i) ?: continue
                val id = p.optString("id")
                val name = p.optString("name", "Untitled Playlist")
                val owner = p.optJSONObject("owner")?.optString("display_name").orEmpty()
                val total = p.optJSONObject("tracks")?.optInt("total", 0) ?: 0
                val art = firstImage(p.optJSONArray("images"))
                if (id.isNotBlank()) {
                    add(
                        SpotifyCollection(
                            id = id,
                            name = name,
                            subtitle = listOfNotNull(owner.takeIf { it.isNotBlank() }, "$total songs").joinToString(" · "),
                            artworkUrl = art,
                            uri = p.optString("uri", "spotify:playlist:$id"),
                            isAlbum = false,
                        )
                    )
                }
            }
        }
    }

    suspend fun savedAlbums(): List<SpotifyCollection> = withContext(Dispatchers.IO) {
        val root = get("https://api.spotify.com/v1/me/albums?limit=50")
        val items = root.optJSONArray("items") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until items.length()) {
                val a = items.optJSONObject(i)?.optJSONObject("album") ?: continue
                val id = a.optString("id")
                val name = a.optString("name", "Untitled Album")
                val artist = firstArtist(a.optJSONArray("artists"))
                val art = firstImage(a.optJSONArray("images"))
                if (id.isNotBlank()) {
                    add(
                        SpotifyCollection(
                            id = id,
                            name = name,
                            subtitle = artist.ifBlank { "Album" },
                            artworkUrl = art,
                            uri = a.optString("uri", "spotify:album:$id"),
                            isAlbum = true,
                        )
                    )
                }
            }
        }
    }

    suspend fun tracks(collection: SpotifyCollection): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        if (collection.isAlbum) {
            val root = get("https://api.spotify.com/v1/albums/${collection.id}/tracks?limit=50")
            val items = root.optJSONArray("items") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until items.length()) {
                    val t = items.optJSONObject(i) ?: continue
                    val id = t.optString("id")
                    if (id.isBlank()) continue
                    add(
                        SpotifyTrack(
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
            val root = get("https://api.spotify.com/v1/playlists/${collection.id}/tracks?limit=100")
            val items = root.optJSONArray("items") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until items.length()) {
                    val t = items.optJSONObject(i)?.optJSONObject("track") ?: continue
                    val id = t.optString("id")
                    if (id.isBlank()) continue
                    val albumObj = t.optJSONObject("album")
                    add(
                        SpotifyTrack(
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

    /**
     * Attempts to start playback directly via Spotify Connect Web API if an active device exists;
     * returns true if Web API accepted the playback command, false if caller should fall back to URI intent.
     */
    suspend fun startPlayback(contextUri: String, offsetIndex: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("https://api.spotify.com/v1/me/player/play").openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                doOutput = true
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
            }
            val payload = JSONObject().apply {
                put("context_uri", contextUri)
                put("offset", JSONObject().put("position", offsetIndex.coerceAtLeast(0)))
                put("position_ms", 0)
            }.toString()
            try {
                conn.outputStream.use { it.write(payload.toByteArray()) }
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }

    private fun get(url: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code == 401) throw SpotifyAuthExpired()
            if (code !in 200..299) throw IllegalStateException("Spotify HTTP $code")
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun firstImage(arr: org.json.JSONArray?): String? {
        if (arr == null || arr.length() == 0) return null
        return arr.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }
    }

    private fun firstArtist(arr: org.json.JSONArray?): String {
        if (arr == null || arr.length() == 0) return ""
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }.joinToString(", ")
    }

    companion object {
        const val REDIRECT_URI = "foldman://spotify-callback"
        private const val SCOPES = "playlist-read-private playlist-read-collaborative user-library-read user-read-playback-state user-modify-playback-state"

        fun generateCodeVerifier(): String {
            val bytes = ByteArray(48)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }

        fun codeChallenge(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }

        fun buildAuthUri(clientId: String, verifier: String): Uri {
            val challenge = codeChallenge(verifier)
            return Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
                .appendQueryParameter("client_id", clientId.trim())
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("scope", SCOPES)
                .build()
        }

        suspend fun exchangeCode(clientId: String, code: String, verifier: String): SpotifyTokens = withContext(Dispatchers.IO) {
            val body = listOf(
                "client_id" to clientId.trim(),
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT_URI,
                "code_verifier" to verifier,
            ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
            postToken(body)
        }

        suspend fun refreshAccessToken(clientId: String, refreshToken: String): SpotifyTokens = withContext(Dispatchers.IO) {
            val body = listOf(
                "client_id" to clientId.trim(),
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
            ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
            postToken(body, fallbackRefresh = refreshToken)
        }

        private fun postToken(formBody: String, fallbackRefresh: String? = null): SpotifyTokens {
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
                return SpotifyTokens(
                    accessToken = access,
                    refreshToken = refresh,
                    expiresAtMs = System.currentTimeMillis() + (expiresIn - 60) * 1000L,
                )
            } finally {
                conn.disconnect()
            }
        }
    }
}
