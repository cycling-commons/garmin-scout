package org.cyclingcommons.scout.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagTalliesTest {
    @Test
    fun directUndoWithinWindow() {
        val t = TagTallies()
        assertFalse(t.countTap(PoiType.DANGER, 0, 1000))
        assertEquals(1, t.count(PoiType.DANGER))
        assertTrue(t.countTap(PoiType.DANGER, 0, 2000))
        assertEquals(0, t.count(PoiType.DANGER))
    }

    @Test
    fun directKeptOutsideWindow() {
        val t = TagTallies()
        t.countTap(PoiType.DANGER, 0, 1000)
        assertFalse(t.countTap(PoiType.DANGER, 0, 1000 + Timings.UNDO_MS))
        assertEquals(2, t.count(PoiType.DANGER))
    }

    @Test
    fun closureGetsDoubleWindow() {
        val t = TagTallies()
        t.countTap(PoiType.CLOSURE, Duration.TODAY, 1000)
        assertTrue(t.countTap(PoiType.CLOSURE, Duration.DAYS, 1000 + Timings.UNDO_MS + 500))
        assertEquals(0, t.count(PoiType.CLOSURE))
    }

    @Test
    fun surfaceNeverUndoes() {
        val t = TagTallies()
        t.countTap(PoiType.SURFACE, Surface.COBBLES, 1000)
        assertFalse(t.countTap(PoiType.SURFACE, Surface.GRAVEL, 1500))
        assertEquals(2, t.count(PoiType.SURFACE))
    }

    @Test
    fun surfaceEndDoesNotTally() {
        val t = TagTallies()
        t.countTap(PoiType.SURFACE, Surface.END, 1000)
        assertEquals(0, t.count(PoiType.SURFACE))
    }

    @Test
    fun resupplyTileSumsLeaves() {
        val t = TagTallies()
        t.countTap(PoiType.WATER, 0, 1000)
        t.countTap(PoiType.FOOD, 0, 5000)
        assertEquals(2, t.tileCount(PoiType.UI_RESUPPLY))
    }

    @Test
    fun closureDetailTalliesKeptSeparately() {
        val t = TagTallies()
        val gap = Timings.UNDO_MS * 2 + 1 // outside closure undo window
        t.countTap(PoiType.CLOSURE, Duration.MONTHS, 1000)
        t.countTap(PoiType.CLOSURE, Duration.MONTHS, 1000 + gap)
        t.countTap(PoiType.CLOSURE, Duration.TODAY, 1000 + gap * 2)
        assertEquals(2, t.closureDetailCount(Duration.MONTHS))
        assertEquals(1, t.closureDetailCount(Duration.TODAY))
        assertEquals(3, t.count(PoiType.CLOSURE))
    }

    @Test
    fun closureUndoDropsMatchingDetail() {
        val t = TagTallies()
        t.countTap(PoiType.CLOSURE, Duration.MONTHS, 1000)
        assertEquals(1, t.closureDetailCount(Duration.MONTHS))
        assertTrue(t.countTap(PoiType.CLOSURE, Duration.DAYS, 2000))
        assertEquals(0, t.closureDetailCount(Duration.MONTHS))
        assertEquals(0, t.count(PoiType.CLOSURE))
    }

    @Test
    fun surfaceDetailTalliesIncludeEnd() {
        val t = TagTallies()
        t.countTap(PoiType.SURFACE, Surface.COBBLES, 1000)
        t.countTap(PoiType.SURFACE, Surface.GRAVEL, 1500)
        t.countTap(PoiType.SURFACE, Surface.END, 2000)
        assertEquals(1, t.surfaceDetailCount(Surface.COBBLES))
        assertEquals(1, t.surfaceDetailCount(Surface.GRAVEL))
        assertEquals(1, t.surfaceDetailCount(Surface.END))
        assertEquals(2, t.count(PoiType.SURFACE)) // starts only
    }

    @Test
    fun openSurfaceTracksActiveStretch() {
        val t = TagTallies()
        assertEquals(Surface.NONE, t.openSurfaceDetail)
        t.countTap(PoiType.SURFACE, Surface.COBBLES, 1000)
        assertEquals(Surface.COBBLES, t.openSurfaceDetail)
        t.countTap(PoiType.SURFACE, Surface.GRAVEL, 1500)
        assertEquals(Surface.GRAVEL, t.openSurfaceDetail)
        t.countTap(PoiType.SURFACE, Surface.END, 2000)
        assertEquals(Surface.NONE, t.openSurfaceDetail)
    }
}

