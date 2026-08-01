package org.cyclingcommons.scout.domain

data class QueuedTag(
    val type: Int,
    val detail: Int,
)

/**
 * Live tally mirror of the parser undo rule (display only — both taps still enqueue).
 */
class TagTallies {
    private val counts = IntArray(9) // index = poi_type 1..8
    /** Closure duration buckets (TODAY..UNKNOWN). Codes collide with poi_type. */
    private val closureDetails = IntArray(6) // index = Duration 1..5
    /** Surface detail buckets (ASPHALT..END). Codes collide with poi_type. */
    private val surfaceDetails = IntArray(10) // index = Surface 1..9

    var lastTapType: Int = PoiType.NONE
        private set
    var lastTapDetail: Int = Duration.NONE
        private set
    var lastTapAtMs: Long = 0L
        private set

    /**
     * Currently open stretch detail (`ASPHALT`…`SAND`), or [Surface.NONE] if
     * the road is untagged. Display-only — mirrors commit transitions.
     */
    var openSurfaceDetail: Int = Surface.NONE
        private set

    fun closureDetailCount(detail: Int): Int =
        if (detail in Duration.TODAY..Duration.UNKNOWN) closureDetails[detail] else 0

    fun surfaceDetailCount(detail: Int): Int =
        if (detail in Surface.ASPHALT..Surface.END) surfaceDetails[detail] else 0

    fun clear() {
        counts.fill(0)
        closureDetails.fill(0)
        surfaceDetails.fill(0)
        lastTapType = PoiType.NONE
        lastTapDetail = Duration.NONE
        lastTapAtMs = 0L
        openSurfaceDetail = Surface.NONE
    }

    fun tileCount(code: Int): Int =
        when (code) {
            PoiType.UI_RESUPPLY ->
                counts[PoiType.WATER] + counts[PoiType.FOOD] + counts[PoiType.MECHANICAL]
            in 1 until counts.size -> counts[code]
            else -> 0
        }

    /** SPEC §6.7: the grid SURFACE tally counts stretch starts, never END. */
    fun countsTowardGridTile(type: Int, detail: Int): Boolean =
        type != PoiType.SURFACE || detail in Surface.ASPHALT..Surface.SAND

    /** @return true if this tap cancelled a pair (undo) */
    fun countTap(type: Int, detail: Int, nowMs: Long): Boolean {
        var undone = false
        if (type != PoiType.SURFACE &&
            type == lastTapType &&
            (nowMs - lastTapAtMs) < undoMsFor(type)
        ) {
            counts[type] = (counts[type] - 1).coerceAtLeast(0)
            if (lastTapType == PoiType.CLOSURE) {
                val d = lastTapDetail
                if (d in Duration.TODAY..Duration.UNKNOWN) {
                    closureDetails[d] = (closureDetails[d] - 1).coerceAtLeast(0)
                }
            }
            lastTapType = PoiType.NONE
            lastTapDetail = Duration.NONE
            undone = true
        } else {
            if (countsTowardGridTile(type, detail)) {
                counts[type]++
            }
            when {
                type == PoiType.CLOSURE && detail in Duration.TODAY..Duration.UNKNOWN ->
                    closureDetails[detail]++
                type == PoiType.SURFACE && detail in Surface.ASPHALT..Surface.END -> {
                    // Includes END for the submenu tile; grid SURFACE ignores END.
                    surfaceDetails[detail]++
                    openSurfaceDetail =
                        if (detail == Surface.END) Surface.NONE else detail
                }
                type == PoiType.SURFACE && detail == Surface.NONE ->
                    openSurfaceDetail = Surface.NONE
            }
            lastTapType = type
            lastTapDetail = detail
        }
        lastTapAtMs = nowMs
        return undone
    }
}
