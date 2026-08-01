package org.cyclingcommons.scout.fit

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * Minimal original-FIT encoder for Scout (SPEC §4.2 / §4.3).
 * Record fields: timestamp, lat, lon, speed, plus the five Scout developer channels.
 * No HR / cadence / altitude / device id extras.
 *
 * Records are appended straight onto the open file: a flush costs the bytes added
 * since the last one plus a 14-byte header/CRC patch, so a multi-hour ride does not
 * grow into repeated full-file rewrites (SPEC §12.1 "I/O frugally").
 */
class ScoutFitWriter(
    private val outFile: File,
) {
    private val pending = ByteArrayOutputStream(PENDING_BUFFER_BYTES)
    private var raf: RandomAccessFile? = null
    private var dataSize = 0

    var recordCount: Int = 0
        private set

    val file: File get() = outFile

    fun append(sample: ScoutSample) {
        writeRecord(FitBinaryWriter(pending), sample)
        recordCount++
        if (pending.size() >= PENDING_BUFFER_BYTES) {
            flush()
        }
    }

    /** Persist everything appended so far and leave a valid, parseable FIT on disk. */
    fun flush() {
        val handle = open()
        if (pending.size() > 0) {
            handle.seek((HEADER_BYTES + dataSize).toLong())
            handle.write(pending.toByteArray())
            dataSize += pending.size()
            pending.reset()
        }
        handle.seek(0L)
        handle.write(header(dataSize))
        handle.seek((HEADER_BYTES + dataSize).toLong())
        handle.write(crcBytes(fileCrc(handle)))
        handle.setLength((HEADER_BYTES + dataSize + CRC_BYTES).toLong())
    }

    fun finish(): File {
        flush()
        raf?.close()
        raf = null
        return outFile
    }

    private fun open(): RandomAccessFile {
        raf?.let { return it }
        outFile.parentFile?.mkdirs()
        val handle = RandomAccessFile(outFile, "rw")
        handle.setLength(0L)
        val definitions = ByteArrayOutputStream()
        writeDefinitions(FitBinaryWriter(definitions))
        handle.write(header(0))
        handle.write(definitions.toByteArray())
        dataSize = definitions.size()
        raf = handle
        return handle
    }

    /** CRC over header + body, streamed so it never holds the whole ride in memory. */
    private fun fileCrc(handle: RandomAccessFile): Int {
        handle.seek(0L)
        val buffer = ByteArray(CRC_CHUNK_BYTES)
        var remaining = HEADER_BYTES + dataSize
        var crc = 0
        while (remaining > 0) {
            val read = handle.read(buffer, 0, minOf(remaining, buffer.size))
            if (read <= 0) break
            crc = FitCrc.crc16(buffer, 0, read, crc)
            remaining -= read
        }
        return crc
    }

    companion object {
        private const val HEADER_BYTES = 12
        private const val CRC_BYTES = 2
        private const val PENDING_BUFFER_BYTES = 4096
        private const val CRC_CHUNK_BYTES = 8192
        private const val PROTOCOL_VERSION = 0x20
        private const val PROFILE_VERSION = 2140

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
            writeDefinitions(w)
            for (r in records) {
                writeRecord(w, r)
            }

            val bodyBytes = body.toByteArray()
            val all = ByteArray(HEADER_BYTES + bodyBytes.size + CRC_BYTES)
            System.arraycopy(header(bodyBytes.size), 0, all, 0, HEADER_BYTES)
            System.arraycopy(bodyBytes, 0, all, HEADER_BYTES, bodyBytes.size)
            val crc = FitCrc.crc16(all, 0, HEADER_BYTES + bodyBytes.size)
            writeU16Le(all, HEADER_BYTES + bodyBytes.size, crc)
            return all
        }

        /** file_id, developer field descriptions and the record layout — written once. */
        private fun writeDefinitions(w: FitBinaryWriter) {
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
        }

        private fun writeRecord(w: FitBinaryWriter, r: ScoutSample) {
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

        private fun header(bodySize: Int): ByteArray {
            val head = ByteArray(HEADER_BYTES)
            head[0] = HEADER_BYTES.toByte()
            head[1] = PROTOCOL_VERSION.toByte()
            writeU16Le(head, 2, PROFILE_VERSION)
            writeU32Le(head, 4, bodySize)
            head[8] = '.'.code.toByte()
            head[9] = 'F'.code.toByte()
            head[10] = 'I'.code.toByte()
            head[11] = 'T'.code.toByte()
            return head
        }

        private fun crcBytes(crc: Int): ByteArray {
            val out = ByteArray(CRC_BYTES)
            writeU16Le(out, 0, crc)
            return out
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
