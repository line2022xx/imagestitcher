package com.linexx.imagestitcher.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 拼接方向 */
enum class StitchDirection { VERTICAL, HORIZONTAL }

/**
 * 尺寸模式
 * - ORIGINAL：直接拼接，每张图保持原始像素尺寸
 * - FIT_MAX：拼接至最大宽度（横向时为最大高度），其余图等比放大到该基准
 * - FIT_MIN：拼接至最小宽度（横向时为最小高度），其余图等比缩小到该基准
 */
enum class SizeMode { ORIGINAL, FIT_MAX, FIT_MIN }

/** 交叉轴对齐方式（纵向拼接时作用于水平方向，横向拼接时作用于垂直方向） */
enum class CrossAlign { START, CENTER, END }

/** 仅包含尺寸的输入，便于纯逻辑单元测试 */
data class SourceSize(val width: Int, val height: Int)

/** 拼接参数（纯数据，不依赖 Android） */
data class StitchOptions(
    val direction: StitchDirection = StitchDirection.VERTICAL,
    val sizeMode: SizeMode = SizeMode.ORIGINAL,
    /** 相邻图片之间的间距，单位为输出像素 */
    val gap: Int = 0,
    val crossAlign: CrossAlign = CrossAlign.CENTER,
)

/** 单张图片在画布上的目标矩形 */
data class Placement(
    val index: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * 拼接方案
 * @param rawWidth 未做内存降采样时的理想宽度
 * @param rawHeight 未做内存降采样时的理想高度
 * @param scale 实际缩放系数（memory 保护导致），1.0 表示无缩放
 */
data class StitchPlan(
    val canvasWidth: Int,
    val canvasHeight: Int,
    val placements: List<Placement>,
    val rawWidth: Int,
    val rawHeight: Int,
    val scale: Float,
) {
    val downscaled: Boolean get() = scale < 0.999f
    val megapixels: Float get() = canvasWidth.toFloat() * canvasHeight.toFloat() / 1_000_000f
}

/**
 * 拼接布局计算器：只负责算坐标，不碰 Bitmap，可被单元测试完全覆盖。
 */
object StitchPlanner {

    /** Android Bitmap / JPEG 编码的安全边长上限 */
    const val MAX_EDGE = 30000

    fun plan(
        sources: List<SourceSize>,
        options: StitchOptions,
        budgetPixels: Long,
        maxEdge: Int = MAX_EDGE,
    ): StitchPlan {
        require(sources.isNotEmpty()) { "sources must not be empty" }

        val vertical = options.direction == StitchDirection.VERTICAL
        val crossOf: (SourceSize) -> Int = if (vertical) { s -> s.width } else { s -> s.height }
        val mainOf: (SourceSize) -> Int = if (vertical) { s -> s.height } else { s -> s.width }

        // 基准交叉轴尺寸
        val crossTarget = when (options.sizeMode) {
            SizeMode.FIT_MIN -> sources.minOf(crossOf).toDouble()
            else -> sources.maxOf(crossOf).toDouble()
        }.coerceAtLeast(1.0)

        val raw = layout(sources, options, crossTarget, 1.0, vertical, crossOf, mainOf)
        val scale = computeScale(raw.canvasW, raw.canvasH, budgetPixels, maxEdge)
        val finalLayout =
            if (scale >= 0.999f) raw
            else layout(sources, options, crossTarget, scale.toDouble(), vertical, crossOf, mainOf)

        return StitchPlan(
            canvasWidth = finalLayout.canvasW,
            canvasHeight = finalLayout.canvasH,
            placements = finalLayout.placements,
            rawWidth = raw.canvasW,
            rawHeight = raw.canvasH,
            scale = if (scale >= 0.999f) 1f else scale,
        )
    }

    private data class RawLayout(
        val canvasW: Int,
        val canvasH: Int,
        val placements: List<Placement>,
    )

    private fun layout(
        sources: List<SourceSize>,
        options: StitchOptions,
        crossTarget: Double,
        scale: Double,
        vertical: Boolean,
        crossOf: (SourceSize) -> Int,
        mainOf: (SourceSize) -> Int,
    ): RawLayout {
        // 1. 计算每张图的缩放后尺寸
        // ORIGINAL 模式只应用整体降采样系数，不做基准对齐缩放
        val keepOriginalSize = options.sizeMode == SizeMode.ORIGINAL
        val crossSizes = IntArray(sources.size)
        val mainSizes = IntArray(sources.size)
        sources.forEachIndexed { i, src ->
            val cross = crossOf(src).coerceAtLeast(1)
            val mainDim = mainOf(src).coerceAtLeast(1)
            val itemScale = if (keepOriginalSize) scale else crossTarget / cross * scale
            crossSizes[i] = (cross * itemScale).roundToInt().coerceAtLeast(1)
            mainSizes[i] = (mainDim * itemScale).roundToInt().coerceAtLeast(1)
        }

        val canvasCross = crossSizes.maxOrNull() ?: 1
        val gap = (options.gap * scale).roundToInt().coerceAtLeast(0)

        // 2. 依次排布
        var cursor = 0
        val placements = ArrayList<Placement>(sources.size)
        sources.indices.forEach { i ->
            if (i > 0) cursor += gap
            val c = crossSizes[i]
            val m = mainSizes[i]
            val slack = (canvasCross - c).coerceAtLeast(0)
            val crossOffset = when (options.crossAlign) {
                CrossAlign.START -> 0
                CrossAlign.CENTER -> slack / 2
                CrossAlign.END -> slack
            }
            placements.add(
                if (vertical) {
                    Placement(i, crossOffset, cursor, crossOffset + c, cursor + m)
                } else {
                    Placement(i, cursor, crossOffset, cursor + m, crossOffset + c)
                }
            )
            cursor += m
        }

        val canvasMain = cursor.coerceAtLeast(1)
        return if (vertical) {
            RawLayout(canvasCross, canvasMain, placements)
        } else {
            RawLayout(canvasMain, canvasCross, placements)
        }
    }

    /** 计算因内存预算/边长上限导致的整体缩放系数 */
    fun computeScale(width: Int, height: Int, budgetPixels: Long, maxEdge: Int): Float {
        if (width <= 0 || height <= 0) return 1f
        var scale = 1.0
        val total = width.toDouble() * height.toDouble()
        if (budgetPixels > 0 && total > budgetPixels) {
            scale = sqrt(budgetPixels.toDouble() / total)
        }
        val longest = max(width, height)
        if (maxEdge > 0 && longest > maxEdge) {
            scale = min(scale, maxEdge.toDouble() / longest)
        }
        return scale.coerceIn(0.01, 1.0).toFloat()
    }
}
