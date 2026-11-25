package io.github.alien2003.nightbaseline.catalog

/** The pages of the component catalog, in `screens.md` 4 order. */
enum class CatalogPage(
    /** Page title. */
    val title: String,
) {
    /** Colours, contrast, type, shapes, glyphs. */
    Foundations("Foundations"),

    /** Bar, rail, top bar, sheet, dialog over a busy backdrop. */
    Shell("Shell"),

    /** Tiles, bands, chips in every metric state. */
    Metrics("Metrics"),

    /** Summary cards, learning, state cards, banners. */
    Nights("Nights"),

    /** Period selector, charts, tooltip, stats. */
    Charts("Charts"),

    /** History rows, calendar, strip, settings rows, buttons. */
    Lists("Lists"),
}
