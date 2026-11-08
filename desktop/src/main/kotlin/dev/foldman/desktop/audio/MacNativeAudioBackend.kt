package dev.foldman.desktop.audio

import dev.foldman.audio.AudioBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class MacNativeAudioBackend : AudioBackend {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    private var process: Process? = null
    private var writer: OutputStreamWriter? = null
    private var readJob: Job? = null

    init {
        ensureProcess()
    }

    private fun ensureBinary(): File? {
        val home = System.getProperty("user.home")
        val binDir = File(home, ".tapeman/bin").apply { mkdirs() }
        val target = File(binDir, "tapeman_player")

        // 1. Extract from classpath resource (ensures packaged binary is up-to-date)
        runCatching {
            val resStream = Thread.currentThread().contextClassLoader.getResourceAsStream("bin/tapeman_player")
                ?: this::class.java.classLoader.getResourceAsStream("bin/tapeman_player")
            if (resStream != null) {
                resStream.use { input ->
                    Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                target.setExecutable(true, false)
                return target
            }
        }

        // 2. Check local file in user home cache if executable
        if (target.exists() && target.canExecute()) {
            return target
        }

        // 3. Check source tree if exists and executable
        val devBin = File("desktop/src/main/resources/bin/tapeman_player")
        if (devBin.exists() && devBin.canExecute()) {
            return devBin
        }

        return null
    }

    @Synchronized
    private fun ensureProcess(): Boolean {
        if (process?.isAlive == true && writer != null) return true

        val bin = ensureBinary() ?: return false
        return try {
            val pb = ProcessBuilder(bin.absolutePath)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            process = proc
            writer = OutputStreamWriter(proc.outputStream, Charsets.UTF_8)

            readJob?.cancel()
            readJob = scope.launch {
                val reader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8))
                var line: String? = null
                while (isActive && reader.readLine().also { line = it } != null) {
                    val l = line ?: break
                    handleOutput(l.trim())
                }
            }
            true
        } catch (t: Throwable) {
            t.printStackTrace()
            false
        }
    }

    private fun handleOutput(line: String) {
        if (line.startsWith("PROGRESS ")) {
            val parts = line.split(" ")
            if (parts.size >= 4) {
                val cur = parts[1].toLongOrNull() ?: 0L
                val dur = parts[2].toLongOrNull() ?: 0L
                val playing = parts[3] == "1"
                _currentPositionMs.value = cur
                if (dur > 0L) _durationMs.value = dur
                _isPlaying.value = playing
            }
        } else if (line.startsWith("READY ")) {
            val dur = line.removePrefix("READY ").trim().toLongOrNull() ?: 0L
            if (dur > 0L) _durationMs.value = dur
        } else if (line.startsWith("STATE ")) {
            when (line.removePrefix("STATE ").trim()) {
                "PLAYING" -> _isPlaying.value = true
                "PAUSED", "STOPPED" -> _isPlaying.value = false
            }
        } else if (line == "ENDED") {
            _isPlaying.value = false
            _currentPositionMs.value = _durationMs.value
        }
    }

    private fun send(cmd: String) {
        scope.launch {
            synchronized(this@MacNativeAudioBackend) {
                if (ensureProcess()) {
                    try {
                        writer?.write("$cmd\n")
                        writer?.flush()
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
            }
        }
    }

    private var lastUrl: String? = null

    override fun play(url: String, headers: Map<String, String>) {
        lastUrl = url
        send("PLAY $url")
        _isPlaying.value = true
    }

    override fun pause() {
        send("PAUSE")
        _isPlaying.value = false
    }

    override fun resume() {
        val url = lastUrl
        if (_durationMs.value == 0L && !url.isNullOrBlank()) {
            send("PLAY $url")
        } else {
            send("RESUME")
        }
        _isPlaying.value = true
    }

    override fun stop() {
        send("STOP")
        _isPlaying.value = false
        _currentPositionMs.value = 0L
    }

    override fun seekTo(positionMs: Long) {
        _currentPositionMs.value = positionMs
        send("SEEK $positionMs")
    }

    override fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _volume.value = clamped
        send("VOLUME $clamped")
    }

    override fun release() {
        send("QUIT")
        readJob?.cancel()
        process?.destroyForcibly()
        process = null
        writer = null
    }
}
