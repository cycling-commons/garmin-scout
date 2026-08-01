package org.cyclingcommons.scout.domain

data class Tile(
    /** POI code, UI_RESUPPLY, UI_BACK, or surface/duration detail codes for pickers */
    val code: Int,
    val label: String,
    /** RGB packed 0xRRGGBB */
    val rgb: Int,
)

enum class UiMode {
    GRID,
    DURATION,
    RESUPPLY,
    SURFACE,
}

object Tiles {
    val grid: List<Tile> = listOf(
        Tile(PoiType.DANGER, "BEWARE", 0xD1421F),
        Tile(PoiType.CLOSURE, "CLOSURE", 0x8E44AD),
        Tile(PoiType.SURFACE, "SURFACE", 0x8E5A2B),
        Tile(PoiType.UI_RESUPPLY, "RESUPPLY", 0x1E7FC0),
        Tile(PoiType.SCENERY, "SCENERY", 0x2E8B57),
        Tile(PoiType.OTHER, "OTHER", 0xB58900),
    )

    val duration: List<Tile> = listOf(
        Tile(Duration.TODAY, "TODAY", 0x2E8B57),
        Tile(Duration.DAYS, "DAYS", 0x1E7FC0),
        Tile(Duration.WEEKS, "WEEKS", 0xB58900),
        Tile(Duration.MONTHS, "MONTHS", 0xD1421F),
        Tile(Duration.UNKNOWN, "UNKNOWN", 0x777777),
        Tile(PoiType.UI_BACK, "BACK", 0x444444),
    )

    val resupply: List<Tile> = listOf(
        Tile(PoiType.WATER, "WATER", 0x1E7FC0),
        Tile(PoiType.FOOD, "FOOD", 0xE67E22),
        Tile(PoiType.MECHANICAL, "REPAIR", 0x7F8C8D),
        Tile(PoiType.UI_BACK, "BACK", 0x444444),
    )

    val surface: List<Tile> = listOf(
        Tile(Surface.ASPHALT, "ASPHALT", 0x555555),
        Tile(Surface.CONCRETE, "CONCRETE", 0x8A8A8A),
        Tile(Surface.PAVING, "PAVING", 0xC0392B),
        Tile(Surface.SETT, "SETT", 0x9B7653),
        Tile(Surface.COBBLES, "COBBLES", 0x6E4B3A),
        Tile(Surface.GRAVEL, "GRAVEL", 0xB58900),
        Tile(Surface.DIRT, "DIRT", 0x8E5A2B),
        Tile(Surface.SAND, "SAND", 0xD2B48C),
        Tile(Surface.END, "END", 0x2E8B57),
        Tile(PoiType.UI_BACK, "BACK", 0x444444),
    )

    fun forMode(mode: UiMode): List<Tile> =
        when (mode) {
            UiMode.GRID -> grid
            UiMode.DURATION -> duration
            UiMode.RESUPPLY -> resupply
            UiMode.SURFACE -> surface
        }

    fun titleFor(mode: UiMode): String? =
        when (mode) {
            UiMode.DURATION -> "CLOSED FOR?"
            UiMode.RESUPPLY -> "WHAT KIND?"
            else -> null
        }

    /** Label for an open stretch detail, or null when none / unknown. */
    fun surfaceLabel(detail: Int): String? =
        if (detail in Surface.ASPHALT..Surface.SAND) {
            surface.firstOrNull { it.code == detail }?.label
        } else {
            null
        }
}
