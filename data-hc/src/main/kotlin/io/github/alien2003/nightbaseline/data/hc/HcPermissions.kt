package io.github.alien2003.nightbaseline.data.hc

/**
 * The Health Connect permissions of NightBaseline. Read only: the app declares and requests no WRITE permission.
 * Required: sleep and heart rate (sleeping heart rate and the night itself); everything else is optional and only
 * widens what is evaluated. Gated ones are requested only when the feature is available on the device.
 */
object HcPermissions {
    private const val PREFIX = "android.permission.health."

    /** Sleep sessions. */
    const val READ_SLEEP: String = PREFIX + "READ_SLEEP"

    /** Heart rate series. */
    const val READ_HEART_RATE: String = PREFIX + "READ_HEART_RATE"

    /** Blood oxygen. */
    const val READ_OXYGEN_SATURATION: String = PREFIX + "READ_OXYGEN_SATURATION"

    /** Respiratory rate. */
    const val READ_RESPIRATORY_RATE: String = PREFIX + "READ_RESPIRATORY_RATE"

    /** HRV (RMSSD). */
    const val READ_HEART_RATE_VARIABILITY: String = PREFIX + "READ_HEART_RATE_VARIABILITY"

    /** Skin temperature; needs [HcFeature.SKIN_TEMPERATURE]. */
    const val READ_SKIN_TEMPERATURE: String = PREFIX + "READ_SKIN_TEMPERATURE"

    /** Steps (the "person is up" check of the night-complete rule). */
    const val READ_STEPS: String = PREFIX + "READ_STEPS"

    /** Other apps' data older than 30 days before the first grant; needs [HcFeature.READ_HISTORY]. */
    const val READ_HEALTH_DATA_HISTORY: String = PREFIX + "READ_HEALTH_DATA_HISTORY"

    /** Reads while the app is not in the foreground (the daily run); needs [HcFeature.READ_IN_BACKGROUND]. */
    const val READ_HEALTH_DATA_IN_BACKGROUND: String = PREFIX + "READ_HEALTH_DATA_IN_BACKGROUND"

    /** Without these nothing is evaluated. */
    val REQUIRED: Set<String> = setOf(READ_SLEEP, READ_HEART_RATE)

    /** Optional data types (each gives one more channel or the steps check). */
    val OPTIONAL: Set<String> =
        setOf(READ_OXYGEN_SATURATION, READ_RESPIRATORY_RATE, READ_HEART_RATE_VARIABILITY, READ_SKIN_TEMPERATURE, READ_STEPS)

    /** Permissions that exist only when a feature is available. */
    val FEATURE_GATED: Map<String, HcFeature> =
        mapOf(
            READ_SKIN_TEMPERATURE to HcFeature.SKIN_TEMPERATURE,
            READ_HEALTH_DATA_HISTORY to HcFeature.READ_HISTORY,
            READ_HEALTH_DATA_IN_BACKGROUND to HcFeature.READ_IN_BACKGROUND,
        )

    /** Every permission the app may request (the manifest must declare exactly these). */
    val ALL: Set<String> = REQUIRED + OPTIONAL + setOf(READ_HEALTH_DATA_HISTORY, READ_HEALTH_DATA_IN_BACKGROUND)

    /** The permissions to request on a device whose available features are [available]. */
    fun requestable(available: Set<HcFeature>): Set<String> =
        ALL.filterTo(LinkedHashSet()) { p ->
            FEATURE_GATED[p]?.let { it in available }
                ?: true
        }

    /** Required permissions missing from [granted]. */
    fun missingRequired(granted: Set<String>): Set<String> = REQUIRED - granted
}
