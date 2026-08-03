package org.cyclingcommons.scout.domain

/**
 * FIFO of tags drained one per sample (~1 Hz). Capacity is load-bearing for undo.
 */
class TagQueue(private val max: Int = Timings.QUEUE_MAX) {
    private val items = ArrayDeque<QueuedTag>()

    val size: Int get() = items.size

    fun offer(type: Int, detail: Int): Boolean {
        if (items.size >= max) return false
        items.addLast(QueuedTag(type, detail))
        return true
    }

    fun poll(): QueuedTag? = items.removeFirstOrNull()

    fun clear() = items.clear()

    fun snapshot(): List<QueuedTag> = items.toList()

    fun restore(tags: List<QueuedTag>) {
        items.clear()
        for (tag in tags) {
            if (items.size >= max) break
            items.addLast(tag)
        }
    }
}
