package io.github.alien2003.nightbaseline.exporter

import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Minimal UI: shows the permission state, requests the missing READ permissions, runs the export.
 * `am start -n <pkg>/.ExportActivity --ez autoExport true` starts an export right away (tools/hc-export.sh).
 */
class ExportActivity : ComponentActivity() {
    private lateinit var log: TextView
    private var running = false

    private val requestPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val pad = (PADDING_DP * resources.displayMetrics.density).toInt()
        log =
            TextView(this).apply {
                setPadding(pad, pad, pad, pad)
                setTextIsSelectable(true)
            }
        val grant =
            Button(this).apply {
                text = "Grant permissions"
                setOnClickListener { request() }
            }
        val export =
            Button(this).apply {
                text = "Export"
                setOnClickListener { export() }
            }
        setContentView(
            ScrollView(this).apply {
                addView(
                    LinearLayout(this@ExportActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(pad, pad * TOP_PADDING_FACTOR, pad, pad)
                        addView(grant)
                        addView(export)
                        addView(log)
                    },
                )
            },
        )
        refresh()
        if (intent.getBooleanExtra("autoExport", false)) export()
    }

    private fun client(): HealthConnectClient? =
        if (HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_AVAILABLE) HealthConnectClient.getOrCreate(this) else null

    private suspend fun wanted(client: HealthConnectClient): Set<String> {
        val set = RECORD_TYPES.map { it.permission }.toMutableSet()
        if (client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        ) {
            set += HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
        }
        if (client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        ) {
            set += HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
        }
        if (client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE) !=
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        ) {
            set -= HealthPermission.getReadPermission(androidx.health.connect.client.records.SkinTemperatureRecord::class)
        }
        return set
    }

    private fun refresh() {
        val c =
            client() ?: run {
                log.text = "Health Connect is not available"
                return
            }
        lifecycleScope.launch {
            val granted = c.permissionController.getGrantedPermissions()
            val missing = wanted(c) - granted
            log.text =
                if (missing.isEmpty()) {
                    "Permissions: all granted\n"
                } else {
                    "Permissions: missing ${missing.size}\n" +
                        missing.joinToString("\n") +
                        "\n"
                }
        }
    }

    private fun request() {
        val c = client() ?: return
        lifecycleScope.launch { requestPermissions.launch(wanted(c)) }
    }

    // Debug tool: any export failure must be shown in the log instead of crashing the activity.
    @Suppress("TooGenericExceptionCaught")
    private fun export() {
        val c = client() ?: return
        if (running) return
        running = true
        lifecycleScope.launch {
            try {
                val dir = Exporter(this@ExportActivity, c) { line -> runOnUiThread { log.append("$line\n") } }.run()
                log.append("DONE ${dir.name}\n")
            } catch (e: Exception) {
                log.append("FAILED ${e.javaClass.simpleName}: ${e.message}\n")
            } finally {
                running = false
            }
        }
    }

    private companion object {
        /** Base padding of the layout, dp. */
        const val PADDING_DP = 16

        /** The top padding is this many times the base padding (clears the status bar). */
        const val TOP_PADDING_FACTOR = 3
    }
}
