package com.github.damontecres.wholphin.services

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.Version
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.headersContentLength
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class UpdateChecker
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:StandardOkHttpClient private val okHttpClient: OkHttpClient,
    ) {
        companion object {
            // TODO apk names
            private const val ASSET_NAME = "Wholphin"
            private const val APK_NAME = "$ASSET_NAME.apk"

            private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

            private const val PERMISSION_REQUEST_CODE = 123456

            private val NOTE_REGEX = Regex("<!-- app-note:(.+) -->")

            val ACTIVE = true
        }

        suspend fun maybeShowUpdateToast(
            updateUrl: String,
            showNegativeToast: Boolean = false,
        ) {
            val pref = PreferenceManager.getDefaultSharedPreferences(context)
            val now = Date()
            val lastUpdateCheckThreshold =
                pref
                    .getLong(context.getString(R.string.pref_key_update_last_check_threshold), 12)
                    .hours
            val lastUpdateCheck =
                pref.getLong(
                    context.getString(R.string.pref_key_update_last_check),
                    0,
                )
            val timeSince = (now.time - lastUpdateCheck).milliseconds
            Timber.v("Last successful update check was $timeSince ago")
            val installedVersion = getInstalledVersion()
            val latestRelease = getLatestRelease(updateUrl)
            if (latestRelease != null && latestRelease.version.isGreaterThan(installedVersion)) {
                Timber.v("Update available $installedVersion => ${latestRelease.version}")
                pref.edit {
                    putLong(context.getString(R.string.pref_key_update_last_check), now.time)
                }
                if (lastUpdateCheckThreshold >= timeSince) {
                    Timber.i(
                        "Skipping update notification, threshold is $lastUpdateCheckThreshold",
                    )
                } else {
                    showToast(
                        context,
                        "Update available: $installedVersion => ${latestRelease.version}!",
                        Toast.LENGTH_LONG,
                    )
                }
            } else {
                Timber.v("No update available for $installedVersion")
                if (showNegativeToast) {
                    showToast(
                        context,
                        "No updates available, $installedVersion is the latest!",
                        Toast.LENGTH_LONG,
                    )
                }
            }
        }

        fun getInstalledVersion(): Version {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            return Version.Companion.fromString(pkgInfo.versionName!!)
        }

        suspend fun getLatestRelease(updateUrl: String): Release? {
            return withContext(Dispatchers.IO) {
                val preferRelease =
                    PreferenceManager
                        .getDefaultSharedPreferences(context)
                        .getBoolean("updatePreferRelease", true)

                val request =
                    Request
                        .Builder()
                        .url(updateUrl)
                        .get()
                        .build()
                okHttpClient.newCall(request).execute().use {
                    if (it.isSuccessful && it.body != null) {
                        val result = Json.parseToJsonElement(it.body!!.string())
                        val name = result.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                        val version = Version.tryFromString(name)
                        val publishedAt =
                            result.jsonObject["published_at"]?.jsonPrimitive?.contentOrNull
                        val body = result.jsonObject["body"]?.jsonPrimitive?.contentOrNull
                        val downloadUrl =
                            result.jsonObject["assets"]
                                ?.jsonArray
                                ?.let { assets -> getDownloadUrl(assets, preferRelease) }
                        Timber.v("version=$version, downloadUrl=$downloadUrl")
                        if (version != null) {
                            val notes =
                                if (body.isNotNullOrBlank()) {
                                    NOTE_REGEX
                                        .findAll(body)
                                        .map { m ->
                                            m.groupValues[1]
                                        }.toList()
                                } else {
                                    listOf()
                                }
                            return@use Release(version, downloadUrl, publishedAt, body, notes)
                        } else {
                            Timber.w("Update version parsing failed. name=$name")
                        }
                    } else {
                        Timber.w("Update check failed: ${it.message}")
                    }
                    return@use null
                }
            }
        }

        private fun getDownloadUrl(
            assets: JsonArray,
            preferRelease: Boolean,
        ): String? {
            val abiSuffix = Build.SUPPORTED_ABIS.firstOrNull().let { if (it != null) "-$it" else "" }
            val releaseSuffix = if (preferRelease) "-release" else "-debug"
            val preferredNames =
                listOf(
                    "$ASSET_NAME${releaseSuffix}$abiSuffix.apk",
                    "$ASSET_NAME$releaseSuffix.apk",
                    "$ASSET_NAME.apk",
                )
            var preferredAsset: JsonObject? = null
            outer@ for (name in preferredNames) {
                for (asset in assets) {
                    val assetName =
                        asset.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                    if (name == assetName) {
                        preferredAsset = asset.jsonObject
                        break@outer
                    }
                }
            }
            return preferredAsset
                ?.get("browser_download_url")
                ?.jsonPrimitive
                ?.contentOrNull
        }

        suspend fun installRelease(
            release: Release,
            callback: DownloadCallback,
        ) {
            withContext(Dispatchers.IO) {
                cleanup()
                val request =
                    Request
                        .Builder()
                        .url(release.downloadUrl!!)
                        .get()
                        .build()
                okHttpClient.newCall(request).execute().use {
                    if (it.isSuccessful && it.body != null) {
                        Timber.v("Request successful for ${release.downloadUrl}")
                        withContext(Dispatchers.Main) {
                            callback.contentLength(it.headersContentLength())
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val contentValues =
                                ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, APK_NAME)
                                    put(MediaStore.MediaColumns.MIME_TYPE, APK_MIME_TYPE)
                                    put(
                                        MediaStore.MediaColumns.RELATIVE_PATH,
                                        Environment.DIRECTORY_DOWNLOADS,
                                    )
                                }
                            val resolver = context.contentResolver
                            val uri =
                                resolver.insert(
                                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                    contentValues,
                                )
                            if (uri != null) {
                                it.body!!.byteStream().use { input ->
                                    resolver.openOutputStream(uri).use { output ->
                                        copyTo(input, output!!, callback = callback)
                                    }
                                }

                                val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                intent.data = uri
                                context.startActivity(intent)
                            } else {
                                Timber.e("Resolver URI is null, trying fallback")
//                                showToast(context, "Unable to download the apk")
                                val targetFile = fallbackDownload(it, callback)
                                val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                intent.data =
                                    FileProvider.getUriForFile(
                                        context,
                                        context.packageName + ".provider",
                                        targetFile,
                                    )
                                context.startActivity(intent)
                            }
                        } else {
                            val targetFile = fallbackDownload(it, callback)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                intent.data =
                                    FileProvider.getUriForFile(
                                        context,
                                        context.packageName + ".provider",
                                        targetFile,
                                    )
                                context.startActivity(intent)
                            } else {
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.setDataAndType(Uri.fromFile(targetFile), APK_MIME_TYPE)
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        }
                    } else {
                        Timber.v("Request failed for ${release.downloadUrl}: ${it.code}")
                        showToast(context, "Error downloading the apk: ${it.code}")
                    }
                }
            }
        }

        private suspend fun fallbackDownload(
            response: Response,
            callback: DownloadCallback,
        ): File {
            val downloadDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadDir.mkdirs()
            val targetFile = File(downloadDir, APK_NAME)
            targetFile.outputStream().use { output ->
                response.body!!.byteStream().use { input ->
                    copyTo(input, output, callback = callback)
                }
            }
            return targetFile
        }

        fun hasPermissions(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED

        /**
         * Delete previously downloaded APKs
         */
        fun cleanup() {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver
                        .query(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            arrayOf(
                                MediaStore.MediaColumns._ID,
                                MediaStore.Files.FileColumns.DISPLAY_NAME,
                            ),
                            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.MIME_TYPE} = ?",
                            arrayOf(context.getString(R.string.app_name) + "%", APK_MIME_TYPE),
                            null,
                        )?.use { cursor ->
                            while (cursor.moveToNext()) {
                                val id = cursor.getString(0)
                                val displayName = cursor.getString(1)
                                Timber.v("id=$id, displayName=$displayName")
                            }
                        }
                    val deletedRows =
                        context.contentResolver.delete(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.MIME_TYPE} = ?",
                            arrayOf(context.getString(R.string.app_name) + "%", APK_MIME_TYPE),
                        )
                    Timber.i("Deleted $deletedRows rows")
                } else {
                    val downloadDir =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val targetFile = File(downloadDir, APK_NAME)
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Exception during cleanup")
            }
        }
    }

@Serializable
data class Release(
    val version: Version,
    val downloadUrl: String?,
    val publishedAt: String?,
    val body: String?,
    val notes: List<String>,
)

interface DownloadCallback {
    fun contentLength(contentLength: Long)

    fun bytesDownloaded(bytes: Long)
}

suspend fun copyTo(
    input: InputStream,
    out: OutputStream,
    bufferSize: Int = 16 * 1024,
    callback: DownloadCallback,
): Long {
    var bytesCopied: Long = 0
    val buffer = ByteArray(bufferSize)
    var bytes = input.read(buffer)
    while (bytes >= 0) {
        out.write(buffer, 0, bytes)
        bytesCopied += bytes
        withContext(Dispatchers.Main) {
            callback.bytesDownloaded(bytesCopied)
        }
        bytes = input.read(buffer)
    }
    return bytesCopied
}
