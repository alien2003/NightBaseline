package io.github.alien2003.nightbaseline.ui.night

import io.github.alien2003.nightbaseline.ui.home.HomeSamples
import io.github.alien2003.nightbaseline.ui.model.BannerKind
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.sample.SampleData

/** The Night detail states of `states.md` 5 that the screenshot tests render; [id] names the files. */
enum class NightSample(
    /** File-name id. */
    val id: String,
) {
    Loading("loading"),
    Learning("l3"),
    LearningShort("l3-short"),
    AllWithin("s-all"),
    AllWithinPartial("s-all-not-provided"),
    SomeOne("s-some"),
    SomeMany("s-some-many"),
    Deviations("s-dev"),
    Short("e-short"),
    Ambiguous("e-ambiguous"),
    NotEvaluated("e-noteval"),
    NoData("e-nodata"),
    Sync("e-sync"),
    Permission("e-perm"),
    HcUpdate("e-hc-update"),
    SourceGone("e-source-gone"),
    Newest("s-dev-newest"),
    Oldest("s-all-oldest"),
    BannerBackground("b-bg"),
    BannerReadFailed("b-read-failed"),
    BannerNotificationsOff("b-notif-off"),
}

/** Builders of [NightDetailState] from the synthetic sample data (through [HomeSamples]). */
object NightDetailSamples {
    /** Night detail in [sample]. */
    @Suppress("CyclomaticComplexMethod") // One line per state; splitting it would hide the table.
    fun state(sample: NightSample): NightDetailState =
        when (sample) {
            NightSample.Loading -> of(HomeSamples.problem(NightState.Loading))
            NightSample.Learning -> of(HomeSamples.learning(LEARNED).night)
            NightSample.LearningShort -> of(HomeSamples.learning(LEARNED, short = true).night).copy(lastNightShort = true)
            NightSample.AllWithin -> of(SampleData.night(NightState.AllWithin))
            NightSample.AllWithinPartial -> of(HomeSamples.partial())
            NightSample.SomeOne -> of(HomeSamples.someMixed())
            NightSample.SomeMany -> of(HomeSamples.someMany())
            NightSample.Deviations -> of(HomeSamples.deviations())
            NightSample.Short -> of(SampleData.night(NightState.Short))
            NightSample.Ambiguous -> of(HomeSamples.problem(NightState.Ambiguous))
            NightSample.NotEvaluated -> of(HomeSamples.notEvaluated())
            NightSample.NoData -> of(HomeSamples.problem(NightState.NoData))
            NightSample.Sync -> of(HomeSamples.problem(NightState.NotSynced))
            NightSample.Permission -> of(HomeSamples.problem(NightState.Permission))
            NightSample.HcUpdate -> of(HomeSamples.problem(NightState.HealthConnectUpdate))
            NightSample.SourceGone -> of(HomeSamples.problem(NightState.SourceGone))
            NightSample.Newest -> of(HomeSamples.deviations()).copy(next = null)
            NightSample.Oldest -> of(SampleData.night(NightState.AllWithin)).copy(previous = null)
            NightSample.BannerBackground -> of(SampleData.night(NightState.AllWithin)).copy(banner = BannerKind.Background)
            NightSample.BannerReadFailed -> of(SampleData.night(NightState.AllWithin)).copy(banner = BannerKind.ReadFailed)
            NightSample.BannerNotificationsOff -> of(HomeSamples.deviations()).copy(banner = BannerKind.NotificationsOff)
        }

    private const val LEARNED = 3

    /** Night detail of [night] with both neighbours present. */
    fun of(night: NightUi): NightDetailState =
        NightDetailState(
            night = night,
            source = SampleData.SOURCE,
            previous = night.wakeDate.minusDays(1),
            next = night.wakeDate.plusDays(1),
        )
}
