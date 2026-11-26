package io.github.alien2003.nightbaseline.display

/** One display mode as the selection sees it (a plain copy of `android.view.Display.Mode`, testable on the JVM). */
data class ModeSpec(
    val id: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
)

/** Picks the display mode the main window asks for while the person is using the app (ADR 0008). */
object DisplayModes {
    /** `WindowManager.LayoutParams.preferredDisplayModeId` value for "no preference". */
    const val NO_PREFERENCE = 0

    /** Refresh rates closer than this are the same rate (panels report 119.99 / 120.00 for one rate). */
    private const val SAME_RATE_HZ = 0.5f

    /**
     * The id of the mode with the highest refresh rate at the current resolution ([currentWidth] x [currentHeight],
     * physical pixels), so the request never changes the resolution. Among modes with the same rate the current
     * mode ([currentId]) wins, then the lowest id. [NO_PREFERENCE] when no mode has the current resolution or the
     * resolution has a single refresh rate: there is nothing to ask for, and leaving the window attribute alone
     * avoids a needless window relayout.
     */
    fun highestRefreshAt(
        modes: List<ModeSpec>,
        currentId: Int,
        currentWidth: Int,
        currentHeight: Int,
    ): Int {
        val sameSize = modes.filter { it.width == currentWidth && it.height == currentHeight }
        val top = sameSize.maxOfOrNull { it.refreshRate } ?: return NO_PREFERENCE
        val fastest = sameSize.filter { top - it.refreshRate < SAME_RATE_HZ }
        return when {
            fastest.size == sameSize.size -> NO_PREFERENCE
            else -> (fastest.firstOrNull { it.id == currentId } ?: fastest.minBy { it.id }).id
        }
    }
}
