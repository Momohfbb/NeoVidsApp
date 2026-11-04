package com.moustayt.cloudvideo.generator

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import com.moustayt.cloudvideo.generator.data.LibraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Reads an image from [uri], downsizes it and returns a data: URI the backend can forward to providers. */
suspend fun encodeImage(context: Context, uri: Uri, maxSide: Int = 1024): String? = withContext(Dispatchers.IO) {
    runCatching {
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 88, bos)
        "data:image/jpeg;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
}

private class Downloaded(val file: File, val mime: String)

private suspend fun fetch(context: Context, item: LibraryItem): Downloaded? = withContext(Dispatchers.IO) {
    val url = item.resultUrl ?: return@withContext null
    runCatching {
        if (url.startsWith("file:")) {
            val f = File(Uri.parse(url).path!!)
            return@runCatching Downloaded(f, "video/mp4")
        }
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        val mime = (conn.contentType ?: if (item.kind.isVideo) "video/mp4" else "image/jpeg").substringBefore(';').trim()
        val ext = when {
            mime.contains("mp4") -> "mp4"
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.startsWith("video") -> "mp4"
            else -> "jpg"
        }
        val dir = File(context.externalCacheDir ?: context.cacheDir, "downloads").apply { mkdirs() }
        val f = File(dir, "neovids_${item.id.hashCode().toUInt()}.$ext")
        conn.inputStream.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        Downloaded(f, mime)
    }.getOrNull()
}

suspend fun shareItem(context: Context, item: LibraryItem): Boolean {
    val d = fetch(context, item) ?: return false
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", d.file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = d.mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return true
}

suspend fun saveToGallery(context: Context, item: LibraryItem): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val d = fetch(context, item) ?: return false
    return withContext(Dispatchers.IO) {
        runCatching {
            val video = d.mime.startsWith("video")
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, d.file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, d.mime)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/NeoVids AI",
                )
            }
            val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val target = context.contentResolver.insert(collection, values)!!
            context.contentResolver.openOutputStream(target)!!.use { o -> d.file.inputStream().use { it.copyTo(o) } }
            true
        }.getOrDefault(false)
    }
}
