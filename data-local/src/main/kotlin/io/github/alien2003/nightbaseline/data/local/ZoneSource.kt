package io.github.alien2003.nightbaseline.data.local

import java.time.ZoneId

/**
 * The device time zone, asked anew on every use (the user may travel). Injected so tests fix it.
 */
fun interface ZoneSource {
    /** The current zone. */
    fun zone(): ZoneId

    /** Implementations. */
    companion object {
        /** The system default zone. */
        val SYSTEM: ZoneSource = ZoneSource { ZoneId.systemDefault() }
    }
}
