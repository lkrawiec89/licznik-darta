package pl.licznikdarta

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String = "",
    val mandatory: Boolean = false
)

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class Available(val info: UpdateInfo) : UpdateStatus()
    object UpToDate : UpdateStatus()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateStatus()
    data class ReadyToInstall(val apkFile: File, val info: UpdateInfo) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

object UpdateManager {
    const val DEFAULT_UPDATE_URL = "https://api.github.com/repos/lkrawiec89/licznik-darta/releases/latest"

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    suspend fun checkUpdate(url: String, currentVersionCode: Int = BuildConfig.VERSION_CODE): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = url.trim()
            if (cleanUrl.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Adres URL aktualizacji jest pusty"))
            }
            val conn = (URL(cleanUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "LicznikDarta/${BuildConfig.VERSION_NAME}")
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("Błąd serwera: HTTP $responseCode"))
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val info = if (json.has("tag_name") && json.has("assets")) {
                parseGitHubRelease(json)
            } else {
                parseCustomUpdateJson(json)
            }

            if (info == null) {
                Result.failure(Exception("Nie znaleziono informacji o wydaniu lub pliku APK w odpowiedzi"))
            } else if (info.versionCode > currentVersionCode) {
                Result.success(info)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun parseCustomUpdateJson(json: JSONObject): UpdateInfo? {
        val code = json.optInt("versionCode", -1)
        val name = json.optString("versionName", "")
        val apkUrl = json.optString("apkUrl", "").ifEmpty { json.optString("downloadUrl", "") }
        val notes = json.optString("releaseNotes", "")
        val mandatory = json.optBoolean("mandatory", false)

        if (code <= 0 || apkUrl.isEmpty()) return null
        return UpdateInfo(
            versionCode = code,
            versionName = name.ifEmpty { "v$code" },
            apkUrl = apkUrl,
            releaseNotes = notes,
            mandatory = mandatory
        )
    }

    internal fun parseGitHubRelease(json: JSONObject): UpdateInfo? {
        val tagName = json.optString("tag_name", "")
        val body = json.optString("body", "")
        val assets = json.optJSONArray("assets") ?: return null

        var apkDownloadUrl = ""
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val assetName = asset.optString("name", "")
            if (assetName.endsWith(".apk", ignoreCase = true)) {
                apkDownloadUrl = asset.optString("browser_download_url", "")
                break
            }
        }
        if (apkDownloadUrl.isEmpty()) return null

        val releaseName = json.optString("name", "")
        val combinedText = "$releaseName\n$body"
        val buildMatch = Regex("""(?i)(?:build|code|versionCode)\s*[:=\s]\s*(\d+)""").find(combinedText)
        val parsedCode = buildMatch?.groupValues?.get(1)?.toIntOrNull()
            ?: parseVersionCodeFromTag(tagName)

        return UpdateInfo(
            versionCode = parsedCode,
            versionName = tagName.removePrefix("v"),
            apkUrl = apkDownloadUrl,
            releaseNotes = body
        )
    }

    internal fun parseVersionCodeFromTag(tag: String): Int {
        val clean = tag.removePrefix("v").trim()
        val parts = clean.split(".")
        return try {
            if (parts.size >= 3) {
                parts[0].toInt() * 10000 + parts[1].toInt() * 100 + parts[2].toInt()
            } else {
                clean.filter { it.isDigit() }.toIntOrNull() ?: 0
            }
        } catch (_: Exception) { 0 }
    }

    suspend fun downloadApk(
        context: Context,
        info: UpdateInfo,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            updatesDir.listFiles()?.forEach { if (it.isFile && it.name.endsWith(".apk")) it.delete() }

            val targetFile = File(updatesDir, "LicznikDarta-${info.versionName}.apk")

            var currentUrl = info.apkUrl
            var redirects = 0
            while (redirects < 5) {
                val conn = URL(currentUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "LicznikDarta/${BuildConfig.VERSION_NAME}")

                val status = conn.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == 307 || status == 308
                ) {
                    val newUrl = conn.getHeaderField("Location")
                    conn.disconnect()
                    currentUrl = newUrl
                    redirects++
                } else if (status in 200..299) {
                    connection = conn
                    break
                } else {
                    return@withContext Result.failure(Exception("Błąd pobierania pliku: HTTP $status"))
                }
            }

            val conn = connection ?: return@withContext Result.failure(Exception("Zbyt wiele przekierowań"))
            val totalBytes = conn.contentLengthLong

