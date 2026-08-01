package org.cyclingcommons.scout.recording

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.cyclingcommons.scout.fit.ScoutFitWriter
import org.cyclingcommons.scout.fit.ScoutSample
import org.cyclingcommons.scout.sensors.LocationFix
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

/**
 * Owns the open FIT file for one ride; flushes on pause/stop and periodically.
 *
 * Callers hand samples over from the sampling tick and never block: a single worker
 * coroutine drains them onto disk in order, so no file I/O lands on the main thread.
 */
class RideFitSession(
    context: Context,
    scope: CoroutineScope,
    private val flushEveryRecords: Int = FLUSH_EVERY_RECORDS,
) {
    private sealed interface Command {
        data class Append(val sample: ScoutSample) : Command
        data object Flush : Command
        data class Finish(val onDone: (File?) -> Unit) : Command
    }

    private val ridesDir = File(context.filesDir, "rides")
    val outFile: File = File(ridesDir, "scout-" + stamp() + ".fit")

    private val counted = AtomicInteger()
    private val commands = Channel<Command>(Channel.UNLIMITED)

    /** Samples handed over so far; the worker may still be writing the tail. */
    val recordCount: Int get() = counted.get()

    init {
        scope.launch(Dispatchers.IO) {
            val writer = ScoutFitWriter(outFile)
            var sinceFlush = 0
            for (command in commands) {
                when (command) {
                    is Command.Append -> {
                        writer.append(command.sample)
                        if (++sinceFlush >= flushEveryRecords) {
                            writer.flush()
                            sinceFlush = 0
                        }
                    }
                    Command.Flush -> {
                        if (writer.recordCount > 0) writer.flush()
                        sinceFlush = 0
                    }
                    is Command.Finish -> {
                        val saved =
                            if (writer.recordCount > 0) {
                                writer.finish()
                            } else {
                                outFile.delete()
                                null
                            }
                        command.onDone(saved)
                        return@launch
                    }
                }
            }
        }
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
        val speed =
            if (fix?.speedMps != null) {
                (fix.speedMps * 1000f).toInt().coerceIn(0, 0xFFFE)
            } else {
                null
            }
        counted.incrementAndGet()
        commands.trySend(
            Command.Append(
                ScoutSample(
                    timestampFit = ScoutSample.unixMsToFit(nowMs),
                    latSemi = fix?.let { ScoutSample.degreesToSemi(it.latitude) },
                    lonSemi = fix?.let { ScoutSample.degreesToSemi(it.longitude) },
                    speedMmPerS = speed,
                    poiType = poiType,
                    poiDetail = poiDetail,
                    radarCount = radarCount,
                    radarNear = radarNear,
                    radarSpeed = radarSpeed,
                ),
            ),
        )
    }

    fun flush() {
        commands.trySend(Command.Flush)
    }

    /** Closes the file; [onDone] gets the saved ride, or null when nothing was recorded. */
    fun finish(onDone: (File?) -> Unit) {
        commands.trySend(Command.Finish(onDone))
        commands.close()
    }

    private fun stamp(): String {
        val fmt = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        fmt.timeZone = TimeZone.getDefault()
        return fmt.format(Date())
    }

    companion object {
        /** Cap on how much of a ride a crash can cost (SPEC §12.1 durability vs flash wear). */
        const val FLUSH_EVERY_RECORDS = 30
    }
}
