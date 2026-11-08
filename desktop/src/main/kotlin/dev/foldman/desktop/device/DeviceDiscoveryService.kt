package dev.foldman.desktop.device

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

enum class DeviceType(val label: String, val icon: String) {
    MAC("This Mac", "🖥"),
    BLUETOOTH("Bluetooth", "ᛒ"),
    GOOGLE_CAST("Google Cast", "⎚"),
    AIRPLAY("AirPlay", "📡"),
}

data class AudioOutputDevice(
    val id: String,
    val name: String,
    val model: String? = null,
    val type: DeviceType,
    val description: String = "",
    val isCurrent: Boolean = false,
    val isConnecting: Boolean = false,
    val address: String? = null,
)

class DeviceDiscoveryService {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanJob: Job? = null

    private val defaultDevice = AudioOutputDevice(
        id = "local_default",
        name = "MacBook Air Speakers",
        model = "Built-in Audio",
        type = DeviceType.MAC,
        description = "This Computer",
        isCurrent = true,
    )

    private val _currentDevice = MutableStateFlow(defaultDevice)
    val currentDevice: StateFlow<AudioOutputDevice> = _currentDevice.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<AudioOutputDevice>>(listOf(defaultDevice))
    val availableDevices: StateFlow<List<AudioOutputDevice>> = _availableDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val deviceCache = ConcurrentHashMap<String, AudioOutputDevice>()

    init {
        deviceCache[defaultDevice.id] = defaultDevice
        scanAllDevices()
    }

