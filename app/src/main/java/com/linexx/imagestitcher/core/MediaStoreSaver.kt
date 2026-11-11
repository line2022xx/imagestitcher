package com.linexx.imagestitcher.core

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * 把导出文件写入系统相册：Pictures/ImageStitcher/
 * Android 10+ 走 MediaStore，无需任何权限。
 */
object MediaStoreSaver {

    private const val ALBUM = "ImageStitcher"

    fun save(context: Context, file: File, displayName: String, mime: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, file, displayName, mime)
        } else {
            saveToPublicDir(context, file, displayName, mime)
        }
    }

    private fun saveViaMediaStore(context: Context, file: File, displayName: String, mime: String): Uri? {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + ALBUM)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = try {
            resolver.insert(collection, values)
        } catch (t: Throwable) {
            null
        } ?: return null

        try {
            val out = resolver.openOutputStream(uri) ?: return null
            out.use { target ->
                file.inputStream().use { source -> source.copyTo(target) }
            }
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }

        val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        runCatching { resolver.update(uri, done, null, null) }
        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveToPublicDir(context: Context, file: File, displayName: String, mime: String): Uri? {
        return try {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                ALBUM,
            )
            if (!dir.exists() && !dir.mkdirs()) return null
            val target = File(dir, displayName)
            file.copyTo(target, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
            Uri.fromFile(target)
        } catch (t: Throwable) {
            null
        }
    }
}
