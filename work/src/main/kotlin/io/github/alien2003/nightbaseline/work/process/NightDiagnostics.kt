package io.github.alien2003.nightbaseline.work.process

import android.util.Log
import javax.inject.Inject

/**
 * Value-free trace of the decisions of one night check (docs/development.md, "Night check diagnostics"): times as
 * `HH:mm` local, night dates, reasons. Never a measured value, a duration or a count of samples.
 */
fun interface NightDiagnostics {
    /** Records one decision. */
    fun log(message: String)
}

/** [NightDiagnostics] to logcat: tag [TAG], info level. */
class LogcatNightDiagnostics
    @Inject
    constructor() : NightDiagnostics {
        override fun log(message: String) {
            Log.i(TAG, message)
        }

        /** Constants. */
        companion object {
            /** Logcat tag. */
            const val TAG: String = "NightBaseline"
        }
    }
