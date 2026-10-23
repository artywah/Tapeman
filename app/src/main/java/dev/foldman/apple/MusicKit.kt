package dev.foldman.apple

import android.content.Context
import android.content.Intent
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Bridge to Apple's MusicKit for Android SDK, reached by reflection.
 *
 * Apple ships the SDK as two AARs you download with a developer account (they aren't on Maven),
 * so Foldman always builds without them. Drop `musickitauth-release-*.aar` and
 * `mediaplayback-release-*.aar` into app/libs and rebuild: sign-in and playlist browsing light up
 * with the first, in-app streaming with the second. Every call is guarded, so a missing class or a
 * changed SDK leaves Apple Music on media-session control instead of crashing.
 */
object MusicKit {
    private const val AUTH_FACTORY = "com.apple.android.sdk.authentication.AuthenticationFactory"
    private const val AUTH_MANAGER = "com.apple.android.sdk.authentication.AuthenticationManager"
    private const val AUTH_BUILDER = "com.apple.android.sdk.authentication.AuthIntentBuilder"
    private const val TOKEN_RESULT = "com.apple.android.sdk.authentication.TokenResult"
    private const val TOKEN_PROVIDER = "com.apple.android.sdk.authentication.TokenProvider"
    private const val CONTROLLER_FACTORY = "com.apple.android.music.playback.controller.MediaPlayerControllerFactory"
    internal const val CONTROLLER = "com.apple.android.music.playback.controller.MediaPlayerController"

    internal fun cls(name: String): Class<*>? = runCatching { Class.forName(name) }.getOrNull()

    val hasAuth: Boolean get() = cls(AUTH_FACTORY) != null
    val hasPlayback: Boolean get() = cls(CONTROLLER_FACTORY) != null

    private fun manager(context: Context): Any =
        cls(AUTH_FACTORY)!!.getMethod("createAuthenticationManager", Context::class.java).invoke(null, context)!!

    /** The intent that shows Apple's sign-in (handled by the Apple Music app), or null without the SDK. */
    fun signInIntent(context: Context, developerToken: String): Intent? = runCatching {
        val m = cls(AUTH_MANAGER)!!
        val b = cls(AUTH_BUILDER)!!
        var builder: Any = m.getMethod("createIntentBuilder", String::class.java).invoke(manager(context), developerToken)!!
        builder = b.getMethod("setHideStartScreen", Boolean::class.javaPrimitiveType).invoke(builder, false) ?: builder
        builder = b.getMethod("setStartScreenMessage", String::class.java)
            .invoke(builder, "Connect Apple Music to play your playlists on the Tapeman deck.") ?: builder
        b.getMethod("build").invoke(builder) as Intent
    }.getOrNull()

    /** The Music User Token from the sign-in result, or the SDK's error name (e.g. USER_CANCELLED). */
    fun readSignInResult(context: Context, data: Intent?): Result<String> = runCatching {
        val r = cls(TOKEN_RESULT)!!
        val result = cls(AUTH_MANAGER)!!.getMethod("handleTokenResult", Intent::class.java).invoke(manager(context), data)!!
        if (r.getMethod("isError").invoke(result) as Boolean) {
            val err = r.getMethod("getError").invoke(result)
            throw IllegalStateException((err as? Enum<*>)?.name ?: err.toString())
        }
        r.getMethod("getMusicUserToken").invoke(result) as String
    }

    /** An in-app Apple Music player, or null when the playback AAR isn't in the build. */
    fun createPlayer(context: Context, developerToken: String, userToken: () -> String): KitPlayer? = runCatching {
        // The player's native code isn't loaded for you (Apple's sample app does this too).
        System.loadLibrary("c++_shared")
        System.loadLibrary("appleMusicSDK")
        val providerCls = cls(TOKEN_PROVIDER)!!
        val provider = Proxy.newProxyInstance(providerCls.classLoader, arrayOf(providerCls)) { proxy, method, args ->
            when (method.name) {
                "getDeveloperToken" -> developerToken
                "getUserToken" -> userToken()
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "FoldmanTokenProvider"
                else -> null
            }
        }
        val create = cls(CONTROLLER_FACTORY)!!.methods.first {
            it.name == "createLocalController" && it.parameterTypes.size == 2
        }
        KitPlayer(create.invoke(null, context.applicationContext, provider)!!)
    }.getOrNull()
}

data class KitItem(
    val queueId: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val artworkUrl: String?,
    val durationMs: Long,
)

/** Thin reflective wrapper over MusicKit's MediaPlayerController. */
class KitPlayer internal constructor(private val target: Any) {
    private val c = MusicKit.cls(MusicKit.CONTROLLER)!!
    private val methods = HashMap<String, Method>()
    private fun m(name: String, vararg types: Class<*>?): Method =
        methods.getOrPut(name + types.size) { c.getMethod(name, *types) }
    private fun call(name: String): Any? = m(name).invoke(target)

    private val statePlaying = constant("com.apple.android.music.playback.model.PlaybackState", "PLAYING")
    private val songType = constant("com.apple.android.music.playback.model.MediaItemType", "SONG")
    private val shuffleOff = constant("com.apple.android.music.playback.model.PlaybackShuffleMode", "SHUFFLE_MODE_OFF")

    private fun constant(cls: String, field: String): Int =
        runCatching { MusicKit.cls(cls)!!.getField(field).getInt(null) }.getOrDefault(Int.MIN_VALUE)

    fun play() { call("play") }
    fun pause() { call("pause") }
    fun stop() { call("stop") }
    fun release() { call("release") }
    fun seekTo(ms: Long) { m("seekToPosition", Long::class.javaPrimitiveType).invoke(target, ms) }
    fun skipToNext() { call("skipToNextItem") }
    fun skipToPrevious() { call("skipToPreviousItem") }
    fun skipTo(queueId: Long) { m("skipToQueueItemWithId", Long::class.javaPrimitiveType).invoke(target, queueId) }

    val isPlaying: Boolean get() = (call("getPlaybackState") as? Number)?.toInt() == statePlaying
    val isBuffering: Boolean get() = call("isBuffering") as? Boolean ?: false
    val positionMs: Long get() = (call("getCurrentPosition") as? Number)?.toLong()?.coerceAtLeast(0) ?: 0L
    val durationMs: Long get() = (call("getDuration") as? Number)?.toLong()?.coerceAtLeast(0) ?: 0L
    val canSkipNext: Boolean get() = call("canSkipToNextItem") as? Boolean ?: false
    val canSkipPrevious: Boolean get() = call("canSkipToPreviousItem") as? Boolean ?: false
    val shuffleMode: Int get() = (call("getShuffleMode") as? Number)?.toInt() ?: -1
    val current: KitItem? get() = call("getCurrentItem")?.let(::item)
    /** Where the playing song sits in [queue]; -1 when nothing is loaded. */
    val queueIndex: Int get() = (call("getPlaybackQueueIndex") as? Number)?.toInt() ?: -1
    val queue: List<KitItem> get() = (call("getQueueItems") as? List<*>).orEmpty().mapNotNull { it?.let(::item) }

    /** Turns shuffle off if the player has it on; true if it had to. */
    fun ensureShuffleOff(): Boolean {
        if (shuffleOff == Int.MIN_VALUE || shuffleMode == shuffleOff || call("canSetShuffleMode") != true) return false
        m("setShuffleMode", Int::class.javaPrimitiveType).invoke(target, shuffleOff)
        return true
    }

    /** Loads catalog songs as the play queue at [startIndex], playing at once unless [play] is false. */
    fun playSongs(catalogIds: List<String>, startIndex: Int, play: Boolean = true) {
        val builderCls = MusicKit.cls("com.apple.android.music.playback.queue.CatalogPlaybackQueueItemProvider\$Builder")!!
        var b: Any = builderCls.getConstructor().newInstance()
        b = builderCls.getMethod("items", Int::class.javaPrimitiveType, Array<String>::class.java)
            .invoke(b, songType, catalogIds.toTypedArray()) ?: b
        b = builderCls.getMethod("startItemIndex", Int::class.javaPrimitiveType).invoke(b, startIndex) ?: b
        // Playlists play in order. Say so explicitly rather than inherit a shuffle setting.
        if (shuffleOff != Int.MIN_VALUE) {
            b = builderCls.getMethod("shuffleMode", Int::class.javaPrimitiveType).invoke(b, shuffleOff) ?: b
        }
        val provider = builderCls.getMethod("build").invoke(b)
        val prepare = c.methods.first {
            it.name == "prepare" && it.parameterTypes.size == 2 && it.parameterTypes[1] == Boolean::class.javaPrimitiveType
        }
        prepare.invoke(target, provider, play)
        ensureShuffleOff()
    }

    private fun item(queueItem: Any): KitItem? = runCatching {
        val qi = MusicKit.cls("com.apple.android.music.playback.model.PlayerQueueItem")!!
        val pmi = MusicKit.cls("com.apple.android.music.playback.model.PlayerMediaItem")!!
        val media = qi.getMethod("getItem").invoke(queueItem)
        fun s(name: String) = media?.let { pmi.getMethod(name).invoke(it) as? String }
        KitItem(
            queueId = (qi.getMethod("getPlaybackQueueId").invoke(queueItem) as Number).toLong(),
            title = s("getTitle"),
            artist = s("getArtistName"),
            album = s("getAlbumTitle"),
            artworkUrl = media?.let {
                pmi.getMethod("getArtworkUrl", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(it, 600, 600) as? String
            },
            durationMs = (media?.let { pmi.getMethod("getDuration").invoke(it) } as? Number)?.toLong() ?: 0L,
        )
    }.getOrNull()
}
