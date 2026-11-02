package win.enderr.voidray.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Checks the project's GitHub releases and installs the newer APK. */
object AppUpdater {
    const val REPO = "EnderMythex/VOID-RAY"

    class Release(val tag: String, val version: List<Int>, val apkUrl: String?, val page: String, val notes: String)

    fun parseVersion(text: String): List<Int> =
        text.trimStart('v', 'V').split('.', '-').take(3).map { it.toIntOrNull() ?: 0 }.let { it + List(3 - it.size) { 0 } }

    fun isNewer(candidate: List<Int>, current: List<Int>): Boolean {
        for (i in 0 until 3) {
            if (candidate[i] != current[i]) return candidate[i] > current[i]
        }
        return false
    }

    /** arm64 build for almost every recent phone, universal otherwise. */
    private fun preferredAssets(): List<String> {
        val abis = Build.SUPPORTED_ABIS.toList()
        val specific = when {
            "arm64-v8a" in abis -> "VoidRay-android-arm64-v8a.apk"
            "armeabi-v7a" in abis -> "VoidRay-android-armeabi-v7a.apk"
            "x86_64" in abis -> "VoidRay-android-x86_64.apk"
            else -> null
        }
        return listOfNotNull(specific, "VoidRay-android-universal.apk")
    }

    fun latest(currentVersion: String): Release? {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "VoidRay-Android/$currentVersion")
            if (conn.responseCode !in 200..299) return null
            val o = JSONObject(conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
            val tag = o.getString("tag_name")
            val assets = o.optJSONArray("assets")
            val byName = (0 until (assets?.length() ?: 0)).associate {
                val a = assets!!.getJSONObject(it)
                a.getString("name") to a.getString("browser_download_url")
            }
            val url = preferredAssets().firstNotNullOfOrNull { byName[it] }
            return Release(tag, parseVersion(tag), url, o.optString("html_url", "https://github.com/$REPO/releases/latest"), o.optString("body"))
        } finally {
            conn.disconnect()
        }
    }

    fun download(context: Context, url: String, progress: (Int) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "VoidRay-update.apk")
        var conn = URL(url).openConnection() as HttpURLConnection
        // GitHub answers with a redirect to its CDN.
        var redirects = 0
        while (conn.responseCode in 300..399 && redirects++ < 5) {
            val next = conn.getHeaderField("Location")
            conn.disconnect()
            conn = URL(next).openConnection() as HttpURLConnection
        }
        try {
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastPercent) { lastPercent = percent; progress(percent) }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return file
    }

    /** True when the system installer was opened; false when the user must first allow installs. */
    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }
}
