// Debug-only synthetic Health Connect seeder for emulators (tools/emu/README.md). Not part of the product: it is never
// released (only the debug variant exists), has no INTERNET permission and writes SYNTHETIC nights from :core-algo's
// SyntheticNights into Health Connect under its own data origin. Never install it on a real phone.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.junit)
}

android {
    namespace = "io.github.alien2003.nightbaseline.seeder"
    compileSdk {
        version =
            release(libs.versions.compileSdk.get().toInt()) {
                minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
            }
    }

    defaultConfig {
        applicationId = "io.github.alien2003.nightbaseline.seeder"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }
}

// Debug only: there is no release variant to build or publish by accident.
androidComponents {
    beforeVariants { variant ->
        if (variant.buildType != "debug") variant.enable = false
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

dependencies {
    implementation(project(":core-algo"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.health.connect.client)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
