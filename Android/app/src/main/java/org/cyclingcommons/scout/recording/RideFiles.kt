package org.cyclingcommons.scout.recording

import android.content.Context
import java.io.File

data class RideFile(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val modifiedMs: Long,
)

object RideFiles {
    fun dir(context: Context): File =
        File(context.filesDir, "rides").also { it.mkdirs() }

    fun list(context: Context): List<RideFile> =
        dir(context)
            .listFiles { f -> f.isFile && f.name.endsWith(".fit", ignoreCase = true) }
            ?.map {
                RideFile(
                    file = it,
                    name = it.name,
                    sizeBytes = it.length(),
                    modifiedMs = it.lastModified(),
                )
            }
            ?.sortedByDescending { it.modifiedMs }
            .orEmpty()

    fun delete(ride: RideFile): Boolean =
        try {
            ride.file.delete()
        } catch (_: Exception) {
            false
        }
}
