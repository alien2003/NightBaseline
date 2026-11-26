package io.github.alien2003.nightbaseline.e2e.safety

import io.github.alien2003.nightbaseline.data.hc.HealthConnectClientGateway
import io.github.alien2003.nightbaseline.data.hc.HealthConnectReader
import io.github.alien2003.nightbaseline.data.hc.HealthDataReader
import io.github.alien2003.nightbaseline.data.hc.ProcessImportanceForegroundState
import io.github.alien2003.nightbaseline.data.local.ZoneSource
import io.github.alien2003.nightbaseline.data.local.settings.AppSettings
import io.github.alien2003.nightbaseline.e2e.E2e
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** How the app-process tests reach Health Connect and the app's settings: read-only, with a read budget. */
object E2eHealth {
    /**
     * The app's own reader (HealthConnectReader over the real gateway, the same background rule as the daily run),
     * with every Health Connect call charged to [budget]. It can only read: the reader and the gateway have no write.
     */
    fun reader(budget: ReadBudget): HealthDataReader =
        HealthConnectReader(
            BudgetedGateway(HealthConnectClientGateway(E2e.app), budget),
            ProcessImportanceForegroundState,
            ZoneSource.SYSTEM,
            ownPackage = E2e.APP_PACKAGE,
        )

    /**
     * The app's current settings, read once from the app's own settings repository (the singleton NightBaselineApp
     * injected; nothing is written). Tests use a copy of them in memory.
     */
    fun settingsSnapshot(): AppSettings =
        runBlocking {
            E2e.application.settings.settings
                .first()
        }
}
