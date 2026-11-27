package dev.foldman.desktop.local

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldman.deck.LocalTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

object DesktopLocalLibrary {
    private val configFile = File(System.getProperty("user.home"), ".tapeman/local_folder.txt")
    private val audioExts = setOf("mp3", "flac", "m4a", "aac", "wav", "ogg", "opus", "aiff", "alac")

    fun loadSavedFolder(): File? = runCatching {
        if (!configFile.exists()) return null
        val path = configFile.readText().trim()
        File(path).takeIf { it.exists() && it.isDirectory }
    }.getOrNull()

    fun saveFolder(dir: File) {
        runCatching {
            configFile.parentFile?.mkdirs()
            configFile.writeText(dir.absolutePath)
        }
    }

    fun pickFolder(): File? {
        val os = System.getProperty("os.name").orEmpty()
        if (os.contains("Mac", ignoreCase = true)) {
            System.setProperty("apple.awt.fileDialogForDirectories", "true")
            try {
                val fd = FileDialog(null as Frame?, "Choose Music Folder", FileDialog.LOAD)
                fd.isVisible = true
                val dir = fd.directory ?: return null
                val file = fd.file ?: return File(dir)
                val chosen = File(dir, file)
                return if (chosen.isDirectory) chosen else File(dir)
            } finally {
                System.setProperty("apple.awt.fileDialogForDirectories", "false")
            }
        } else {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = "Choose Music Folder"
            }
            return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile
            } else null
        }
    }

    suspend fun scanFolder(root: File): List<LocalTrack> = withContext(Dispatchers.IO) {
        if (!root.exists() || !root.isDirectory) return@withContext emptyList()
        val files = mutableListOf<File>()
        fun walk(dir: File, depth: Int) {
            if (depth > 4) return
            val children = dir.listFiles() ?: return
            for (c in children.sortedBy { it.name.lowercase() }) {
                if (c.name.startsWith(".")) continue
                if (c.isDirectory) {
                    walk(c, depth + 1)
                } else if (c.extension.lowercase() in audioExts) {
                    files.add(c)
                }
            }
        }
        walk(root, 0)

        files.map { f ->
            val parentName = f.parentFile?.name?.takeIf { f.parentFile != root } ?: root.name
            val grandParentName = f.parentFile?.parentFile?.takeIf {
                f.parentFile != root && f.parentFile?.parentFile != root
            }?.name.orEmpty()
            val rawName = f.nameWithoutExtension
                .replace(Regex("^\\d{1,3}\\s*[-.]\\s*"), "")
                .trim()
            LocalTrack(
                uri = f.toURI().toString(),
                title = rawName.ifBlank { f.nameWithoutExtension },
                artist = grandParentName.ifBlank { "Local Audio" },
                album = parentName,
                durationMs = 0L,
                coverUri = null,
                folder = parentName,
            )
        }
    }
}

@Composable
fun DesktopLocalBrowser(
    activeFolder: File?,
    tracks: List<LocalTrack>,
    activeTrackUri: String?,
    onPickFolder: () -> Unit,
    onTrackSelected: (LocalTrack, List<LocalTrack>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredTracks = remember(tracks, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) tracks
        else tracks.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.album.contains(q, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF101114))
            .border(1.dp, Color(0xFF23252C), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Header row with folder picker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activeFolder?.name ?: "No Folder Selected",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (activeFolder != null) "${tracks.size} audio files · ${activeFolder.absolutePath}" else "Choose a folder with MP3, FLAC, M4A, WAV or OGG files",
                    color = Color(0xFF888B94),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFE5A00D))
                    .clickable(onClick = onPickFolder)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (activeFolder == null) "Choose Folder" else "Change Folder",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (tracks.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            // Search box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF181A20))
                    .border(1.dp, Color(0xFF282B34), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                if (searchQuery.isEmpty()) {
                    Text("Filter local tracks, folders...", color = Color(0xFF606470), fontSize = 11.5.sp)
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 11.5.sp),
                    cursorBrush = SolidColor(Color(0xFFE5A00D)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (activeFolder == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📁", fontSize = 32.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Play Local Audio Files", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Select a folder on your Mac to load tracks onto the tape deck.", color = Color(0xFF888B94), fontSize = 11.5.sp)
                }
            }
        } else if (filteredTracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (tracks.isEmpty()) "No supported audio files found in this folder." else "No tracks match \"$searchQuery\".",
                    color = Color(0xFF888B94),
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(filteredTracks) { idx, track ->
                    val isCurrent = activeTrackUri != null && track.uri == activeTrackUri
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isCurrent) Color(0xFF232118) else Color(0xFF191B20))
                            .then(
                                if (isCurrent) Modifier.border(1.dp, Color(0xFFE5A00D).copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                else Modifier
                            )
                            .clickable {
                                val origIdx = tracks.indexOfFirst { it.uri == track.uri }.coerceAtLeast(idx)
                                onTrackSelected(track, tracks, origIdx)
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isCurrent) "▶" else "%02d".format(idx + 1),
                                color = Color(0xFFE5A00D),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(28.dp)
                            )
                            Column {
                                Text(
                                    text = track.title,
                                    color = if (isCurrent) Color(0xFFE5A00D) else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = listOf(track.artist, track.folder).filter { it.isNotBlank() }.joinToString(" · "),
                                    color = Color(0xFF70747E),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
