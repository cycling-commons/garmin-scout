package org.cyclingcommons.scout.fit

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Minimal original-FIT encoder for Scout (SPEC §4.2 / §4.3).
 * Record fields: timestamp, lat, lon, speed, plus the five Scout developer channels.
 * No HR / cadence / altitude / device id extras.
 */
class ScoutFitWriter(
    private val outFile: File,
) {
    private val samples = CopyOnWriteArrayList<ScoutSample>()

    val recordCount: Int get() = samples.size
    val file: File get() = outFile

    fun append(sample: ScoutSample) {
        samples.add(sample)
    }

    /** Rewrite the complete FIT to disk (header + body + CRC). */
    fun flush() {
        outFile.parentFile?.mkdirs()
        val bytes = encode(samples.toList())
        FileOutputStream(outFile).use { it.write(bytes) }
    }

    fun finish(): File {
        flush()
        return outFile
    }

    companion object {
        private val DEV_FIELDS = listOf(
            0 to "poi_type",
            1 to "poi_detail",
            2 to "radar_count",
            3 to "radar_near",
            4 to "radar_speed",
        )

        fun encode(records: List<ScoutSample>): ByteArray {
            val body = ByteArrayOutputStream()
            val w = FitBinaryWriter(body)

            // file_id (0), local 0 — type = activity (4)
            w.u8(0x40); w.u8(0); w.u8(0); w.u16(0); w.u8(1)
            w.u8(0); w.u8(1); w.u8(0x00)
            w.u8(0x00); w.u8(4)

            // developer_data_id (207), local 1 — developer_data_index = 0
            w.u8(0x41); w.u8(0); w.u8(0); w.u16(207); w.u8(1)
            w.u8(3); w.u8(1); w.u8(0x02)
            w.u8(0x01); w.u8(0)

            // field_description (206), local 2
            w.u8(0x42); w.u8(0); w.u8(0); w.u16(206); w.u8(4)
            w.u8(0); w.u8(1); w.u8(0x02) // developer_data_index
            w.u8(1); w.u8(1); w.u8(0x02) // field_definition_number
            w.u8(2); w.u8(1); w.u8(0x02) // fit_base_type_id
            w.u8(3); w.u8(16); w.u8(0x07) // field_name string(16)
            for ((num, name) in DEV_FIELDS) {
                w.u8(0x02)
                w.u8(0)
                w.u8(num)
                w.u8(0x02)
                w.str(name, 16)
            }

            // record (20), local 3 — SPEC sample: time + GPS + speed + Scout channels
            w.u8(0x63); w.u8(0); w.u8(0); w.u16(20); w.u8(4)
            w.u8(253); w.u8(4); w.u8(0x86) // timestamp uint32
            w.u8(0); w.u8(4); w.u8(0x85) // position_lat sint32
            w.u8(1); w.u8(4); w.u8(0x85) // position_long sint32
            w.u8(6); w.u8(2); w.u8(0x84) // speed uint16
            w.u8(5) // 5 Scout developer fields
            for ((num, _) in DEV_FIELDS) {
                w.u8(num); w.u8(1); w.u8(0)
            }

            for (r in records) {
                w.u8(0x03)
                w.u32(r.timestampFit)
                w.i32(r.latSemi ?: ScoutSample.POSITION_INVALID)
                w.i32(r.lonSemi ?: ScoutSample.POSITION_INVALID)
                w.u16(r.speedMmPerS ?: ScoutSample.SPEED_INVALID)
                w.u8(r.poiType)
                w.u8(r.poiDetail)
                w.u8(r.radarCount)
                w.u8(r.radarNear)
                w.u8(r.radarSpeed)
            }

            val bodyBytes = body.toByteArray()
            val head = ByteArray(12)
            head[0] = 12
            head[1] = 0x20
            writeU16Le(head, 2, 2140)
            writeU32Le(head, 4, bodyBytes.size)
            head[8] = '.'.code.toByte()
            head[9] = 'F'.code.toByte()
            head[10] = 'I'.code.toByte()
            head[11] = 'T'.code.toByte()

            val all = ByteArray(head.size + bodyBytes.size + 2)
            System.arraycopy(head, 0, all, 0, head.size)
            System.arraycopy(bodyBytes, 0, all, head.size, bodyBytes.size)
            val crc = FitCrc.crc16(all, 0, head.size + bodyBytes.size)
            writeU16Le(all, head.size + bodyBytes.size, crc)
            return all
        }

        private fun writeU16Le(buf: ByteArray, offset: Int, value: Int) {
            buf[offset] = (value and 0xFF).toByte()
            buf[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        }

        private fun writeU32Le(buf: ByteArray, offset: Int, value: Int) {
            writeU16Le(buf, offset, value and 0xFFFF)
            writeU16Le(buf, offset + 2, (value ushr 16) and 0xFFFF)
        }
    }
}

private class FitBinaryWriter(private val out: ByteArrayOutputStream) {
    fun u8(v: Int) = out.write(v and 0xFF)
    fun u16(v: Int) {
        u8(v)
        u8(v ushr 8)
    }

    fun u32(v: Long) {
        u16((v and 0xFFFF).toInt())
        u16(((v ushr 16) and 0xFFFF).toInt())
    }

    fun i32(v: Int) = u32(v.toLong() and 0xFFFFFFFFL)

    fun str(s: String, len: Int) {
        for (i in 0 until len) {
            u8(if (i < s.length) s[i].code else 0)
        }
    }
}
