// Offline runs of :core-algo over the developer dataset (.data/) or synthetic nights. JVM only.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

application {
    mainClass.set("io.github.alien2003.nightbaseline.cli.MainKt")
    applicationName = "algo-cli"
}

dependencies {
    implementation(project(":core-algo"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
