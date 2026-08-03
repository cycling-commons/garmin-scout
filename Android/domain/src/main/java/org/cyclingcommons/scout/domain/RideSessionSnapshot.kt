package org.cyclingcommons.scout.domain

/** In-memory ride state that can survive a process kill when persisted. */
data class RideSessionSnapshot(
    val timer: TimerState,
    val queuedTags: List<QueuedTag>,
    val tallies: TagTalliesSnapshot,
    /** Wall-clock elapsed at the last persist (frozen while the app was dead). */
    val elapsedMs: Long,
    val sampleCount: Long,
    val carCount: Int,
    val lastCarSpeedKph: Int,
)
