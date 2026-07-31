package org.cyclingcommons.scout.recording

import android.content.Context
import org.cyclingcommons.scout.fit.ScoutFitWriter
import org.cyclingcommons.scout.fit.ScoutSample
import org.cyclingcommons.scout.sensors.LocationFix
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Owns the open FIT file for one ride; flushes on pause/stop and periodically. */
class RideFitSession(
    context: Context,
    private val flushEveryRecords: Int = 30,
) {
    private val ridesDir = File(context.filesDir, "rides").also { it.mkdirs() }
    private val writer: ScoutFitWriter
    private var sinceFlush = 0

    val outFile: File

    init {
        val name = "scout-" + stamp() + ".fit"
        outFile = File(ridesDir, name)
        writer = ScoutFitWriter(outFile)
    }

    fun appendSample(
        nowMs: Long,
        fix: LocationFix?,
        poiType: Int,
        poiDetail: Int,
        radarCount: Int = ScoutSample.RADAR_NA,
        radarNear: Int = ScoutSample.RADAR_NA,
        radarSpeed: Int = ScoutSample.RADAR_NA,
    ) {
        val lat = fix?.let { ScoutSample.degreesToSemi(it.latitude) }
        val lon = fix?.let { ScoutSample.degreesToSemi(it.longitude) }
        val speed =
            if (fix != null && fix.hasSpeed && fix.speedMps != null) {
                (fix.speedMps * 1000f).toInt().coerceIn(0, 0xFFFE)
            } else {
                null
            }
        writer.append(
            ScoutSample(
                timestampFit = ScoutSample.unixMsToFit(nowMs),
                latSemi = lat,
                lonSemi = lon,
                speedMmPerS = speed,
                poiType = poiType,
                poiDetail = poiDetail,
                radarCount = radarCount,
                radarNear = radarNear,
                radarSpeed = radarSpeed,
            ),
        )
        sinceFlush++
        if (sinceFlush >= flushEveryRecords) {
            flush()
        }
    }

    fun flush() {
        writer.flush()
        sinceFlush = 0
    }

    fun finish(): File {
        writer.finish()
        sinceFlush = 0
        return outFile
    }

    val recordCount: Int get() = writer.recordCount

    private fun stamp(): String {
        val fmt = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        fmt.timeZone = TimeZone.getDefault()
        return fmt.format(Date())
    }
}
