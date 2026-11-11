package com.linexx.imagestitcher.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.linexx.imagestitcher.model.ImageItem

/**
 * 图片探测与解码。
 * 关键点：
 * 1. 先只读边界拿尺寸，避免整图解码；
 * 2. 读取 EXIF 方向，保证竖拍照片不会横躺；
 * 3. 解码时按目标尺寸设置 inSampleSize，控制单张图内存。
 */
object ImageLoader {

    /** 读取 uri 的显示尺寸与旋转角度，失败返回 null */
    fun probe(context: Context, uri: Uri): ImageItem? {
        val bounds = readBounds(context, uri) ?: return null
        val rotation = readRotation(context, uri)
        val swapped = rotation == 90 || rotation == 270
        val displayWidth = if (swapped) bounds.second else bounds.first
        val displayHeight = if (swapped) bounds.first else bounds.second
        if (displayWidth <= 0 || displayHeight <= 0) return null
        return ImageItem(uri = uri, width = displayWidth, height = displayHeight, rotation = rotation)
    }

    private fun readBounds(context: Context, uri: Uri): Pair<Int, Int>? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // 注意：inJustDecodeBounds 模式下 decodeStream 恒定返回 null，
            // 不能把它的返回值当作"读取成功"的依据，否则会误判为失败。
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            stream.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
            val w = options.outWidth
            val h = options.outHeight
            if (w <= 0 || h <= 0) null else w to h
        } catch (t: Throwable) {
            null
        }
    }

    fun readRotation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (t: Throwable) {
            0
        }
    }

    /**
     * 解码并按目标矩形做采样与旋转校正。
     * @param targetWidth 目标显示宽度（旋转后）
     * @param targetHeight 目标显示高度（旋转后）
     * @param rotation 需要应用的顺时针旋转角度
     */
    fun decodeScaled(
        context: Context,
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int,
        rotation: Int,
    ): Bitmap? {
        val raw = readBounds(context, uri) ?: return null
        val rawWidth = raw.first
        val rawHeight = raw.second

        // 旋转 90/270 时源图的宽高与目标宽高是交换关系
        val swapped = rotation == 90 || rotation == 270
        val compareWidth = if (swapped) targetHeight else targetWidth
        val compareHeight = if (swapped) targetWidth else targetHeight

        var sample = 1
        while (rawWidth / (sample * 2) >= compareWidth && rawHeight / (sample * 2) >= compareHeight) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }

        var bitmap = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        } catch (t: Throwable) {
            null
        } ?: return null

        if (rotation != 0) {
            try {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated !== bitmap) {
                    bitmap.recycle()
                }
                bitmap = rotated
            } catch (t: Throwable) {
                // 旋转失败时保留原图，最多方向不对，不影响流程
            }
        }
        return bitmap
    }
}
