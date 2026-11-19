package com.github.damontecres.wholphin.services.tvprovider

import android.content.ContentProvider
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import com.github.damontecres.wholphin.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class ImageProvider : ContentProvider() {
    override fun onCreate() = true

    override fun getType(uri: Uri) = null

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ) = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ) = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    private val okHttpClient = OkHttpClient()

    suspend fun loadBitmap(url: String): Bitmap? =
        withContext(Dispatchers.IO) {
            val call = okHttpClient.newCall(Request.Builder().url(url).build())
            coroutineContext.job.invokeOnCompletion { call.cancel() }

            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                BitmapFactory.decodeStream(response.body.byteStream())
            }
        }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor? {
        val src = requireNotNull(uri.getQueryParameter("src"))
        val (read, write) = ParcelFileDescriptor.createReliablePipe()

        scope.launch {
            try {
                val bitmap =
                    loadBitmap(src)
                        ?: throw IOException("Undecodable image")
                ParcelFileDescriptor.AutoCloseOutputStream(write).use { out ->
                    bitmap.compress(webpFormat, COMPRESSION_QUALITY, out)
                }
                bitmap.recycle()
            } catch (e: Throwable) {
                // Throwable on purpose: also catches OutOfMemoryError
                runCatching { write.closeWithError(e.message) }
            }
        }
        return read
    }

    @Suppress("DEPRECATION")
    private val webpFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }

    companion object {
        private const val COMPRESSION_QUALITY = 95

        /**
         * Get a [Uri] that uses the [ImageProvider] to load an image. The input should be a valid
         * Jellyfin image URL created using the SDK.
         */
        fun getImageUri(src: String): Uri =
            Uri
                .Builder()
                .scheme("content")
                .authority("${BuildConfig.APPLICATION_ID}.services.tvprovider.ImageProvider")
                .appendQueryParameter("src", src)
                .appendQueryParameter("v", BuildConfig.VERSION_NAME)
                .build()
    }
}