    fun scanAllDevices() {
        if (_isScanning.value) return
        scanJob?.cancel()
        scanJob = scope.launch {
            _isScanning.value = true
            try {
                // 1. Scan JVM & Mac audio outputs (Built-in, Bluetooth, USB)
                scanLocalAudioDevices()

                // 2. Scan Google Cast devices via mDNS
                scanGoogleCastDevices()

                // 3. Scan AirPlay devices via mDNS
                scanAirPlayDevices()

                updateDeviceList()
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    private fun scanLocalAudioDevices() {
        runCatching {
            val mixers = AudioSystem.getMixerInfo()
            for (info in mixers) {
                val mixer = AudioSystem.getMixer(info)
                val lineInfos = mixer.sourceLineInfo
                // Only consider output-capable lines and skip raw microphone ports
                if (lineInfos.isNotEmpty() && !info.name.contains("Microphone", ignoreCase = true)) {
                    val isBluetooth = info.name.contains("Bluetooth", ignoreCase = true) ||
                            info.name.contains("AirPods", ignoreCase = true) ||
                            info.name.contains("WH-", ignoreCase = true) ||
                            info.name.contains("Beats", ignoreCase = true)

                    val cleanName = info.name
                        .removePrefix("Port ")
                        .trim()

                    if (cleanName.isNotBlank() && cleanName != "Default Audio Device") {
                        val id = "local_${cleanName.lowercase().replace(" ", "_")}"
                        val isCurr = _currentDevice.value.id == id ||
                                (_currentDevice.value.id == defaultDevice.id && cleanName.contains("Speaker", ignoreCase = true))

                        val dev = AudioOutputDevice(
                            id = id,
                            name = cleanName,
                            model = if (isBluetooth) "Bluetooth Audio" else "Built-in / CoreAudio",
                            type = if (isBluetooth) DeviceType.BLUETOOTH else DeviceType.MAC,
                            description = if (isBluetooth) "Paired Bluetooth Device" else "System Audio Output",
                            isCurrent = isCurr,
                        )
                        if (isCurr && id != defaultDevice.id) {
                            deviceCache.remove(defaultDevice.id)
                        }
                        deviceCache[id] = dev
                        if (isCurr) {
                            _currentDevice.value = dev
                        }
                    }
                }
            }
        }
    }

    private suspend fun scanGoogleCastDevices() {
        val lookupJobs = mutableListOf<Job>()
        val seenInstances = ConcurrentHashMap.newKeySet<String>()

        runCatching {
            val pb = ProcessBuilder("dns-sd", "-B", "_googlecast._tcp", "local")
            val proc = pb.start()
            val reader = BufferedReader(InputStreamReader(proc.inputStream))

            // Background killer after 1.2 seconds
            val timer = scope.launch {
                delay(1200)
                proc.destroyForcibly()
            }

            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: break
                    if (l.contains("Add") && l.contains("_googlecast._tcp")) {
                        val idx = l.indexOf("_googlecast._tcp.")
                        if (idx != -1) {
                            val instanceName = l.substring(idx + "_googlecast._tcp.".length).trim()
                            if (instanceName.isNotBlank() && seenInstances.add(instanceName)) {
                                val cleanName = formatCastName(instanceName)
                                val id = "cast_$instanceName"
                                val isCurr = _currentDevice.value.id == id

                                val dev = AudioOutputDevice(
                                    id = id,
                                    name = cleanName,
                                    model = "Google Cast / Nest",
                                    type = DeviceType.GOOGLE_CAST,
                                    description = "Google Cast Speaker / Display",
                                    isCurrent = isCurr,
                                )
                                deviceCache[id] = dev
                                updateDeviceList()

                                // Launch concurrent lookup for friendly room name (fn=) and device model (md=)
                                lookupJobs += scope.launch {
                                    resolveCastDetails(instanceName, id)
                                }
                            }
                        }
                    }
                }
            } finally {
                timer.cancel()
                proc.destroyForcibly()
            }
        }

        // Wait up to 1000ms for parallel lookups to complete so UI shows real names immediately
        if (lookupJobs.isNotEmpty()) {
            withTimeoutOrNull(1000) {
                lookupJobs.joinAll()
            }
        }
    }

    private fun resolveCastDetails(instanceName: String, id: String) {
        runCatching {
            val pb = ProcessBuilder("dns-sd", "-L", instanceName, "_googlecast._tcp", "local")
            val proc = pb.start()
            val reader = BufferedReader(InputStreamReader(proc.inputStream))

            val timer = scope.launch {
                delay(600)
                proc.destroyForcibly()
            }

            var friendlyName: String? = null
            var modelName: String? = null

            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: break
                    if (l.contains("fn=")) {
                        val fn = parseTxtAttribute(l, "fn")
                        if (!fn.isNullOrBlank()) friendlyName = fn
                    }
                    if (l.contains("md=")) {
                        val md = parseTxtAttribute(l, "md")
                        if (!md.isNullOrBlank()) modelName = md
                    }
                    if (friendlyName != null && modelName != null) {
                        break
                    }
                }
            } finally {
                timer.cancel()
                proc.destroyForcibly()
            }

            if (friendlyName != null) {
                val currentDev = deviceCache[id]
                if (currentDev != null) {
                    val updatedDev = currentDev.copy(
                        name = friendlyName,
                        model = modelName ?: currentDev.model,
                        description = if (modelName != null) "$modelName · Google Cast" else "Google Cast Speaker / Display"
                    )
                    deviceCache[id] = updatedDev
                    if (_currentDevice.value.id == id) {
                        _currentDevice.value = updatedDev
                    }
                    updateDeviceList()
                }
            }
        }
    }

    private fun parseTxtAttribute(line: String, attr: String): String? {
        val regex = Regex("""(?:^|\s)$attr=((?:\\.|[^\s])+)""")
        val match = regex.find(line) ?: return null
        val rawValue = match.groupValues.getOrNull(1) ?: return null
        return rawValue.replace(Regex("""\\(.)""")) { it.groupValues[1] }.trim()
    }

    private fun scanAirPlayDevices() {
        val seenInstances = ConcurrentHashMap.newKeySet<String>()
        runCatching {
            val pb = ProcessBuilder("dns-sd", "-B", "_airplay._tcp", "local")
            val proc = pb.start()
            val reader = BufferedReader(InputStreamReader(proc.inputStream))

            val timer = scope.launch {
                delay(1200)
                proc.destroyForcibly()
            }

            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: break
                    if (l.contains("Add") && l.contains("_airplay._tcp")) {
                        // Extract instance name from line (last token or multi-word)
                        val idx = l.indexOf("_airplay._tcp.")
                        if (idx != -1) {
                            val raw = l.substring(idx + "_airplay._tcp.".length).trim()
                            if (raw.isNotBlank() && seenInstances.add(raw)) {
                                val id = "airplay_${raw.lowercase().replace(" ", "_")}"
                                val isCurr = _currentDevice.value.id == id
                                val dev = AudioOutputDevice(
                                    id = id,
                                    name = raw,
                                    model = "AirPlay 2",
                                    type = DeviceType.AIRPLAY,
                                    description = "Apple AirPlay Device",
                                    isCurrent = isCurr,
                                )
                                deviceCache[id] = dev
                            }
                        }
                    }
                }
            } finally {
                timer.cancel()
                proc.destroyForcibly()
            }
        }
    }

    private fun formatCastName(raw: String): String {
        // e.g. "Google-Nest-Hub-46f3976659b660d14a813235f1fee952" -> "Google Nest Hub"
        // e.g. "Nest-Wifi-point-ec67..." -> "Nest Wifi Point"
        // e.g. "Google-TV-Streamer-..." -> "Google TV Streamer"
        val parts = raw.split("-")
        val cleanParts = parts.filter { p ->
            p.length < 20 && !p.matches(Regex("^[0-9a-fA-F]{16,}$"))
        }
        val joined = cleanParts.joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        return joined.ifBlank { raw }
    }

    private fun updateDeviceList() {
        val currId = _currentDevice.value.id
        val list = deviceCache.values.map { dev ->
            dev.copy(isCurrent = dev.id == currId)
        }.sortedWith(
            compareBy<AudioOutputDevice> {
                when (it.type) {
                    DeviceType.MAC -> 0
                    DeviceType.BLUETOOTH -> 1
                    DeviceType.GOOGLE_CAST -> 2
                    DeviceType.AIRPLAY -> 3
                }
            }.thenBy { it.name }
        )
        _availableDevices.value = list
    }

    fun selectDevice(device: AudioOutputDevice) {
        scope.launch {
            // Set connecting state
            _currentDevice.value = device.copy(isCurrent = true, isConnecting = true)
            updateDeviceList()

            // Connect delay simulation / verification
            delay(400)

            _currentDevice.value = device.copy(isCurrent = true, isConnecting = false)
            deviceCache[device.id] = device.copy(isCurrent = true)
            updateDeviceList()
        }
    }
}
