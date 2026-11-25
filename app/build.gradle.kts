import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.HostTestBuilder

// The NightBaseline app: UI (own design layer on Compose, ADR 0003), Hilt wiring and the Health Connect manifest.
// Build setup follows PulseStages :app (version from -P properties, env-var signing, offline Robolectric).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.android.junit)
}

// -PversionName=1.2.3 -PversionCode=1020399 (a release workflow derives both from the git tag).
val appVersionName: String = providers.gradleProperty("versionName").getOrElse("0.1.0-dev")
val appVersionCode: Int = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(1)

// Release signing is configured only when all four variables are present; otherwise release stays unsigned.
val releaseKeystorePath = providers.environmentVariable("NIGHTBASELINE_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers.environmentVariable("NIGHTBASELINE_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("NIGHTBASELINE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("NIGHTBASELINE_KEY_PASSWORD").orNull
val hasReleaseSigning =
    listOf(releaseKeystorePath, releaseKeystorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "io.github.alien2003.nightbaseline"
    compileSdk {
        version =
            release(libs.versions.compileSdk.get().toInt()) {
                minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
            }
    }

    defaultConfig {
        applicationId = "io.github.alien2003.nightbaseline"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
    }

    androidResources {
        localeFilters += listOf("en", "ru")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release").apply {
                storeFile = file(checkNotNull(releaseKeystorePath))
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }

    testOptions {
        unitTests {
            // The screenshot tests run on Robolectric with the app's merged resources.
            isIncludeAndroidResources = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

/**
 * Hands the merged manifest of every variant (Variant API, SingleArtifact.MERGED_MANIFEST) to the unit-test JVM as
 * `-Dnightbaseline.mergedManifest.<variant>=<path>`. Declaring the files as inputs makes the test task depend on
 * manifest merging, so MergedManifestTest always checks the real merged output.
 */
abstract class MergedManifestsArgumentProvider : CommandLineArgumentProvider {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifestFiles: ConfigurableFileCollection

    @get:Internal
    abstract val manifestPathsByVariant: MapProperty<String, String>

    override fun asArguments(): Iterable<String> =
        manifestPathsByVariant.get().map { (variant, path) -> "-Dnightbaseline.mergedManifest.$variant=$path" }
}

val mergedManifests = objects.newInstance(MergedManifestsArgumentProvider::class.java)

// Robolectric runs offline against the android-all jar resolved by Gradle (as in :data-local and PulseStages).
val robolectricAndroidAll = configurations.dependencyScope("robolectricAndroidAll")
val robolectricAndroidAllClasspath =
    configurations.resolvable("robolectricAndroidAllClasspath") {
        extendsFrom(robolectricAndroidAll.get())
        isTransitive = false
    }
val robolectricDependencyDir = layout.buildDirectory.dir("robolectric-deps")
val prepareRobolectricDependencies =
    tasks.register<Sync>("prepareRobolectricDependencies") {
        description = "Copies the android-all jar resolved by Gradle into the directory used by offline Robolectric."
        from(robolectricAndroidAllClasspath)
        into(robolectricDependencyDir)
    }

// The screenshot matrix (component catalog pages and the placeholder Home, synthetic data only) is rendered on the JVM
// by CatalogScreenshotsTest. Every test run renders into build/screenshots; with -PrecordScreenshots the images are
// written to docs/screenshots instead:
//   ./gradlew :app:testDebugUnitTest --tests '*CatalogScreenshotsTest' -PrecordScreenshots
val recordScreenshots = providers.gradleProperty("recordScreenshots").isPresent
val screenshotDir: String =
    if (recordScreenshots) {
        rootProject.layout.projectDirectory
            .dir("docs/screenshots")
            .asFile.absolutePath
    } else {
        layout.buildDirectory
            .dir("screenshots")
            .get()
            .asFile.absolutePath
    }
// ColorTokensTest compares the Kotlin colour tokens with the tables of docs/design/tokens.md.
val tokensDoc: File = rootProject.layout.projectDirectory.file("docs/design/tokens.md").asFile

tasks.withType<Test>().configureEach {
    dependsOn(prepareRobolectricDependencies)
    inputs.files(prepareRobolectricDependencies).withPropertyName("robolectricDependencies").withNormalizer(ClasspathNormalizer::class.java)
    inputs.files(tokensDoc).withPropertyName("tokensDoc").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricDependencyDir.get().asFile.absolutePath)
    // PixelCopy through Robolectric's hardware renderer, so that RenderEffect-based blur can be captured.
    systemProperty("robolectric.pixelCopyRenderMode", "hardware")
    systemProperty("nightbaseline.screenshots.dir", screenshotDir)
    systemProperty("nightbaseline.tokens.file", tokensDoc.absolutePath)
    if (recordScreenshots) outputs.upToDateWhen { false }
    maxHeapSize = "3g"
}

androidComponents {
    onVariants { variant ->
        val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        mergedManifests.manifestFiles.from(manifest)
        mergedManifests.manifestPathsByVariant.put(variant.name, manifest.map { it.asFile.absolutePath })
        variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.configureTestTask { testTask ->
            testTask.jvmArgumentProviders.add(mergedManifests)
        }
    }
}

dependencies {
    // The data modules (:data-local, :data-hc, :work) are wired in a later step.
    implementation(project(":core-algo"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.haze)
    implementation(libs.haze.blur)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    // Screenshots run on Robolectric (JUnit 4 through the Vintage engine) with Compose's test rule.
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.vintage.engine)
    robolectricAndroidAll(libs.robolectric.android.all.instrumented)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
