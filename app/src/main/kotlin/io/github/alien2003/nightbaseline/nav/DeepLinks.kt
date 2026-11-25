package io.github.alien2003.nightbaseline.nav

import io.github.alien2003.nightbaseline.work.notify.NightIntents
import java.net.URI
import java.net.URISyntaxException

/** A deep link of `screens.md` 3 (built by the notifications in `:work`). */
sealed interface DeepLink {
    /** `nightbaseline://night/{date}`: Home if it is the newest evaluated night, otherwise Night detail. */
    data class Night(
        /** Wake date, ISO, validated. */
        val date: String,
    ) : DeepLink

    /** `nightbaseline://fix/{problem}`: the fix flow of a data-access problem. */
    data class Fix(
        /** Which problem. */
        val problem: FixProblem,
    ) : DeepLink
}

/** The `fix/{segment}` targets. */
enum class FixProblem(
    /** URI segment. */
    val segment: String,
) {
    /** `E-PERM`, `B-BG`: the Health Connect permission request. */
    Permission("permission"),

    /** `E-HC-UPDATE`: Home with the update card. */
    HealthConnect("health-connect"),

    /** `B-SOURCE-QUIET`, `E-SOURCE-GONE`: the source picker. */
    SourceSync("source-sync"),
}

/** Parses the launch intent's data URI and the night date extra. Pure (no Android types). */
object DeepLinks {
    /**
     * The link of an intent with [dataUri] (`Intent.dataString`) and [nightDateExtra]
     * ([NightIntents.EXTRA_NIGHT_DATE]); both are untrusted input: the date is validated with
     * [NightIntents.nightDateOf], unknown hosts and segments are ignored. The URI wins over the extra.
     */
    fun parse(
        dataUri: String?,
        nightDateExtra: String?,
    ): DeepLink? = dataUri?.let(::parseUri) ?: NightIntents.nightDateOf(nightDateExtra)?.let(DeepLink::Night)

    @Suppress("ReturnCount") // Each rejected part of untrusted input returns early.
    private fun parseUri(value: String): DeepLink? {
        val uri =
            try {
                URI(value)
            } catch (_: URISyntaxException) {
                return null
            }
        if (uri.scheme != NightIntents.SCHEME) return null
        val segment = uri.path?.trim('/')?.takeIf { it.isNotEmpty() && '/' !in it } ?: return null
        return when (uri.host) {
            "night" -> NightIntents.nightDateOf(segment)?.let(DeepLink::Night)
            "fix" -> FixProblem.entries.firstOrNull { it.segment == segment }?.let(DeepLink::Fix)
            else -> null
        }
    }
}
