package io.github.alien2003.nightbaseline.app

import io.github.alien2003.nightbaseline.algo.AlgoParams
import io.github.alien2003.nightbaseline.data.hc.HcError
import io.github.alien2003.nightbaseline.data.hc.HcPermissions
import io.github.alien2003.nightbaseline.data.local.settings.AppSettings
import io.github.alien2003.nightbaseline.ui.settings.SourceOption
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Source discovery for onboarding step 3 and the source picker (`screens.md` 7.5): the apps that wrote sleep sessions
 * to Health Connect in the last 30 days, with the measurements each writes, OHealth first when present, otherwise
 * the most sessions first. Needs READ_SLEEP; before it is granted only the installed default source is offered.
 * An explicit user step, so reading Health Connect here is allowed (`layouts.md` 8.2 rule 3).
 */
object SourceDiscovery {
    /** How far back sources are looked for. */
    const val DAYS: Long = 30

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** The sources, or the installed default when sleep cannot be read yet (or Health Connect fails). */
    suspend fun discover(
        data: AppData,
        params: AlgoParams = AlgoParams(),
    ): List<SourceOption> =
        withContext(data.io) {
            val now = data.clock.millis()
            val from = now - DAYS * DAY_MS
            val granted = runCatching { data.reader.grantedPermissions() }.getOrDefault(emptySet())
            val found =
                if (HcPermissions.READ_SLEEP in granted) {
                    try {
                        data.reader.sleepOrigins(from, now).map { origin ->
                            val channels = runCatching { data.reader.channelsOf(origin.packageName, from, now) }.getOrDefault(emptySet())
                            SourceOption(
                                packageName = origin.packageName,
                                label = data.platform.appLabel(origin.packageName) ?: origin.packageName,
                                lastSleep = Instant.ofEpochMilli(origin.lastEndMs).atZone(data.zoneId()).toLocalDate(),
                                metrics = params.metrics.filter { it.channel == null || it.channel in channels }.map { it.id },
                            )
                        }
                    } catch (_: HcError) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            val default = AppSettings.DEFAULT_SOURCE_PACKAGE
            val withDefault =
                if (found.none { it.packageName == default }) {
                    found +
                        listOfNotNull(
                            data.platform
                                .appLabel(default)
                                ?.let { label ->
                                    SourceOption(default, label, lastSleep = null, metrics = emptyList())
                                }.takeIf { found.isEmpty() },
                        )
                } else {
                    found
                }
            withDefault.sortedWith(compareByDescending<SourceOption> { it.packageName == default })
        }
}
