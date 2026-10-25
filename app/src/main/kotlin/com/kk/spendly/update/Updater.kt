package com.kk.spendly.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A published GitHub release that carries an installable APK. */
data class ReleaseInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    /** Lower-case hex SHA-256 published by GitHub for the asset, when available. */
    val sha256: String?,
) {
    /** First real sentence of the release notes: skips Markdown headings and strips list bullets. */
    val highlight: String?
        get() = notes.lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.trimStart('-', '*', '•', ' ') }
            .firstOrNull { it.isNotEmpty() }
}

/**
 * Checks GitHub Releases for a newer build and installs it.
 *
 * The only network traffic is a plain HTTPS request to GitHub; no identifiers or app data are sent. Android itself refuses
 * to install an update that isn't signed with the same key as the installed app, so a tampered APK can't replace Spendly.
 */
object Updater {
    /** GitHub "owner/repo" whose releases are checked. */
    const val REPO = "Karthikjl/spendly"

    private const val USER_AGENT = "Spendly-Android"

    fun installedVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    /** True when [remote] is a higher dotted version than [local] ("v1.10.0" > "1.9.2"). Pre-release suffixes are ignored. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = numbers(remote)
        val l = numbers(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun numbers(version: String): List<Int> =
        version.trim().removePrefix("v").takeWhile { it.isDigit() || it == '.' }.split('.').mapNotNull { it.toIntOrNull() }

    /** Reads GitHub's "latest release" JSON. Drafts, pre-releases, releases without an APK and non-HTTPS links are rejected. */
    fun parseRelease(json: String): ReleaseInfo? {
        try {
            val release = JSONObject(json)
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
            val tag = release.getString("tag_name")
            val assets = release.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (!asset.getString("name").endsWith(".apk", ignoreCase = true)) continue
                val url = asset.getString("browser_download_url")
                if (!url.startsWith("https://")) return null
                val digest = asset.optString("digest").removePrefix("sha256:").lowercase()
                return ReleaseInfo(
                    version = tag.trim().removePrefix("v"),
                    notes = release.optString("body").trim(),
                    apkUrl = url,
                    sizeBytes = asset.optLong("size"),
                    sha256 = digest.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } },
                )
            }
            return null
        } catch (_: JSONException) {
            return null
        }
    }

    /** The newest published release, or null when offline, rate-limited, or nothing is published yet. */
    suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val connection = open("https://api.github.com/repos/$REPO/releases/latest", 10_000, 10_000)
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) null
                else parseRelease(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        } catch (_: IOException) {
            null
        }
    }

    /** Downloads the APK into the app cache, reporting progress 0..1, and verifies its SHA-256 when GitHub published one. */
    suspend fun download(context: Context, info: ReleaseInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val target = File(dir, "spendly-${info.version}.apk")
        val connection = open(info.apkUrl, 15_000, 30_000)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Server returned ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (info.sha256 != null && actual != info.sha256) {
                target.delete()
                throw IOException("The download was corrupted. Please try again.")
            }
        } finally {
            connection.disconnect()
        }
        target
    }

    /** Hands the APK to Android's installer; the system shows its own confirmation screen. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName) }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("spendly.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val result = PendingIntent.getBroadcast(
                context, id,
                Intent(context, InstallReceiver::class.java).setPackage(context.packageName),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            session.commit(result.intentSender)
        }
    }

    private fun open(url: String, connectMs: Int, readMs: Int): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectMs
            readTimeout = readMs
            setRequestProperty("User-Agent", USER_AGENT)
        }
}
