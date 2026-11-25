// Health Connect boundary (read only): availability, permissions, source discovery, paged reads mapped to
// :core-algo types. Patterns adapted from PulseStages :data-hc (paging, error mapping, background-read guard).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.android.junit)
}

android {
    namespace = "io.github.alien2003.nightbaseline.data.hc"
    compileSdk {
        version =
            release(libs.versions.compileSdk.get().toInt()) {
                minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
            }
    }
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }
    // FakeHealthConnectGateway and the synthetic-source loader live in src/testFixtures so that the :work tests
    // reuse them: testImplementation(testFixtures(project(":data-hc"))).
    testFixtures {
        enable = true
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

dependencies {
    // Types of these modules appear in this module's public API (SleepSession, Sample, ChannelId, ZoneSource).
    api(project(":core-algo"))
    api(project(":data-local"))

    implementation(libs.androidx.health.connect.client)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testFixturesApi(project(":core-algo"))
    testFixturesApi(project(":data-local"))
    testFixturesApi(libs.kotlinx.coroutines.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
