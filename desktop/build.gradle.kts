plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")
    implementation("org.json:json:20240303")

    // Standalone desktop audio playback via JavaFX Media
    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()
    val jfxClassifier = when {
        osName.contains("mac") && (osArch.contains("aarch64") || osArch.contains("arm")) -> "mac-aarch64"
        osName.contains("mac") -> "mac"
        osName.contains("win") -> "win"
        else -> "linux"
    }
    implementation("org.openjfx:javafx-media:21.0.2:$jfxClassifier")
    implementation("org.openjfx:javafx-graphics:21.0.2:$jfxClassifier")
    implementation("org.openjfx:javafx-base:21.0.2:$jfxClassifier")

    // Google Cast V2 client
    implementation("de.sfuhrm:chromecast-java-api-v2:0.12.13")
}

compose.desktop {
    application {
        mainClass = "dev.foldman.desktop.MainKt"
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi
            )
            packageName = "Tapeman"
            packageVersion = "1.0.0"
            appResourcesRootDir.set(project.file("src/main/packaging"))
            macOS {
                bundleID = "dev.foldman.desktop"
                iconFile.set(project.file("src/main/resources/icons/Tapeman.icns"))
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSLocalNetworkUsageDescription</key>
                        <string>Tapeman needs local network access to discover and stream audio to Google Cast and AirPlay devices.</string>
                        <key>NSBonjourServices</key>
                        <array>
                            <string>_googlecast._tcp</string>
                            <string>_airplay._tcp</string>
                            <string>_raop._tcp</string>
                        </array>
                    """.trimIndent()
                }
            }
        }
    }
}

tasks.matching { it.name.startsWith("packageDmg") }.configureEach {
    doLast {
        val dmgFile = file("build/compose/binaries/main/dmg/Tapeman-1.0.0.dmg")
        val iconFile = file("src/main/resources/icons/Tapeman.icns")
        val scriptFile = file("src/main/resources/bin/patch_dmg_icon.py")
        if (dmgFile.exists() && iconFile.exists() && scriptFile.exists()) {
            println("[packageDmg] Patching volume icon on ${dmgFile.name}...")
            val pb = ProcessBuilder("python3", scriptFile.absolutePath, dmgFile.absolutePath, iconFile.absolutePath)
            pb.inheritIO()
            val exitCode = pb.start().waitFor()
            println("[packageDmg] Patching finished with code $exitCode")
        }
    }
}
