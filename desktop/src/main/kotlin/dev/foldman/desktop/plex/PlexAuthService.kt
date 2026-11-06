package dev.foldman.desktop.plex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.awt.Desktop
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID

data class PlexPin(
    val id: Long,
    val code: String,
    val authUrl: String,
    val clientIdentifier: String,
)

data class PlexServerConnection(
    val uri: String,
    val isLocal: Boolean,
    val isRelay: Boolean,
)

data class PlexServer(
    val name: String,
    val clientIdentifier: String,
    val accessToken: String,
    val connections: List<PlexServerConnection>,
)

data class PlexSavedConfig(
    val clientIdentifier: String,
    val authToken: String,
    val serverName: String,
    val serverUrl: String,
    val serverToken: String,
    val libraryName: String,
    val librarySectionId: String? = null,
    val keySounds: Boolean = true,
)

class PlexAuthService {
    private val configFile: File by lazy {
        val home = System.getProperty("user.home")
        val dir = File(home, ".tapeman")
        if (!dir.exists()) dir.mkdirs()
        File(dir, "plex_config.json")
    }

    private val clientIdFile: File by lazy {
        val home = System.getProperty("user.home")
        val dir = File(home, ".tapeman")
        if (!dir.exists()) dir.mkdirs()
        File(dir, "client_id")
    }

    fun getClientIdentifier(): String {
        if (clientIdFile.exists()) {
            val id = clientIdFile.readText().trim()
            if (id.isNotBlank()) return id
        }
        val newId = "tapeman-desktop-${UUID.randomUUID()}"
        runCatching { clientIdFile.writeText(newId) }
        return newId
    }

    /**
     * Creates a new Plex PIN for browser-based authorization.
     */
    suspend fun requestPin(): PlexPin = withContext(Dispatchers.IO) {
        val clientId = getClientIdentifier()
        val url = URL("https://plex.tv/api/v2/pins?strong=true")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Plex-Product", "Tapeman")
            setRequestProperty("X-Plex-Version", "1.0.0")
            setRequestProperty("X-Plex-Client-Identifier", clientId)
            setRequestProperty("X-Plex-Device", "Mac")
            setRequestProperty("X-Plex-Platform", "macOS")
            doOutput = true
        }

        val json = try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } finally {
            conn.disconnect()
        }

        val pinId = json.getLong("id")
        val code = json.getString("code")
        val authUrl = "https://app.plex.tv/auth#?clientID=$clientId&code=$code&context[device][product]=Tapeman&context[device][platform]=macOS"

        PlexPin(
            id = pinId,
            code = code,
            authUrl = authUrl,
            clientIdentifier = clientId,
        )
    }

    /**
     * Opens the user's default browser to authorize the PIN.
     */
    fun openAuthUrl(authUrl: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(authUrl))
            }
        }
    }

    /**
     * Polls plex.tv until the user authorizes the PIN, returning the user authToken.
     */
    suspend fun pollForToken(
        pinId: Long,
        timeoutSeconds: Int = 300,
        onPollTick: ((elapsedSec: Int) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        val clientId = getClientIdentifier()
        val startTime = System.currentTimeMillis()
        var elapsed = 0

        while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000L) {
            delay(1500)
            elapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            onPollTick?.invoke(elapsed)

            val url = URL("https://plex.tv/api/v2/pins/$pinId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 10000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Plex-Client-Identifier", clientId)
            }

            try {
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val token = json.optString("authToken", "")
                    if (token.isNotBlank()) {
                        return@withContext token
                    }
                }
            } catch (e: Exception) {
                // Continue polling
            } finally {
                conn.disconnect()
            }
        }
        null
    }

    /**
     * Discovers all Plex servers associated with this user account.
     */
    suspend fun fetchServers(authToken: String): List<PlexServer> = withContext(Dispatchers.IO) {
        val clientId = getClientIdentifier()
        val url = URL("https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Plex-Token", authToken)
            setRequestProperty("X-Plex-Client-Identifier", clientId)
        }

        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            val servers = mutableListOf<PlexServer>()

            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val provides = item.optString("provides", "")
                if (!provides.contains("server")) continue

                val name = item.optString("name", "Unnamed Server")
                val serverId = item.optString("clientIdentifier", "")
                val accessToken = item.optString("accessToken", authToken)
                val connsArr = item.optJSONArray("connections")
                val connections = mutableListOf<PlexServerConnection>()

                if (connsArr != null) {
                    for (j in 0 until connsArr.length()) {
                        val c = connsArr.getJSONObject(j)
                        val uri = c.optString("uri", "")
                        if (uri.isBlank()) continue
                        connections.add(
                            PlexServerConnection(
                                uri = uri.trimEnd('/'),
                                isLocal = c.optBoolean("local", false),
                                isRelay = c.optBoolean("relay", false),
                            )
                        )
                    }
                }

                if (connections.isNotEmpty()) {
                    // Sort connections: local non-relay first, then remote non-relay, then relay
                    val sortedConns = connections.sortedWith(
                        compareByDescending<PlexServerConnection> { it.isLocal }
                            .thenBy { it.isRelay }
                    )
                    servers.add(
                        PlexServer(
                            name = name,
                            clientIdentifier = serverId,
                            accessToken = accessToken,
                            connections = sortedConns,
                        )
                    )
                }
            }
            servers
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Tests connections to find the fastest/working connection for a server.
     */
    suspend fun testConnection(server: PlexServer): String? = withContext(Dispatchers.IO) {
        for (conn in server.connections) {
            val working = pingServer(conn.uri, server.accessToken)
            if (working) return@withContext conn.uri
        }
        server.connections.firstOrNull()?.uri
    }

    suspend fun pingServer(baseUrl: String, token: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("${baseUrl.trimEnd('/')}/identity")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Plex-Token", token)
            }
            val ok = conn.responseCode in 200..299
            conn.disconnect()
            ok
        }.getOrDefault(false)
    }

    /**
     * Lists music library sections available on the server.
     */
    suspend fun fetchMusicSections(serverUrl: String, token: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val url = URL("${serverUrl.trimEnd('/')}/library/sections")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 12000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Plex-Token", token)
        }

        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
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
        } finally {
            conn.disconnect()
        }
    }

    fun saveConfig(config: PlexSavedConfig) {
        runCatching {
            val json = JSONObject().apply {
                put("clientIdentifier", config.clientIdentifier)
                put("authToken", config.authToken)
                put("serverName", config.serverName)
                put("serverUrl", config.serverUrl)
                put("serverToken", config.serverToken)
                put("libraryName", config.libraryName)
                put("librarySectionId", config.librarySectionId ?: "")
                put("keySounds", config.keySounds)
            }
            configFile.writeText(json.toString(2))
        }
    }

    fun loadConfig(): PlexSavedConfig? {
        if (!configFile.exists()) return null
        return runCatching {
            val text = configFile.readText().trim()
            if (text.isBlank()) return null
            val json = JSONObject(text)
            PlexSavedConfig(
                clientIdentifier = json.optString("clientIdentifier", getClientIdentifier()),
                authToken = json.optString("authToken", ""),
                serverName = json.optString("serverName", ""),
                serverUrl = json.optString("serverUrl", ""),
                serverToken = json.optString("serverToken", ""),
                libraryName = json.optString("libraryName", "Music"),
                librarySectionId = json.optString("librarySectionId", "").ifBlank { null },
                keySounds = json.optBoolean("keySounds", true),
            )
        }.getOrNull()
    }

    fun clearConfig() {
        runCatching {
            if (configFile.exists()) configFile.delete()
        }
    }
}
