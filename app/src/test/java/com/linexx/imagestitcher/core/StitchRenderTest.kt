package com.linexx.imagestitcher.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.linexx.imagestitcher.model.OutputFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * 端到端渲染回归：真的生成图片、真的拼接、再读回像素校验位置与颜色。
 * 用 Robolectric 的 NATIVE 图形模式，Canvas/Bitmap/PNG 编解码都走真实实现。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StitchRenderTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    private fun newDir(tag: String): File {
        val dir = File(context.cacheDir, "stitch-$tag")
        if (dir.exists()) dir.deleteRecursively()
        dir.mkdirs()
        return dir
    }

    private fun solidImage(dir: File, name: String, width: Int, height: Int, color: Int): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        val file = File(dir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return Uri.fromFile(file)
    }

    private fun probeAll(uris: List<Uri>): List<com.linexx.imagestitcher.model.ImageItem> =
        uris.mapNotNull { ImageLoader.probe(context, it) }

    private fun assertPixel(expected: Int, actual: Int, label: String) {
        assertEquals(
            "$label 颜色不符：期望 #%08X，实际 #%08X".format(expected, actual),
            expected,
            actual,
        )
    }

    @Test
    fun verticalOriginal_paintsEachImageAtItsOwnPlacement() {
        val dir = newDir("v-orig")
        val uris = listOf(
            solidImage(dir, "a.png", 100, 50, Color.RED),
            solidImage(dir, "b.png", 100, 50, Color.GREEN),
            solidImage(dir, "c.png", 100, 50, Color.BLUE),
        )
        val items = probeAll(uris)
        assertEquals(3, items.size)
        items.forEach {
            assertEquals(100, it.width)
            assertEquals(50, it.height)
        }

        val plan = StitchPlanner.plan(
            items.map { SourceSize(it.width, it.height) },
            StitchOptions(StitchDirection.VERTICAL, SizeMode.ORIGINAL),
            budgetPixels = 100_000_000L,
        )
        assertEquals(100, plan.canvasWidth)
        assertEquals(150, plan.canvasHeight)

        val out = File(dir, "out.png")
        val ok = Stitcher(context).render(
            items = items,
            plan = plan,
            background = Color.WHITE,
            format = OutputFormat.PNG,
            quality = 100,
            output = out,
            onProgress = {},
        )
        assertTrue("渲染应成功", ok)
        assertTrue("输出文件应存在", out.length() > 0)

        val result = BitmapFactory.decodeFile(out.absolutePath)!!
        assertEquals(100, result.width)
        assertEquals(150, result.height)
        assertPixel(Color.RED, result.getPixel(50, 25), "第一张")
        assertPixel(Color.GREEN, result.getPixel(50, 75), "第二张")
        assertPixel(Color.BLUE, result.getPixel(50, 125), "第三张")
        result.recycle()
    }

    @Test
    fun verticalFitMin_scalesEveryImageToNarrowestWidth() {
        val dir = newDir("v-min")
        // 三张图宽高比都是 2:1，最小宽度 60 -> 全部变成 60x30
        val uris = listOf(
            solidImage(dir, "a.png", 100, 50, Color.RED),
            solidImage(dir, "b.png", 60, 30, Color.GREEN),
            solidImage(dir, "c.png", 80, 40, Color.BLUE),
        )
        val items = probeAll(uris)

        val plan = StitchPlanner.plan(
            items.map { SourceSize(it.width, it.height) },
            StitchOptions(StitchDirection.VERTICAL, SizeMode.FIT_MIN),
            budgetPixels = 100_000_000L,
        )
        assertEquals(60, plan.canvasWidth)
        assertEquals(90, plan.canvasHeight)
        plan.placements.forEach {
            assertEquals(60, it.width)
            assertEquals(30, it.height)
        }

        val out = File(dir, "out.png")
        assertTrue(
            Stitcher(context).render(
                items, plan, Color.WHITE, OutputFormat.PNG, 100, out, {},
            )
        )

        val result = BitmapFactory.decodeFile(out.absolutePath)!!
        assertEquals(60, result.width)
        assertEquals(90, result.height)
        assertPixel(Color.RED, result.getPixel(30, 15), "第一张")
        assertPixel(Color.GREEN, result.getPixel(30, 45), "第二张")
        assertPixel(Color.BLUE, result.getPixel(30, 75), "第三张")
        result.recycle()
    }

    @Test
    fun horizontalWithGap_fillsBackgroundBetweenImages() {
        val dir = newDir("h-gap")
        val uris = listOf(
            solidImage(dir, "a.png", 50, 100, Color.RED),
            solidImage(dir, "b.png", 50, 100, Color.GREEN),
        )
        val items = probeAll(uris)

        val plan = StitchPlanner.plan(
            items.map { SourceSize(it.width, it.height) },
            StitchOptions(StitchDirection.HORIZONTAL, SizeMode.ORIGINAL, gap = 10),
            budgetPixels = 100_000_000L,
        )
        assertEquals(110, plan.canvasWidth)
        assertEquals(100, plan.canvasHeight)

        val out = File(dir, "out.png")
        assertTrue(
            Stitcher(context).render(
                items, plan, Color.WHITE, OutputFormat.PNG, 100, out, {},
            )
        )

        val result = BitmapFactory.decodeFile(out.absolutePath)!!
        assertEquals(110, result.width)
        assertPixel(Color.RED, result.getPixel(25, 50), "左侧图")
        assertPixel(Color.WHITE, result.getPixel(55, 50), "间距应用背景色填充")
        assertPixel(Color.GREEN, result.getPixel(85, 50), "右侧图")
        result.recycle()
    }

    @Test
    fun transparentBackground_keepsAlphaInPng() {
        val dir = newDir("alpha")
        val uris = listOf(
            solidImage(dir, "a.png", 40, 40, Color.RED),
            solidImage(dir, "b.png", 40, 40, Color.RED),
        )
        val items = probeAll(uris)

        val plan = StitchPlanner.plan(
            items.map { SourceSize(it.width, it.height) },
            StitchOptions(StitchDirection.VERTICAL, SizeMode.ORIGINAL, gap = 20),
            budgetPixels = 100_000_000L,
        )
        assertEquals(100, plan.canvasHeight)

        val out = File(dir, "out.png")
        assertTrue(
            Stitcher(context).render(
                items, plan, Color.TRANSPARENT, OutputFormat.PNG, 100, out, {},
            )
        )

        val result = BitmapFactory.decodeFile(out.absolutePath)!!
        assertEquals(0, result.getPixel(20, 50) ushr 24)
        assertPixel(Color.RED, result.getPixel(20, 20), "图片区域")
        result.recycle()
    }
}
