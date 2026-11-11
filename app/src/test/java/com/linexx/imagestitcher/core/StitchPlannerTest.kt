package com.linexx.imagestitcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class StitchPlannerTest {

    private val largeBudget = 100_000_000_000L

    private fun vertical(sizeMode: SizeMode, gap: Int = 0) =
        StitchOptions(
            direction = StitchDirection.VERTICAL,
            sizeMode = sizeMode,
            gap = gap,
        )

    private fun horizontal(sizeMode: SizeMode, gap: Int = 0) =
        StitchOptions(
            direction = StitchDirection.HORIZONTAL,
            sizeMode = sizeMode,
            gap = gap,
        )

    @Test
    fun verticalOriginal_keepsPixelSize_andCentersNarrowImage() {
        val sources = listOf(SourceSize(1080, 2400), SourceSize(720, 1280))
        val plan = StitchPlanner.plan(sources, vertical(SizeMode.ORIGINAL), largeBudget)

        assertEquals(1080, plan.canvasWidth)
        assertEquals(3680, plan.canvasHeight)
        assertFalse(plan.downscaled)

        val first = plan.placements[0]
        assertEquals(0, first.left)
        assertEquals(0, first.top)
        assertEquals(1080, first.right)
        assertEquals(2400, first.bottom)

        val second = plan.placements[1]
        assertEquals(180, second.left)
        assertEquals(900, second.right)
        assertEquals(2400, second.top)
        assertEquals(1280, second.height)
    }

    @Test
    fun verticalFitMax_upscalesEveryImageToWidest() {
        val sources = listOf(
            SourceSize(1080, 2400),
            SourceSize(720, 1280),
            SourceSize(1440, 3200),
        )
        val plan = StitchPlanner.plan(sources, vertical(SizeMode.FIT_MAX), largeBudget)

        assertEquals(1440, plan.canvasWidth)
        plan.placements.forEach { assertEquals(1440, it.width) }
        assertEquals(3200, plan.placements[0].height)
        assertEquals(2560, plan.placements[1].height)
        assertEquals(3200, plan.placements[2].height)
        assertEquals(8960, plan.canvasHeight)
    }

    @Test
    fun verticalFitMin_downscalesEveryImageToNarrowest() {
        val sources = listOf(
            SourceSize(1080, 2400),
            SourceSize(720, 1280),
            SourceSize(1440, 3200),
        )
        val plan = StitchPlanner.plan(sources, vertical(SizeMode.FIT_MIN), largeBudget)

        assertEquals(720, plan.canvasWidth)
        plan.placements.forEach { assertEquals(720, it.width) }
        assertEquals(1600, plan.placements[0].height)
        assertEquals(1280, plan.placements[1].height)
        assertEquals(1600, plan.placements[2].height)
        assertEquals(4480, plan.canvasHeight)
    }

    @Test
    fun horizontalFitMax_upscalesEveryImageToTallest() {
        val sources = listOf(SourceSize(1080, 2400), SourceSize(1080, 1200))
        val plan = StitchPlanner.plan(sources, horizontal(SizeMode.FIT_MAX), largeBudget)

        assertEquals(2400, plan.canvasHeight)
        assertEquals(1080, plan.placements[0].width)
        assertEquals(2160, plan.placements[1].width)
        assertEquals(3240, plan.canvasWidth)
        assertEquals(0, plan.placements[0].left)
        assertEquals(1080, plan.placements[1].left)
    }

    @Test
    fun horizontalOriginal_centersShorterImageVertically() {
        val sources = listOf(SourceSize(1000, 800), SourceSize(600, 400))
        val plan = StitchPlanner.plan(sources, horizontal(SizeMode.ORIGINAL), largeBudget)

        assertEquals(1600, plan.canvasWidth)
        assertEquals(800, plan.canvasHeight)
        val second = plan.placements[1]
        assertEquals(1000, second.left)
        assertEquals(200, second.top)
        assertEquals(600, second.bottom)
    }

    @Test
    fun gapAppliesOnlyBetweenImages() {
        val sources = listOf(SourceSize(100, 100), SourceSize(100, 100), SourceSize(100, 100))
        val plan = StitchPlanner.plan(sources, vertical(SizeMode.ORIGINAL, gap = 20), largeBudget)

        assertEquals(340, plan.canvasHeight)
        assertEquals(0, plan.placements[0].top)
        assertEquals(120, plan.placements[1].top)
        assertEquals(240, plan.placements[2].top)
    }

    @Test
    fun budgetForcesDownscale_andKeepsRawSize() {
        val sources = List(20) { SourceSize(1080, 2400) }
        val budget = 20_000_000L
        val plan = StitchPlanner.plan(sources, vertical(SizeMode.ORIGINAL), budget)

        assertEquals(1080, plan.rawWidth)
        assertEquals(48000, plan.rawHeight)
        assertTrue(plan.downscaled)
        val pixels = plan.canvasWidth.toLong() * plan.canvasHeight
        assertTrue("pixels=$pixels", pixels <= (budget * 1.02).toLong())
    }

    @Test
    fun maxEdgeIsRespected() {
        val sources = List(30) { SourceSize(1080, 2400) }
        val plan = StitchPlanner.plan(
            sources = sources,
            options = vertical(SizeMode.ORIGINAL),
            budgetPixels = largeBudget,
            maxEdge = 30000,
        )

        assertTrue(plan.downscaled)
        assertEquals(30000, plan.canvasHeight)
        assertTrue(plan.canvasWidth <= 1080)
    }

    @Test
    fun singleImageIsUnchanged() {
        val sources = listOf(SourceSize(800, 600))
        val plan = StitchPlanner.plan(sources, vertical(SizeMode.FIT_MAX), largeBudget)

        assertEquals(800, plan.canvasWidth)
        assertEquals(600, plan.canvasHeight)
        assertEquals(1, plan.placements.size)
    }

    @Test
    fun placementsAreContiguous_whenNoGap() {
        val sources = listOf(
            SourceSize(1080, 2400),
            SourceSize(720, 1280),
            SourceSize(1440, 3200),
        )
        val plan = StitchPlanner.plan(sources, vertical(SizeMode.FIT_MAX), largeBudget)

        plan.placements.zipWithNext { upper, lower ->
            assertEquals("上下两张图之间不应有缝隙或重叠", upper.bottom, lower.top)
        }
        assertEquals(0, plan.placements.first().top)
        assertEquals(plan.canvasHeight, plan.placements.last().bottom)
    }

    @Test
    fun identicalSizes_allModesProduceSameLayout() {
        val sources = List(3) { SourceSize(1080, 2400) }
        val original = StitchPlanner.plan(sources, vertical(SizeMode.ORIGINAL), largeBudget)
        val fitMax = StitchPlanner.plan(sources, vertical(SizeMode.FIT_MAX), largeBudget)
        val fitMin = StitchPlanner.plan(sources, vertical(SizeMode.FIT_MIN), largeBudget)

        assertEquals(1080, original.canvasWidth)
        assertEquals(7200, original.canvasHeight)
        assertEquals(original.canvasWidth, fitMax.canvasWidth)
        assertEquals(original.canvasHeight, fitMax.canvasHeight)
        assertEquals(original.canvasWidth, fitMin.canvasWidth)
        assertEquals(original.canvasHeight, fitMin.canvasHeight)
    }

    @Test
    fun aspectRatioIsPreserved_whenScaling() {
        val sources = listOf(
            SourceSize(1080, 2400),
            SourceSize(720, 1280),
            SourceSize(1440, 3200),
        )
        val plan = StitchPlanner.plan(sources, vertical(SizeMode.FIT_MAX), largeBudget)

        sources.forEachIndexed { index, source ->
            val expected = source.width.toDouble() / source.height
            val placement = plan.placements[index]
            val actual = placement.width.toDouble() / placement.height
            assertEquals("第 ${index + 1} 张宽高比应保持", expected, actual, 0.01)
        }
    }

    @Test
    fun extremeAspectRatio_isHandled() {
        val sources = listOf(SourceSize(1, 10000), SourceSize(1, 5000))
        val plan = StitchPlanner.plan(sources, vertical(SizeMode.ORIGINAL), largeBudget)

        assertEquals(1, plan.canvasWidth)
        assertEquals(15000, plan.canvasHeight)
        assertFalse(plan.downscaled)
    }

    @Test
    fun gapIsScaledWithDownscale() {
        val sources = List(20) { SourceSize(1080, 2400) }
        val plan = StitchPlanner.plan(sources, vertical(SizeMode.ORIGINAL, gap = 100), 20_000_000L)

        assertTrue(plan.downscaled)
        val firstGap = plan.placements[1].top - plan.placements[0].bottom
        assertEquals("间距应随降采样等比缩小", (100 * plan.scale).roundToInt(), firstGap)
        plan.placements.zipWithNext { upper, lower ->
            assertEquals(firstGap, lower.top - upper.bottom)
        }
    }

    @Test
    fun maxEdgeAndBudget_bothApply() {
        val sources = List(30) { SourceSize(1080, 2400) }

        val edgeLimited = StitchPlanner.plan(
            sources = sources,
            options = vertical(SizeMode.ORIGINAL),
            budgetPixels = largeBudget,
            maxEdge = 30000,
        )
        assertEquals(30000, edgeLimited.canvasHeight)

        val budgetLimited = StitchPlanner.plan(
            sources = sources,
            options = vertical(SizeMode.ORIGINAL),
            budgetPixels = 2_000_000L,
            maxEdge = 30000,
        )
        assertTrue(budgetLimited.canvasHeight < 30000)
        val pixels = budgetLimited.canvasWidth.toLong() * budgetLimited.canvasHeight
        assertTrue("pixels=$pixels", pixels <= 2_040_000L)
    }
}
