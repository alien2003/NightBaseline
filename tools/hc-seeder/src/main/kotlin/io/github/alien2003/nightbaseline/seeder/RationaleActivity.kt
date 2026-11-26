package io.github.alien2003.nightbaseline.seeder

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity

/** Permission rationale (also the Android 14+ VIEW_PERMISSION_USAGE target that Health Connect requires). */
class RationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (PADDING_DP * resources.displayMetrics.density).toInt()
        setContentView(
            TextView(this).apply {
                setPadding(pad, pad * TOP_PADDING_FACTOR, pad, pad)
                text =
                    "NightBaseline Seeder is a test tool for emulators. It writes SYNTHETIC sleep, heart rate, SpO2, " +
                    "respiratory rate, HRV, skin temperature and step records into Health Connect and deletes only " +
                    "the records it wrote itself. It reads them back only to check its own writes. No network access."
            },
        )
    }

    private companion object {
        const val PADDING_DP = 16
        const val TOP_PADDING_FACTOR = 3
    }
}
