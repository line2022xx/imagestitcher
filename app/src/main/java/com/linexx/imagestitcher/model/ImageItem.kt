package com.linexx.imagestitcher.model

import android.net.Uri
import com.linexx.imagestitcher.core.CrossAlign
import com.linexx.imagestitcher.core.SizeMode
import com.linexx.imagestitcher.core.StitchDirection
import com.linexx.imagestitcher.core.StitchOptions

/** 一张待拼接的图片。width/height 为已按 EXIF 校正旋转后的显示尺寸。 */
data class ImageItem(
    val uri: Uri,
    val width: Int,
    val height: Int,
    val rotation: Int,
)

/** 输出格式 */
enum class OutputFormat(val mime: String, val extension: String) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
}

/** 用户可配置项 */
data class StitchConfig(
    val direction: StitchDirection = StitchDirection.VERTICAL,
    val sizeMode: SizeMode = SizeMode.ORIGINAL,
    val gap: Int = 0,
    val background: Int = 0xFFFFFFFF.toInt(),
    val crossAlign: CrossAlign = CrossAlign.CENTER,
    val format: OutputFormat = OutputFormat.JPEG,
    val quality: Int = 92,
) {
    val transparent: Boolean get() = (background ushr 24) and 0xFF < 255

    /** 透明背景无法写入 JPEG，自动改用 PNG */
    val effectiveFormat: OutputFormat get() = if (transparent) OutputFormat.PNG else format

    fun toOptions(): StitchOptions = StitchOptions(
        direction = direction,
        sizeMode = sizeMode,
        gap = gap,
        crossAlign = crossAlign,
    )
}

/** 一次导出的产物 */
data class StitchResult(
    val file: java.io.File,
    val width: Int,
    val height: Int,
    val mime: String,
    val sizeBytes: Long,
)
