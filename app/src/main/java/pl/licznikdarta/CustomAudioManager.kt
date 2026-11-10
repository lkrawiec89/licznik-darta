package pl.licznikdarta

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class CustomSoundEntry(
    val score: Int,
    val fileName: String,
    val originalName: String = fileName
)

object CustomAudioManager {
    private const val CONFIG_FILE_NAME = "custom_sounds.json"
    private const val AUDIO_DIR_NAME = "custom_audio"
    private var activePlayer: MediaPlayer? = null

    private fun getAudioDir(context: Context): File {
        return File(context.filesDir, AUDIO_DIR_NAME).apply { mkdirs() }
    }

    private fun getConfigFile(context: Context): File {
        return File(context.filesDir, CONFIG_FILE_NAME)
    }

    fun loadCustomSounds(context: Context): Map<Int, CustomSoundEntry> {
        val file = getConfigFile(context)
        if (!file.exists()) return emptyMap()
        return try {
            val json = JSONObject(file.readText())
            val result = mutableMapOf<Int, CustomSoundEntry>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val score = key.toIntOrNull() ?: continue
                val obj = json.getJSONObject(key)
                val fileName = obj.optString("fileName", "")
                val originalName = obj.optString("originalName", fileName)
                if (fileName.isNotEmpty()) {
                    val audioFile = File(getAudioDir(context), fileName)
                    if (audioFile.exists() && audioFile.length() > 0) {
                        result[score] = CustomSoundEntry(score, fileName, originalName)
                    }
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveCustomSounds(context: Context, map: Map<Int, CustomSoundEntry>) {
        try {
            val json = JSONObject()
            map.forEach { (score, entry) ->
                val obj = JSONObject().apply {
                    put("fileName", entry.fileName)
                    put("originalName", entry.originalName)
                }
                json.put(score.toString(), obj)
            }
            getConfigFile(context).writeText(json.toString(2))
        } catch (_: Exception) {}
    }

    fun importSoundFromUri(context: Context, uri: Uri, score: Int): CustomSoundEntry? {
        try {
            var originalName = "audio_$score"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    originalName = cursor.getString(nameIndex) ?: originalName
                }
            }

            val extension = originalName.substringAfterLast('.', "mp3")
            val targetFileName = "score_${score}_${UUID.randomUUID().toString().take(8)}.$extension"
            val targetFile = File(getAudioDir(context), targetFileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            if (!targetFile.exists() || targetFile.length() == 0L) {
                targetFile.delete()
                return null
            }

            // Usuń ewentualny stary plik dla tego wyniku
            val currentMap = loadCustomSounds(context).toMutableMap()
            currentMap[score]?.let { old ->
                val oldFile = File(getAudioDir(context), old.fileName)
                if (oldFile.exists() && old.fileName != targetFileName) {
                    oldFile.delete()
                }
            }

            val entry = CustomSoundEntry(score, targetFileName, originalName)
            currentMap[score] = entry
            saveCustomSounds(context, currentMap)
            return entry
        } catch (_: Exception) {
            return null
        }
    }

    fun removeSound(context: Context, score: Int) {
        val currentMap = loadCustomSounds(context).toMutableMap()
        val removed = currentMap.remove(score)
        if (removed != null) {
            val file = File(getAudioDir(context), removed.fileName)
            if (file.exists()) {
                file.delete()
            }
            saveCustomSounds(context, currentMap)
        }
    }

    @Synchronized
    fun playSound(context: Context, fileName: String, onCompletion: (() -> Unit)? = null): Boolean {
        try {
            val file = File(getAudioDir(context), fileName)
            if (!file.exists() || file.length() == 0L) return false

            stopPlaying()

            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    it.release()
                    if (activePlayer == it) {
                        activePlayer = null
                    }
                    onCompletion?.invoke()
                }
                setOnErrorListener { _, _, _ ->
                    stopPlaying()
                    true
                }
                prepare()
                start()
            }
            activePlayer = mp
            return true
        } catch (_: Exception) {
            stopPlaying()
            return false
        }
    }

    fun playSoundForScore(context: Context, score: Int, onCompletion: (() -> Unit)? = null): Boolean {
        val map = loadCustomSounds(context)
        val entry = map[score] ?: return false
        return playSound(context, entry.fileName, onCompletion)
    }

    @Synchronized
    fun stopPlaying() {
        try {
            activePlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (_: Exception) {}
        activePlayer = null
    }

    fun exportToZip(context: Context, targetFile: File): Boolean {
        val map = loadCustomSounds(context)
        if (map.isEmpty()) return false

        return try {
            val configJson = JSONObject().apply {
                map.forEach { (score, entry) ->
                    put(score.toString(), JSONObject().apply {
                        put("fileName", entry.fileName)
                        put("originalName", entry.originalName)
                    })
                }
            }.toString(2)

            ZipOutputStream(FileOutputStream(targetFile)).use { zos ->
                // Zapisz konfigurację
                val configEntry = ZipEntry("sounds_config.json")
                zos.putNextEntry(configEntry)
                zos.write(configJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // Zapisz pliki audio
                val audioDir = getAudioDir(context)
                map.values.forEach { entry ->
                    val file = File(audioDir, entry.fileName)
                    if (file.exists() && file.length() > 0) {
                        zos.putNextEntry(ZipEntry("audio/${entry.fileName}"))
                        FileInputStream(file).use { fis ->
                            fis.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }
            }
            targetFile.exists() && targetFile.length() > 0
        } catch (_: Exception) {
            false
        }
    }

    fun importFromZip(context: Context, zipFile: File): Boolean {
        if (!zipFile.exists() || zipFile.length() == 0L) return false

        return try {
            var configContent: String? = null
            val audioDir = getAudioDir(context)

            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var ze: ZipEntry? = zis.nextEntry
                while (ze != null) {
                    val name = ze.name
                    if (name == "sounds_config.json") {
                        configContent = zis.bufferedReader(Charsets.UTF_8).readText()
                    } else if (name.startsWith("audio/") && !ze.isDirectory) {
                        val fileName = name.substringAfter("audio/")
                        if (fileName.isNotEmpty()) {
                            val target = File(audioDir, fileName)
                            FileOutputStream(target).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                    }
                    zis.closeEntry()
                    ze = zis.nextEntry
                }
            }

            if (configContent.isNullOrBlank()) return false

            val json = JSONObject(configContent!!)
            val currentMap = loadCustomSounds(context).toMutableMap()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val score = key.toIntOrNull() ?: continue
                val obj = json.getJSONObject(key)
                val fileName = obj.optString("fileName", "")
                val originalName = obj.optString("originalName", fileName)
                if (fileName.isNotEmpty()) {
                    val file = File(audioDir, fileName)
                    if (file.exists() && file.length() > 0) {
                        currentMap[score] = CustomSoundEntry(score, fileName, originalName)
                    }
                }
            }

            saveCustomSounds(context, currentMap)
            true
        } catch (_: Exception) {
            false
        }
    }
}