            conn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalDownloaded = 0L
                    var lastReported = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReported > 80 || totalDownloaded == totalBytes) {
                            lastReported = now
                            val progress = if (totalBytes > 0) totalDownloaded.toFloat() / totalBytes.toFloat() else -1f
                            onProgress(progress, totalDownloaded, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            if (targetFile.length() < 1000) {
                targetFile.delete()
                return@withContext Result.failure(Exception("Pobrany plik jest uszkodzony lub pusty"))
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    fun installApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) return false

        if (!canInstallPackages(context)) {
            openInstallPermissionSettings(context)
            return false
        }

        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}

@Composable
fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Available(info)) }
    var progress by remember { mutableStateOf(-1f) }
    var downloadedBytes by remember { mutableStateOf(0L) }
    var totalBytes by remember { mutableStateOf(0L) }

    fun startDownload() {
        status = UpdateStatus.Downloading(0f, 0L, 0L)
        scope.launch {
            val res = UpdateManager.downloadApk(context, info) { p, down, tot ->
                progress = p
                downloadedBytes = down
                totalBytes = tot
            }
            if (res.isSuccess) {
                val file = res.getOrThrow()
                status = UpdateStatus.ReadyToInstall(file, info)
                // Próba automatycznego wywołania instalacji
                UpdateManager.installApk(context, file)
            } else {
                status = UpdateStatus.Error(res.exceptionOrNull()?.message ?: "Nieznany błąd pobierania")
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!info.mandatory && status !is UpdateStatus.Downloading) {
                onDismiss()
            }
        },
        containerColor = DartCardBg,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = DartGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Dostępna aktualizacja",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Nowa wersja: v${info.versionName} (build ${info.versionCode})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DartGreen
                )
                Text(
                    text = "Obecna wersja: v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    fontSize = 12.sp,
                    color = DartSubtle,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (info.releaseNotes.isNotBlank()) {
                    Text(
                        text = "Co nowego:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Surface(
                        color = Color(0xFF1E1E20),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = info.releaseNotes,
                            fontSize = 12.sp,
                            color = DartSubtle,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                when (val currentStatus = status) {
                    is UpdateStatus.Downloading -> {
                        Spacer(Modifier.height(8.dp))
                        if (progress >= 0f) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = DartGreen,
                                trackColor = Color(0xFF333333)
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = DartGreen,
                                trackColor = Color(0xFF333333)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        val mbDown = String.format("%.1f", downloadedBytes / (1024f * 1024f))
                        val mbTotal = if (totalBytes > 0) String.format("%.1f MB", totalBytes / (1024f * 1024f)) else "..."
                        val percentStr = if (progress >= 0f) "${(progress * 100).toInt()}%" else "Pobieranie..."
                        Text(
                            text = "$percentStr ($mbDown MB / $mbTotal)",
                            fontSize = 12.sp,
                            color = DartSubtle
                        )
                    }
                    is UpdateStatus.ReadyToInstall -> {
                        Surface(
                            color = Color(0xFF1A3320),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "✓ Plik aktualizacji został pobrany. Kliknij poniżej, aby zainstalować.",
                                fontSize = 12.sp,
                                color = DartGreen,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    is UpdateStatus.Error -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = DartRed, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Błąd: ${currentStatus.message}",
                                fontSize = 12.sp,
                                color = DartRed
                            )
                        }
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            when (val currentStatus = status) {
                is UpdateStatus.Available -> {
                    Button(
                        onClick = { startDownload() },
                        colors = ButtonDefaults.buttonColors(containerColor = DartGreen)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pobierz i zainstaluj", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                is UpdateStatus.ReadyToInstall -> {
                    Button(
                        onClick = { UpdateManager.installApk(context, currentStatus.apkFile) },
                        colors = ButtonDefaults.buttonColors(containerColor = DartGreen)
                    ) {
                        Text("Zainstaluj", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                is UpdateStatus.Error -> {
                    Button(
                        onClick = { startDownload() },
                        colors = ButtonDefaults.buttonColors(containerColor = DartGreen)
                    ) {
                        Text("Spróbuj ponownie", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                is UpdateStatus.Downloading -> {
                    // Blokada podczas pobierania
                }
                else -> {}
            }
        },
        dismissButton = {
            if (!info.mandatory && status !is UpdateStatus.Downloading) {
                TextButton(onClick = onDismiss) {
                    Text("Później", color = DartSubtle)
                }
            }
        }
    )
}
