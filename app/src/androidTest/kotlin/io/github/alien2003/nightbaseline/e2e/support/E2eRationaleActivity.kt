package io.github.alien2003.nightbaseline.e2e.support

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * What Health Connect shows for the test package's single permission (WRITE_STEPS): the test package exists on the
 * phone only while tools/device-test/run-device-tests.sh runs.
 */
class E2eRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (PADDING_DP * resources.displayMetrics.density).toInt()
        setContentView(
            TextView(this).apply {
                setPadding(padding, padding, padding, padding)
                text = RATIONALE
            },
        )
    }

    private companion object {
        const val PADDING_DP = 24
        const val RATIONALE =
            "NightBaseline on-device tests. While the test run lasts, this test package writes ONE step record dated " +
                "1 January 2000 (clientRecordId starting with nb-e2e:), reads it back and deletes it right away. " +
                "It reads and changes nothing else. The package is uninstalled at the end of the run."
    }
}
