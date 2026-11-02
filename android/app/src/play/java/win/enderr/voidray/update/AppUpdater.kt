package win.enderr.voidray.update

import android.content.Context
import java.io.File

/**
 * Google Play build: Play delivers the updates. The app never downloads or
 * installs APKs itself (Play's Device and Network Abuse policy), so this only
 * keeps the GitHub updater's shape for the shared code.
 */
object AppUpdater {
    class Release(val tag: String, val version: List<Int>, val apkUrl: String?, val page: String, val notes: String)

    fun parseVersion(text: String): List<Int> = listOf(0, 0, 0)

    fun isNewer(candidate: List<Int>, current: List<Int>): Boolean = false

    fun latest(currentVersion: String): Release? = null

    fun download(context: Context, url: String, progress: (Int) -> Unit): File =
        throw UnsupportedOperationException("Updates come from Google Play")

    fun install(context: Context, apk: File): Boolean = false
}
