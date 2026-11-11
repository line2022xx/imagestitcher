package com.linexx.imagestitcher.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.linexx.imagestitcher.model.ImageItem
import com.linexx.imagestitcher.model.OutputFormat
import java.io.File
import java.io.FileOutputStream

/**
 * 拼接渲染器。
 *
 * 内存策略：只创建一张目标画布，源图按目标矩形采样后逐张绘制并立即回收，
 * 峰值内存约为「画布 + 单张源图」，与图片总数无关。
 */
class Stitcher(private val context: Context) {

    /**
     * @param onProgress 进度回调 0f~1f，会在 IO 线程被调用
     * @return 是否成功
     */
    fun render(
        items: List<ImageItem>,
        plan: StitchPlan,
        background: Int,
        format: OutputFormat,
        quality: Int,
        output: File,
        onProgress: (Float) -> Unit,
    ): Boolean {
        if (plan.canvasWidth <= 0 || plan.canvasHeight <= 0) return false

        val canvasBitmap: Bitmap = try {
            Bitmap.createBitmap(plan.canvasWidth, plan.canvasHeight, Bitmap.Config.ARGB_8888)
        } catch (t: Throwable) {
            return false
        }

        try {
            val canvas = Canvas(canvasBitmap)
            canvas.drawColor(background)

            val paint = Paint().apply {
                isFilterBitmap = true
                isAntiAlias = true
                isDither = true
            }

            val total = plan.placements.size.coerceAtLeast(1)
            plan.placements.forEachIndexed { order, placement ->
                val item = items.getOrNull(placement.index)
                if (item != null && placement.width > 0 && placement.height > 0) {
                    val bitmap = try {
                        ImageLoader.decodeScaled(
                            context = context,
                            uri = item.uri,
                            targetWidth = placement.width,
                            targetHeight = placement.height,
                            rotation = item.rotation,
                        )
                    } catch (t: Throwable) {
                        null
                    }
                    if (bitmap != null) {
                        try {
                            canvas.drawBitmap(
                                bitmap,
                                null as Rect?,
                                Rect(placement.left, placement.top, placement.right, placement.bottom),
                                paint,
                            )
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
                onProgress((order + 1).toFloat() / total)
            }

            val compressFormat =
                if (format == OutputFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG

            output.parentFile?.mkdirs()
            return FileOutputStream(output).use { stream ->
                canvasBitmap.compress(compressFormat, quality.coerceIn(1, 100), stream)
                stream.flush()
                true
            }
        } catch (t: Throwable) {
            return false
        } finally {
            if (!canvasBitmap.isRecycled) canvasBitmap.recycle()
        }
    }
}