class ScoutControllerTest {
    @Test
    fun directTagEnqueuesWhenRunning() {
        val c = ScoutController()
        c.start()
        c.onTileTap(0, 1000) // DANGER
        assertEquals(1, c.queueSize())
        val tag = c.drainTag()
        assertEquals(PoiType.DANGER, tag!!.type)
        assertEquals(0, tag.detail)
    }

    @Test
    fun doesNotEnqueueWhenPaused() {
        val c = ScoutController()
        c.start()
        c.pause()
        c.onTileTap(0, 1000)
        assertEquals(0, c.queueSize())
        assertEquals(0, c.snapshot().tileCounts[0])
    }

    @Test
    fun idleTapDoesNotTallyOrEnqueue() {
        val c = ScoutController()
        c.onTileTap(0, 1000) // DANGER while idle
        assertEquals(0, c.queueSize())
        assertEquals(0, c.snapshot().tileCounts[0])
        assertNotNull(c.takeFeedback()) // flash/beep still confirms the tap
    }

    @Test
    fun closurePickerCommitsAfterCorrectWindow() {
        val c = ScoutController()
        c.start()
        c.onTileTap(1, 1000) // CLOSURE
        assertEquals(UiMode.DURATION, c.snapshot().mode)
        c.onTileTap(0, 1100) // TODAY
        assertEquals(0, c.queueSize())
        c.onTick(1100 + Timings.CORRECT_MS + 1)
        val tag = c.drainTag()
        assertEquals(PoiType.CLOSURE, tag!!.type)
        assertEquals(Duration.TODAY, tag.detail)
        assertEquals(UiMode.GRID, c.snapshot().mode)
    }

    @Test
    fun closureTimeoutWritesUnknown() {
        val c = ScoutController()
        c.start()
        c.onTileTap(1, 1000)
        c.onTick(1000 + Timings.PICK_MS + 1)
        val tag = c.drainTag()
        assertEquals(PoiType.CLOSURE, tag!!.type)
        assertEquals(Duration.UNKNOWN, tag.detail)
    }

    @Test
    fun resupplyTimeoutDrops() {
        val c = ScoutController()
        c.start()
        c.onTileTap(3, 1000) // RESUPPLY
        c.onTick(1000 + Timings.PICK_MS + 1)
        assertEquals(0, c.queueSize())
        assertEquals(UiMode.GRID, c.snapshot().mode)
    }

    @Test
    fun backAbortsPending() {
        val c = ScoutController()
        c.start()
        c.onTileTap(1, 1000)
        c.onTileTap(0, 1100) // TODAY pending
        c.onTileTap(5, 1200) // BACK
        assertEquals(0, c.queueSize())
        assertEquals(UiMode.GRID, c.snapshot().mode)
    }

    @Test
    fun fifoPreservesDoubleTap() {
        val c = ScoutController()
        c.start()
        c.onTileTap(0, 1000)
        c.onTileTap(0, 1500)
        assertEquals(2, c.queueSize())
        assertEquals(PoiType.DANGER, c.drainTag()!!.type)
        assertEquals(PoiType.DANGER, c.drainTag()!!.type)
    }

    @Test
    fun endOpenSurfaceWritesEnd() {
        val c = ScoutController()
        c.start()
        c.onTileTap(2, 1000) // SURFACE
        c.onTileTap(4, 1100) // COBBLES (index in surface picker)
        c.onTick(1100 + Timings.CORRECT_MS + 1)
        assertEquals(Surface.COBBLES, c.snapshot().openSurfaceDetail)
        c.drainTag() // consume cobbles start
        c.endOpenSurface(5000)
        val end = c.drainTag()
        assertEquals(PoiType.SURFACE, end!!.type)
        assertEquals(Surface.END, end.detail)
        assertEquals(Surface.NONE, c.snapshot().openSurfaceDetail)
    }
}

class VehicleCounterTest {
    @Test
    fun oneSecondBlipNotCounted() {
        val v = VehicleCounter()
        v.onSample(true, 1, 20, 20)
        v.onSample(true, 0, -1, 20)
        assertEquals(0, v.carCount)
    }

    @Test
    fun corroboratedArrivalCounts() {
        val v = VehicleCounter()
        v.onSample(true, 0, -1, 20)
        v.onSample(true, 1, 30, 20)
        v.onSample(true, 1, 28, 20)
        assertEquals(1, v.carCount)
    }

    @Test
    fun dropoutClearsPending() {
        val v = VehicleCounter()
        v.onSample(true, 0, -1, 0)
        v.onSample(true, 1, 20, 0)
        v.onSample(false, 0, -1, 0)
        v.onSample(true, 1, 20, 0)
        assertEquals(0, v.carCount)
    }
}
